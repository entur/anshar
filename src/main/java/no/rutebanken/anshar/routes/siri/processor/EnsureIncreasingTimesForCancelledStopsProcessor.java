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
import no.rutebanken.anshar.subscription.SiriDataType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.org.siri.siri20.EstimatedCall;
import uk.org.siri.siri20.EstimatedTimetableDeliveryStructure;
import uk.org.siri.siri20.EstimatedVehicleJourney;
import uk.org.siri.siri20.EstimatedVersionFrameStructure;
import uk.org.siri.siri20.RecordedCall;
import uk.org.siri.siri20.Siri;

import java.time.ZonedDateTime;
import java.util.List;

import static no.rutebanken.anshar.routes.siri.transformer.MappingNames.ENSURE_INCREASING_TIMES;
import static no.rutebanken.anshar.routes.siri.transformer.impl.OutboundIdAdapter.getOriginalId;

/**
 * Rewrites the SIRI ET-arrival/departure-times on cancelled stops so that they are valid/increasing
 * "Temporary solution" awaiting fix for https://github.com/opentripplanner/OpenTripPlanner/issues/3714
 */
public class EnsureIncreasingTimesForCancelledStopsProcessor extends ValueAdapter implements PostProcessor {

    private static final Logger logger = LoggerFactory.getLogger(
        EnsureIncreasingTimesForCancelledStopsProcessor.class);

    private String datasetId;

    public EnsureIncreasingTimesForCancelledStopsProcessor(String datasetId) {
        this.datasetId = datasetId;
    }

    @Override
    protected String apply(String value) {
        return null;
    }

    @Override
    public void process(Siri siri) {
        long startTime = System.currentTimeMillis();

        int hitCount = 0;
        int journeyCount = 0;
        int cancelledStopCount = 0;

        if (siri != null && siri.getServiceDelivery() != null) {

            List<EstimatedTimetableDeliveryStructure> etDeliveries = siri.getServiceDelivery().getEstimatedTimetableDeliveries();
            if (etDeliveries != null) {
                for (EstimatedTimetableDeliveryStructure etDelivery : etDeliveries) {
                    List<EstimatedVersionFrameStructure> estimatedJourneyVersionFrames = etDelivery.getEstimatedJourneyVersionFrames();
                    for (EstimatedVersionFrameStructure estimatedJourneyVersionFrame : estimatedJourneyVersionFrames) {

                        List<EstimatedVehicleJourney> estimatedVehicleJourneies = estimatedJourneyVersionFrame.getEstimatedVehicleJourneies();
                        journeyCount += estimatedVehicleJourneies.size();
                        for (EstimatedVehicleJourney estimatedVehicleJourney : estimatedVehicleJourneies) {
                            int dwelltimeCount = 0;
                            int runtimeCount = 0;
                            ZonedDateTime latestTimestamp = null;
                            if (estimatedVehicleJourney.getRecordedCalls() != null && estimatedVehicleJourney.getRecordedCalls().getRecordedCalls() != null) {
                                List<RecordedCall> recordedCalls = estimatedVehicleJourney.getRecordedCalls().getRecordedCalls();
                                for (RecordedCall recordedCall : recordedCalls) {
                                    boolean isCancelledStop = false;
                                    if (Boolean.TRUE.equals(recordedCall.isCancellation())) {
                                        // Need to loop through all calls to keep track of "latestTimestamp",
                                        // but times are only adjusted if the stop is cancelled
                                        cancelledStopCount++;
                                        isCancelledStop = true;
                                    }
                                    if (recordedCall.getActualArrivalTime() != null) {
                                        if (isCancelledStop && latestTimestamp != null && recordedCall.getActualArrivalTime().isBefore(latestTimestamp)) {
                                            recordedCall.setActualArrivalTime(latestTimestamp);
                                            runtimeCount++;
                                        } else {
                                            latestTimestamp = recordedCall.getActualArrivalTime();
                                        }
                                    } else if (recordedCall.getExpectedArrivalTime() != null) {
                                        if (isCancelledStop && latestTimestamp != null && recordedCall.getExpectedArrivalTime().isBefore(latestTimestamp)) {
                                            recordedCall.setExpectedArrivalTime(latestTimestamp);
                                            runtimeCount++;
                                        } else {
                                            latestTimestamp = recordedCall.getExpectedArrivalTime();
                                        }
                                    }
                                    if (recordedCall.getActualDepartureTime() != null) {
                                        if (isCancelledStop && latestTimestamp != null && recordedCall.getActualDepartureTime().isBefore(latestTimestamp)) {
                                            recordedCall.setActualDepartureTime(latestTimestamp);
                                            dwelltimeCount++;
                                        } else {
                                            latestTimestamp = recordedCall.getActualDepartureTime();
                                        }
                                    } else if (recordedCall.getExpectedDepartureTime() != null) {
                                        if (isCancelledStop && latestTimestamp != null && recordedCall.getExpectedDepartureTime().isBefore(latestTimestamp)) {
                                            recordedCall.setExpectedDepartureTime(latestTimestamp);
                                            dwelltimeCount++;
                                        } else {
                                            latestTimestamp = recordedCall.getExpectedDepartureTime();
                                        }
                                    }

                                }
                            }
                            if (estimatedVehicleJourney.getEstimatedCalls() != null && estimatedVehicleJourney.getEstimatedCalls().getEstimatedCalls() != null) {
                                List<EstimatedCall> estimatedCalls = estimatedVehicleJourney.getEstimatedCalls().getEstimatedCalls();
                                for (EstimatedCall estimatedCall : estimatedCalls) {
                                    boolean isCancelledStop = false;
                                    if (Boolean.TRUE.equals(estimatedCall.isCancellation())) {
                                        // Need to loop through all calls to keep track of "latestTimestamp",
                                        // but times are only adjusted if the stop is cancelled
                                        cancelledStopCount++;
                                        isCancelledStop = true;
                                    }

                                    if (estimatedCall.getExpectedArrivalTime() != null) {
                                        if (isCancelledStop && latestTimestamp != null && estimatedCall.getExpectedArrivalTime().isBefore(latestTimestamp)) {
                                            estimatedCall.setExpectedArrivalTime(latestTimestamp);
                                            runtimeCount++;
                                        } else {
                                            latestTimestamp = estimatedCall.getExpectedArrivalTime();
                                        }
                                    }
                                    if (estimatedCall.getExpectedDepartureTime() != null) {
                                        if (isCancelledStop && latestTimestamp != null && estimatedCall.getExpectedDepartureTime().isBefore(latestTimestamp)) {
                                            estimatedCall.setExpectedDepartureTime(latestTimestamp);
                                            dwelltimeCount++;
                                        } else {
                                            latestTimestamp = estimatedCall.getExpectedDepartureTime();
                                        }
                                    }

                                }
                            }

                            if ((runtimeCount + dwelltimeCount) > 0) {
                                String lineRef = estimatedVehicleJourney.getLineRef() != null ? estimatedVehicleJourney.getLineRef().getValue():"";
                                String vehicleRef = estimatedVehicleJourney.getVehicleRef() != null ? estimatedVehicleJourney.getVehicleRef().getValue():"";

                                logger.warn("Fixed {} dwelltimes, {} runtimes for line {}, vehicle {}.", dwelltimeCount, runtimeCount, getOriginalId(lineRef), vehicleRef);
                            }
                            hitCount += (runtimeCount + dwelltimeCount);
                        }
                    }
                }
            }
        }
        if (hitCount > 0) {
            logger.warn("Fixed {} dwelltimes/runtimes for {} journeys with {} cancelled stops in {} ms.", hitCount, journeyCount, cancelledStopCount, (System.currentTimeMillis() - startTime));
            getMetricsService().registerDataMapping(SiriDataType.ESTIMATED_TIMETABLE, datasetId, ENSURE_INCREASING_TIMES, hitCount);
        }
    }
}
