/*
 * Licensed under the EUPL, Version 1.2 or – as soon they will be approved by
 * the European Commission - subsequent versions of the EUPL (the "Licence");
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 *
 *   https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and
 * limitations under the Licence.
 */

package no.rutebanken.anshar.routes.siri.processor;

import no.rutebanken.anshar.routes.siri.processor.routedata.ServiceDate;
import no.rutebanken.anshar.routes.siri.processor.routedata.StopTime;
import no.rutebanken.anshar.routes.siri.transformer.ValueAdapter;
import no.rutebanken.anshar.subscription.SiriDataType;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.org.siri.siri20.EstimatedCall;
import uk.org.siri.siri20.EstimatedTimetableDeliveryStructure;
import uk.org.siri.siri20.EstimatedVehicleJourney;
import uk.org.siri.siri20.EstimatedVersionFrameStructure;
import uk.org.siri.siri20.FramedVehicleJourneyRefStructure;
import uk.org.siri.siri20.QuayRefStructure;
import uk.org.siri.siri20.RecordedCall;
import uk.org.siri.siri20.Siri;
import uk.org.siri.siri20.StopAssignmentStructure;

import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;

import static no.rutebanken.anshar.routes.siri.processor.BaneNorSiriEtRewriter.getFirstDepartureTime;
import static no.rutebanken.anshar.routes.siri.processor.BaneNorSiriEtRewriter.getServiceDate;
import static no.rutebanken.anshar.routes.siri.processor.routedata.NetexUpdaterService.getServiceDates;
import static no.rutebanken.anshar.routes.siri.processor.routedata.NetexUpdaterService.getServiceJourney;
import static no.rutebanken.anshar.routes.siri.processor.routedata.NetexUpdaterService.getStopTimes;
import static no.rutebanken.anshar.routes.siri.transformer.MappingNames.POPULATE_STOP_ASSIGNMENTS;
import static no.rutebanken.anshar.routes.siri.transformer.impl.OutboundIdAdapter.getMappedId;

/**
 * Rewrites the SIRI ET-feed from BaneNOR to match the planned routes received from NSB
 *
 */
public class BaneNorSiriStopAssignmentPopulater extends ValueAdapter implements PostProcessor {

    private static final Logger logger = LoggerFactory.getLogger(BaneNorSiriStopAssignmentPopulater.class);
    private final String datasetId;

    public BaneNorSiriStopAssignmentPopulater(String datasetId) {
        this.datasetId = datasetId;
    }

    @Override
    protected String apply(String value) {
        return null;
    }

    @Override
    public void process(Siri siri) {
        long startTime = System.currentTimeMillis();

        int estimatedVehicleJourneyCounter = 0;
        int populatedAssigmentsCounter = 0;
        if (siri != null && siri.getServiceDelivery() != null) {
            List<EstimatedTimetableDeliveryStructure> etDeliveries = siri.getServiceDelivery().getEstimatedTimetableDeliveries();
            if (etDeliveries != null) {
                for (EstimatedTimetableDeliveryStructure etDelivery : etDeliveries) {
                    List<EstimatedVersionFrameStructure> estimatedJourneyVersionFrames = etDelivery.getEstimatedJourneyVersionFrames();
                    for (EstimatedVersionFrameStructure estimatedJourneyVersionFrame : estimatedJourneyVersionFrames) {
                        List<EstimatedVehicleJourney> estimatedVehicleJourneies = estimatedJourneyVersionFrame.getEstimatedVehicleJourneies();
                        for (EstimatedVehicleJourney estimatedVehicleJourney : estimatedVehicleJourneies) {
                            estimatedVehicleJourneyCounter++;
                            if (populateStopAssignments(estimatedVehicleJourney)) {
                                populatedAssigmentsCounter++;
                                getMetricsService().registerDataMapping(SiriDataType.ESTIMATED_TIMETABLE, datasetId, POPULATE_STOP_ASSIGNMENTS, 1);

                            }
                        }
                    }
                }
            }
        }
        logger.info("Done adding StopAssignment to {} of {} journeys in {} ms", populatedAssigmentsCounter, estimatedVehicleJourneyCounter, (System.currentTimeMillis()-startTime));
    }

    private boolean populateStopAssignments(EstimatedVehicleJourney estimatedVehicleJourney) {

        String datedVehicleJourneyRefValue;
        FramedVehicleJourneyRefStructure framedVehicleJourneyRef = estimatedVehicleJourney.getFramedVehicleJourneyRef();
        if (framedVehicleJourneyRef != null) {
            datedVehicleJourneyRefValue = framedVehicleJourneyRef.getDatedVehicleJourneyRef();
        } else {
            datedVehicleJourneyRefValue = resolveServiceJourney(estimatedVehicleJourney);
        }

        if (datedVehicleJourneyRefValue == null) {
            return false;
        }

        List<StopTime> stopTimes = getStopTimes(datedVehicleJourneyRefValue);
        String operator = estimatedVehicleJourney.getOperatorRef() != null ? estimatedVehicleJourney.getOperatorRef().getValue() : null;
        if (stopTimes == null) {
            logger.debug("Found no stopplaces for DatedVehicleJourneyRef = {}, Operator = {}", datedVehicleJourneyRefValue, operator);
            return false;
        }
        boolean addedAimedQuay = false;
        EstimatedVehicleJourney.EstimatedCalls estimatedCalls = estimatedVehicleJourney.getEstimatedCalls();

        if (estimatedCalls == null) {
            logger.debug("Found no estimatedCalls in DatedVehicleJourneyRef = {}, Operator = {}", datedVehicleJourneyRefValue, operator);
            return false;
        }

        // We need to track number of ExtraCalls to find correct StopAssignment based on order.
        // Since ExtraCalls are not planned (duh), they will not include StopAssignment
        int extraCalls = 0;
        if (estimatedVehicleJourney.getRecordedCalls() != null) {
            for (RecordedCall recordedCall : estimatedVehicleJourney.getRecordedCalls().getRecordedCalls()) {
                if (recordedCall.isExtraCall() != null && recordedCall.isExtraCall()) {
                    extraCalls++;
                }
            }
            if (extraCalls > 0) {
                logger.info("Found {} ExtraCalls in RecordedCalls", extraCalls);
            }
        }

        for (EstimatedCall estimatedCall : estimatedCalls.getEstimatedCalls()) {
            if (estimatedCall.getStopPointRef() == null || estimatedCall.getOrder() == null) {
                logger.debug("Got a call without stopPointRef ({}) or order {}", estimatedCall.getStopPointRef(), estimatedCall.getOrder());
                continue;
            }
            if (estimatedCall.isExtraCall() != null && estimatedCall.isExtraCall()) {
                extraCalls++;
                continue;
            }
            String expectedQuay = getMappedId(estimatedCall.getStopPointRef().getValue());
            int order = estimatedCall.getOrder().intValue();
            StopAssignmentStructure stopAssignment;
            if (order == 1) { //only one of departure- or arrivalStopAssignments should be populated according to the norwegian SIRI profile
                if (estimatedCall.getDepartureStopAssignment() == null) {
                    estimatedCall.setDepartureStopAssignment(new StopAssignmentStructure());
                }
                stopAssignment = estimatedCall.getDepartureStopAssignment();
            } else {
                if (estimatedCall.getArrivalStopAssignment() == null) {
                    estimatedCall.setArrivalStopAssignment(new StopAssignmentStructure());
                }
                stopAssignment = estimatedCall.getArrivalStopAssignment();
            }
            if (stopAssignment.getAimedQuayRef() == null || StringUtils.isEmpty(stopAssignment.getAimedQuayRef().getValue()) ) {
                int sequence = order - 1 - extraCalls; //Stops in GTFS starts with 0, while it starts with 1 in the EstimatedCall-structure
                if (stopTimes.size() > sequence) {
                    StopTime stopTime = stopTimes.get(sequence);
                    //Sometimes (when part of the route is in Sweden) the order of the call in the EstimatedCall does not match the stoptimes from route data
                    if (stopTime.getStopSequence() == sequence) {
                        String aimedQuay = stopTime.getStopId();
                        stopAssignment.setAimedQuayRef(createQuayRef(aimedQuay));
                        addedAimedQuay = true;
                    } else {
                        logger.warn("Got incorrect sequence in stoptime for DatedVehicleJourney={}: has {} (estimatedCall.order-1), but got {}", datedVehicleJourneyRefValue, sequence, stopTime.getStopSequence());
                    }
                } else {
                    logger.warn("Got a sequence number ({}) that is out of bounds (stopTimes.size()={}) for DatedVehicleJourney={}", sequence, stopTimes.size(), datedVehicleJourneyRefValue);
                }

            }
            if (stopAssignment.getExpectedQuayRef() == null || StringUtils.isEmpty(stopAssignment.getExpectedQuayRef().getValue()) ) {
                stopAssignment.setExpectedQuayRef(createQuayRef(expectedQuay));
            }
        }
        return addedAimedQuay;
    }

    private String resolveServiceJourney(EstimatedVehicleJourney estimatedVehicleJourney) {
        String id = null;

        if (estimatedVehicleJourney.getVehicleRef() != null) {
            String vehicleRef = estimatedVehicleJourney.getVehicleRef().getValue();
            Set<String> serviceJourneyIds = getServiceJourney(vehicleRef);
            if (serviceJourneyIds != null) {
                ServiceDate serviceDate = getServiceDate(estimatedVehicleJourney);
                int departureTimeAsSecondsOfDay = getDepartureTimeAsSecondsOfDay(estimatedVehicleJourney);
                for (String serviceJourneyId : serviceJourneyIds) {
                    List<StopTime> stopTimes = getStopTimes(serviceJourneyId);

                    if (getServiceDates(serviceJourneyId).contains(serviceDate) &&
                            departureTimeAsSecondsOfDay == stopTimes.get(0).getArrivalTime()) {
                        id = serviceJourneyId;
                    }
                }
            }
        }
        return id;
    }

    private int getDepartureTimeAsSecondsOfDay(EstimatedVehicleJourney estimatedVehicleJourney) {
        ZonedDateTime departureTime = getFirstDepartureTime(estimatedVehicleJourney);
        return LocalTime.from(departureTime).toSecondOfDay();
    }

    private QuayRefStructure createQuayRef(String value) {
        QuayRefStructure quayRef = new QuayRefStructure();
        quayRef.setValue(value);
        return quayRef;
    }


}
