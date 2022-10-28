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
import uk.org.siri.siri21.EstimatedCall;
import uk.org.siri.siri21.EstimatedTimetableDeliveryStructure;
import uk.org.siri.siri21.EstimatedVehicleJourney;
import uk.org.siri.siri21.EstimatedVersionFrameStructure;
import uk.org.siri.siri21.RecordedCall;
import uk.org.siri.siri21.Siri;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static no.rutebanken.anshar.routes.siri.transformer.MappingNames.ENSURE_INCREASING_INACCURATE_TIMES;
import static no.rutebanken.anshar.routes.siri.transformer.MappingNames.ENSURE_INCREASING_TIMES;
import static no.rutebanken.anshar.routes.siri.transformer.impl.OutboundIdAdapter.getOriginalId;

/**
 * Rewrites the SIRI ET-arrival/departure-times so that they are always increasing
 *
 */
public class EnsureIncreasingTimesProcessor extends ValueAdapter implements PostProcessor {

    private static final Logger logger = LoggerFactory.getLogger(EnsureIncreasingTimesProcessor.class);

    private String datasetId;

    public EnsureIncreasingTimesProcessor(String datasetId) {
        this.datasetId = datasetId;
    }

    @Override
    protected String apply(String value) {
        return null;
    }

    @Override
    public void process(Siri siri) {
        long startTime = System.currentTimeMillis();

        int negativeTimesHitCount = 0;
        int predictionInaccurateFixedHitCount = 0;
        int journeyCount = 0;

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
                            int predictionInaccurateTimeOverridden = 0;

                            ZonedDateTime latestTimestamp = null;
                            if (estimatedVehicleJourney.getRecordedCalls() != null && estimatedVehicleJourney.getRecordedCalls().getRecordedCalls() != null) {
                                List<RecordedCall> recordedCalls = estimatedVehicleJourney.getRecordedCalls().getRecordedCalls();
                                for (RecordedCall recordedCall : recordedCalls) {

                                    if (recordedCall.getActualArrivalTime() != null) {
                                        if (latestTimestamp != null && recordedCall.getActualArrivalTime().isBefore(latestTimestamp)) {
                                            recordedCall.setActualArrivalTime(latestTimestamp);
                                            runtimeCount++;
                                        } else {
                                            latestTimestamp = recordedCall.getActualArrivalTime();
                                        }
                                    } else if (recordedCall.getExpectedArrivalTime() != null) {
                                        if (latestTimestamp != null && recordedCall.getExpectedArrivalTime().isBefore(latestTimestamp)) {
                                            recordedCall.setExpectedArrivalTime(latestTimestamp);
                                            runtimeCount++;
                                        } else {
                                            latestTimestamp = recordedCall.getExpectedArrivalTime();
                                        }
                                    } else {
                                        // No realtime data is set for arrival - override with "fake" timestamp for validity
                                        if (recordedCall.getAimedArrivalTime() != null) {
                                            latestTimestamp = getLatest(
                                                    latestTimestamp,
                                                    recordedCall.getAimedArrivalTime()
                                            );
                                            recordedCall.setPredictionInaccurate(true);
                                            recordedCall.setExpectedArrivalTime(latestTimestamp);
                                            predictionInaccurateTimeOverridden++;
                                        }
                                    }
                                    if (recordedCall.getActualDepartureTime() != null) {
                                        if (latestTimestamp != null && recordedCall.getActualDepartureTime().isBefore(latestTimestamp)) {
                                            recordedCall.setActualDepartureTime(latestTimestamp);
                                            dwelltimeCount++;
                                        } else {
                                            latestTimestamp = recordedCall.getActualDepartureTime();
                                        }
                                    } else if (recordedCall.getExpectedDepartureTime() != null) {
                                        if (latestTimestamp != null && recordedCall.getExpectedDepartureTime().isBefore(latestTimestamp)) {
                                            recordedCall.setExpectedDepartureTime(latestTimestamp);
                                            dwelltimeCount++;
                                        } else {
                                            latestTimestamp = recordedCall.getExpectedDepartureTime();
                                        }
                                    } else {
                                        // No realtime data is set for departure - override with "fake" timestamp for validity
                                        if (recordedCall.getAimedDepartureTime() != null) {
                                            latestTimestamp = getLatest(
                                                    latestTimestamp,
                                                    recordedCall.getAimedDepartureTime()
                                            );
                                            recordedCall.setPredictionInaccurate(true);
                                            recordedCall.setExpectedDepartureTime(latestTimestamp);
                                            predictionInaccurateTimeOverridden++;
                                        }
                                    }
                                }
                            }
                            if (estimatedVehicleJourney.getEstimatedCalls() != null && estimatedVehicleJourney.getEstimatedCalls().getEstimatedCalls() != null) {
                                List<EstimatedCall> estimatedCalls = estimatedVehicleJourney.getEstimatedCalls().getEstimatedCalls();
                                for (EstimatedCall estimatedCall : estimatedCalls) {

                                    if (estimatedCall.getExpectedArrivalTime() != null) {
                                        if (latestTimestamp != null && estimatedCall.getExpectedArrivalTime().isBefore(latestTimestamp)) {
                                            estimatedCall.setExpectedArrivalTime(latestTimestamp);
                                            runtimeCount++;
                                        } else {
                                            latestTimestamp = estimatedCall.getExpectedArrivalTime();
                                        }
                                    } else {
                                        // No realtime data is set for arrival - override with "fake" timestamp for validity
                                        if (estimatedCall.getAimedArrivalTime() != null) {
                                            latestTimestamp = getLatest(
                                                    latestTimestamp,
                                                    estimatedCall.getAimedArrivalTime()
                                            );
                                            estimatedCall.setPredictionInaccurate(true);
                                            estimatedCall.setExpectedArrivalTime(latestTimestamp);
                                            predictionInaccurateTimeOverridden++;
                                        }
                                    }
                                    if (estimatedCall.getExpectedDepartureTime() != null) {
                                        if (latestTimestamp != null && estimatedCall.getExpectedDepartureTime().isBefore(latestTimestamp)) {
                                            estimatedCall.setExpectedDepartureTime(latestTimestamp);
                                            dwelltimeCount++;
                                        } else {
                                            latestTimestamp = estimatedCall.getExpectedDepartureTime();
                                        }
                                    } else {
                                        // No realtime data is set for departure - override with "fake" timestamp for validity
                                        if (estimatedCall.getAimedDepartureTime() != null) {
                                            latestTimestamp = getLatest(
                                                    latestTimestamp,
                                                    estimatedCall.getAimedDepartureTime()
                                            );
                                            estimatedCall.setPredictionInaccurate(true);
                                            estimatedCall.setExpectedDepartureTime(latestTimestamp);
                                            predictionInaccurateTimeOverridden++;
                                        }
                                    }
                                }
                            }

                            if ((runtimeCount + dwelltimeCount) > 0) {
                                String lineRef = estimatedVehicleJourney.getLineRef() != null ? estimatedVehicleJourney.getLineRef().getValue():"";
                                String vehicleRef = estimatedVehicleJourney.getVehicleRef() != null ? estimatedVehicleJourney.getVehicleRef().getValue():"";

                                logger.warn("Fixed {} dwelltimes, {} runtimes for line {}, vehicle {}.", dwelltimeCount, runtimeCount, getOriginalId(lineRef), vehicleRef);

                                negativeTimesHitCount += (runtimeCount + dwelltimeCount);
                            }
                            if (predictionInaccurateTimeOverridden > 0) {
                                String lineRef = estimatedVehicleJourney.getLineRef() != null ? estimatedVehicleJourney.getLineRef().getValue():"";
                                String vehicleRef = estimatedVehicleJourney.getVehicleRef() != null ? estimatedVehicleJourney.getVehicleRef().getValue():"";

                                logger.warn("Fixed {} inaccurate-times for line {}, vehicle {}.", predictionInaccurateTimeOverridden, getOriginalId(lineRef), vehicleRef);
                                predictionInaccurateFixedHitCount += predictionInaccurateTimeOverridden;
                            }
                        }
                    }
                }
            }
        }
        if (predictionInaccurateFixedHitCount + negativeTimesHitCount > 0) {
            logger.warn("Fixed {} dwelltimes/runtimes, {} inaccurate times, for {} journeys in {} ms.", negativeTimesHitCount, predictionInaccurateFixedHitCount, journeyCount, (System.currentTimeMillis() - startTime));
            if (negativeTimesHitCount > 0) {
                getMetricsService().registerDataMapping(SiriDataType.ESTIMATED_TIMETABLE, datasetId, ENSURE_INCREASING_TIMES, negativeTimesHitCount);
            }
            if (predictionInaccurateFixedHitCount > 0) {
                getMetricsService().registerDataMapping(SiriDataType.ESTIMATED_TIMETABLE, datasetId, ENSURE_INCREASING_INACCURATE_TIMES, negativeTimesHitCount);
            }
        }
    }

    private ZonedDateTime getLatest(ZonedDateTime... timestamps) {
        List<ZonedDateTime> nonNullValues = new ArrayList<>();
        for (ZonedDateTime timestamp : timestamps) {
            if (timestamp != null) {
                nonNullValues.add(timestamp);
            }
        }

        if (nonNullValues.isEmpty()) {
            return null;
        }

        Collections.sort(nonNullValues);

        return nonNullValues.get(nonNullValues.size()-1);
    }
}
