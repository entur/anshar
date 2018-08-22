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

import no.rutebanken.anshar.routes.siri.transformer.ValueAdapter;
import org.apache.commons.lang3.StringUtils;
import org.onebusaway.gtfs.model.StopTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.org.siri.siri20.*;

import java.util.List;

import static no.rutebanken.anshar.routes.siri.transformer.impl.OutboundIdAdapter.getMappedId;

/**
 * Rewrites the SIRI ET-feed from BaneNOR to match the planned routes received from NSB
 *
 */
public class BaneNorSiriStopAssignmentPopulater extends ValueAdapter implements PostProcessor {

    private static final Logger logger = LoggerFactory.getLogger(BaneNorSiriStopAssignmentPopulater.class);

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
                            }
                        }
                    }
                }
            }
        }
        logger.info("Done adding StopAssignment to {} of {} journeys in {} ms", populatedAssigmentsCounter, estimatedVehicleJourneyCounter, (System.currentTimeMillis()-startTime));
    }

    private boolean populateStopAssignments(EstimatedVehicleJourney estimatedVehicleJourney) {
        DatedVehicleJourneyRef datedVehicleJourneyRef = estimatedVehicleJourney.getDatedVehicleJourneyRef();
        if (datedVehicleJourneyRef == null) {
            logger.debug("No DatedVehicleJourneyRef on journey");
            return false;
        }
        String datedVehicleJourneyRefValue = datedVehicleJourneyRef.getValue();
        List<StopTime> stopTimes = NSBGtfsUpdaterService.getStopTimes(datedVehicleJourneyRefValue);
        if (stopTimes == null) {
            String operator = estimatedVehicleJourney.getOperatorRef() != null ? estimatedVehicleJourney.getOperatorRef().getValue() : null;
            logger.debug("Found no stopplaces for DatedVehicleJourneyRef = {}, Operator = {}", datedVehicleJourneyRefValue, operator);
            return false;
        }
        boolean addedAimedQuay = false;
        EstimatedVehicleJourney.EstimatedCalls estimatedCalls = estimatedVehicleJourney.getEstimatedCalls();
        for (EstimatedCall estimatedCall : estimatedCalls.getEstimatedCalls()) {
            if (estimatedCall.getStopPointRef() == null || estimatedCall.getOrder() == null) {
                logger.debug("Got a call without stopPointRef ({}) or order {}", estimatedCall.getStopPointRef(), estimatedCall.getOrder());
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
                int sequence = order - 1; //Stops in GTFS starts with 0, while it starts with 1 in the EstimatedCall-structure
                if (stopTimes.size() > sequence) {
                    StopTime stopTime = stopTimes.get(sequence);
                    //Sometimes (when part of the route is in Sweden) the order of the call in the EstimatedCall does not match the stoptimes from route data
                    if (stopTime.getStopSequence() == sequence) {
                        String aimedQuay = stopTime.getStop().getId().getId();
                        stopAssignment.setAimedQuayRef(createQuayRef(aimedQuay));
                        addedAimedQuay = true;
                    } else {
                        logger.warn("Got incorrect sequence in stoptime for DatedVehicleJourney={}: has {} (estimatedCall.order-1), but got {}", datedVehicleJourneyRefValue, sequence, stopTime.getStopSequence());
                    }
                } else {
                    logger.warn("Got a sequence number ({}) that is out of bounds (stopTimes.size()={}) for DatedVehicleJourney={}", sequence, stopTimes.size(), datedVehicleJourneyRef);
                }

            }
            if (stopAssignment.getExpectedQuayRef() == null || StringUtils.isEmpty(stopAssignment.getExpectedQuayRef().getValue()) ) {
                stopAssignment.setExpectedQuayRef(createQuayRef(expectedQuay));
            }
        }
        return addedAimedQuay;
    }

    private QuayRefStructure createQuayRef(String value) {
        QuayRefStructure quayRef = new QuayRefStructure();
        quayRef.setValue(value);
        return quayRef;
    }


}
