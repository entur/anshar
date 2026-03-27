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

import no.rutebanken.anshar.routes.siri.processor.routedata.AlreadyExistsException;
import no.rutebanken.anshar.routes.siri.processor.routedata.InvalidVehicleModeForStopException;
import no.rutebanken.anshar.routes.siri.processor.routedata.StopsUtil;
import no.rutebanken.anshar.routes.siri.processor.routedata.TooFastException;
import no.rutebanken.anshar.routes.siri.transformer.ValueAdapter;
import no.rutebanken.anshar.subscription.SiriDataType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.org.siri.siri21.DepartureBoardingActivityEnumeration;
import uk.org.siri.siri21.EstimatedCall;
import uk.org.siri.siri21.EstimatedTimetableDeliveryStructure;
import uk.org.siri.siri21.EstimatedVehicleJourney;
import uk.org.siri.siri21.EstimatedVersionFrameStructure;
import uk.org.siri.siri21.RecordedCall;
import uk.org.siri.siri21.Siri;
import uk.org.siri.siri21.VehicleModesEnumeration;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import static no.rutebanken.anshar.routes.siri.processor.routedata.NetexUpdaterService.serviceJourneyIdExists;
import static no.rutebanken.anshar.routes.siri.processor.routedata.StopsUtil.getDistance;
import static no.rutebanken.anshar.routes.siri.processor.routedata.StopsUtil.getSeconds;
import static no.rutebanken.anshar.routes.siri.transformer.MappingNames.EXTRA_JOURNEY_ID_EXISTS;
import static no.rutebanken.anshar.routes.siri.transformer.MappingNames.EXTRA_JOURNEY_INVALID_MODE;
import static no.rutebanken.anshar.routes.siri.transformer.MappingNames.EXTRA_JOURNEY_TOO_FAST;
import static no.rutebanken.anshar.routes.siri.transformer.impl.OutboundIdAdapter.getMappedId;
import static no.rutebanken.anshar.routes.validation.validators.et.SaneSpeedValidator.SANE_SPEED_LIMIT;

/**
 * Verifies that ExtraJourneys in ET stop at stops having the correct mode, and that
 * the reported arrival-/departure times are reasonable
 */
public class ExtraJourneyPostProcessor extends ValueAdapter implements PostProcessor {
    private final Logger logger = LoggerFactory.getLogger(ExtraJourneyPostProcessor.class);
    private final String datasetId;

    public ExtraJourneyPostProcessor(String datasetId) {
        this.datasetId = datasetId;
    }

    @Override
    protected String apply(String text) {
        return null;
    }

    @Override
    public void process(Siri siri) {
        if (siri == null || siri.getServiceDelivery() == null) {
            return;
        }
        List<EstimatedTimetableDeliveryStructure> etDeliveries = siri.getServiceDelivery().getEstimatedTimetableDeliveries();
        if (etDeliveries == null) {
            return;
        }
        for (EstimatedTimetableDeliveryStructure etDelivery : etDeliveries) {
            for (EstimatedVersionFrameStructure frame : etDelivery.getEstimatedJourneyVersionFrames()) {
                List<EstimatedVehicleJourney> journeys = frame.getEstimatedVehicleJourneies();
                List<EstimatedVehicleJourney> toRemove = new ArrayList<>();

                for (EstimatedVehicleJourney journey : journeys) {
                    String journeyCode = journey.getEstimatedVehicleJourneyCode();
                    /*
                        Only verify that EstimatedVehicleJourneyCode exists - as it should only be
                        used together when also "ExtraJourney=true" is set
                     */
                    if (journeyCode == null) {
                        continue;
                    }
                    try {
                        if (serviceJourneyIdExists(journeyCode)) {
                            throw new AlreadyExistsException(journeyCode);
                        }
                        validateJourney(journey);
                    } catch (TooFastException e) {
                        getMetricsService().registerDataMapping(SiriDataType.ESTIMATED_TIMETABLE, datasetId, EXTRA_JOURNEY_TOO_FAST, 1);
                        logger.info("Removing {}, cause: {}", journeyCode, e.getMessage());
                        toRemove.add(journey);
                    } catch (InvalidVehicleModeForStopException e) {
                        getMetricsService().registerDataMapping(SiriDataType.ESTIMATED_TIMETABLE, datasetId, EXTRA_JOURNEY_INVALID_MODE, 1);
                        logger.info("Removing {}, cause: {}", journeyCode, e.getMessage());
                        toRemove.add(journey);
                    } catch (AlreadyExistsException e) {
                        getMetricsService().registerDataMapping(SiriDataType.ESTIMATED_TIMETABLE, datasetId, EXTRA_JOURNEY_ID_EXISTS, 1);
                        logger.info("Removing {}, cause: {}", journeyCode, e.getMessage());
                        toRemove.add(journey);
                    }
                }
                journeys.removeAll(toRemove);
            }
        }
    }

    private void validateJourney(EstimatedVehicleJourney journey)
            throws TooFastException, InvalidVehicleModeForStopException {
        List<VehicleModesEnumeration> modes = journey.getVehicleModes();

        // lastBoarding* tracks across both loops: a RecordedCall boarding stop seeds the EstimatedCalls loop
        String lastBoardingStopId = null;
        ZonedDateTime lastBoardingDepartureTime = null;

        EstimatedVehicleJourney.RecordedCalls recordedCallsWrapper = journey.getRecordedCalls();
        if (recordedCallsWrapper != null && recordedCallsWrapper.getRecordedCalls() != null) {
            List<RecordedCall> calls = recordedCallsWrapper.getRecordedCalls();
            for (int i = 0; i < calls.size(); i++) {
                RecordedCall call = calls.get(i);
                if (isBoarding(call.getDepartureBoardingActivity()) && call.getStopPointRef() != null) {
                    lastBoardingStopId = getMappedId(call.getStopPointRef().getValue());
                    lastBoardingDepartureTime = getDepartureTime(call);
                }
                if (lastBoardingStopId == null || i == calls.size() - 1) {
                    continue;
                }
                RecordedCall next = calls.get(i + 1);
                if (next.getStopPointRef() == null) {
                    continue;
                }
                validateContents(journey, modes,
                        lastBoardingStopId, getMappedId(next.getStopPointRef().getValue()),
                        lastBoardingDepartureTime, getArrivalTime(next));
            }
        }

        EstimatedVehicleJourney.EstimatedCalls estimatedCallsWrapper = journey.getEstimatedCalls();
        if (estimatedCallsWrapper != null && estimatedCallsWrapper.getEstimatedCalls() != null) {
            List<EstimatedCall> calls = estimatedCallsWrapper.getEstimatedCalls();
            for (int i = 0; i < calls.size() - 1; i++) {
                EstimatedCall call = calls.get(i);
                if (isBoarding(call.getDepartureBoardingActivity()) && call.getStopPointRef() != null) {
                    lastBoardingStopId = getMappedId(call.getStopPointRef().getValue());
                    lastBoardingDepartureTime = getDepartureTime(call);
                }
                if (lastBoardingStopId == null) {
                    continue;
                }
                EstimatedCall next = calls.get(i + 1);
                if (next.getStopPointRef() == null) {
                    continue;
                }
                validateContents(journey, modes,
                        lastBoardingStopId, getMappedId(next.getStopPointRef().getValue()),
                        lastBoardingDepartureTime, getArrivalTime(next));
            }
        }
    }

    private void validateContents(
            EstimatedVehicleJourney journey, List<VehicleModesEnumeration> modes,
            String fromStop, String toStop, ZonedDateTime fromTime, ZonedDateTime toTime
    ) throws TooFastException, InvalidVehicleModeForStopException {
        if (!StopsUtil.doesVehicleModeMatchStopMode(modes, fromStop)) {
            logger.warn("Vehicle mode {} does not match Stop-mode for stop {}", modes, fromStop);
            throw new InvalidVehicleModeForStopException(journey, modes, fromStop);
        }
        if (!StopsUtil.doesVehicleModeMatchStopMode(modes, toStop)) {
            logger.warn("Vehicle mode {} does not match Stop-mode for stop {}", modes, toStop);
            throw new InvalidVehicleModeForStopException(journey, modes, toStop);
        }

        if (fromTime == null || toTime == null) {
            return;
        }
        double distance = getDistance(fromStop, toStop);
        long seconds = getSeconds(fromTime, toTime);
        if (seconds < 0) {
            logger.warn("Negative time difference between {} and {}: {} seconds", fromStop, toStop, seconds);
            throw new TooFastException(journey, fromStop, toStop, fromTime, toTime);
        }
        if (seconds <= 60) {
            seconds = 60;
        }
        final int kph = StopsUtil.calculateSpeedKph(distance, seconds);
        if (kph > SANE_SPEED_LIMIT) {
            logger.warn("Calculated speed between {} and {}: {} kph", fromStop, toStop, kph);
            throw new TooFastException(journey, fromStop, toStop, fromTime, toTime);
        } else {
            logger.debug("Calculated speed between {} and {}: {} kph", fromStop, toStop, kph);
        }
    }

    private boolean isBoarding(DepartureBoardingActivityEnumeration activity) {
        return activity == null || activity == DepartureBoardingActivityEnumeration.BOARDING;
    }

    private ZonedDateTime getDepartureTime(RecordedCall call) {
        if (call.getActualDepartureTime() != null) return call.getActualDepartureTime();
        if (call.getExpectedDepartureTime() != null) return call.getExpectedDepartureTime();
        return call.getAimedDepartureTime();
    }

    private ZonedDateTime getDepartureTime(EstimatedCall call) {
        if (call.getExpectedDepartureTime() != null) return call.getExpectedDepartureTime();
        return call.getAimedDepartureTime();
    }

    private ZonedDateTime getArrivalTime(RecordedCall call) {
        if (call.getActualArrivalTime() != null) return call.getActualArrivalTime();
        if (call.getExpectedArrivalTime() != null) return call.getExpectedArrivalTime();
        return call.getAimedArrivalTime();
    }

    private ZonedDateTime getArrivalTime(EstimatedCall call) {
        if (call.getExpectedArrivalTime() != null) return call.getExpectedArrivalTime();
        return call.getAimedArrivalTime();
    }
}