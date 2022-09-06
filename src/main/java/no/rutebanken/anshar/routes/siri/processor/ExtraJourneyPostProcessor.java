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
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import static no.rutebanken.anshar.routes.siri.transformer.MappingNames.EXTRA_JOURNEY_ID_EXISTS;
import static no.rutebanken.anshar.routes.siri.transformer.MappingNames.EXTRA_JOURNEY_INVALID_MODE;
import static no.rutebanken.anshar.routes.siri.transformer.MappingNames.EXTRA_JOURNEY_TOO_FAST;
import static no.rutebanken.anshar.routes.siri.transformer.impl.OutboundIdAdapter.getMappedId;
import static no.rutebanken.anshar.routes.validation.validators.et.SaneSpeedValidator.SANE_SPEED_LIMIT;
import static org.apache.commons.lang3.BooleanUtils.isTrue;

/**
 * Verifies that ExtraJourneys in ET stop at stops having the correct mode, and that
 * the reported arrival-/departure times are reasonable
 */
public class ExtraJourneyPostProcessor extends ValueAdapter implements PostProcessor {
    private Logger logger = LoggerFactory.getLogger(ExtraJourneyPostProcessor.class);
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
        if (siri != null && siri.getServiceDelivery() != null) {

            List<EstimatedTimetableDeliveryStructure> etDeliveries = siri.getServiceDelivery().getEstimatedTimetableDeliveries();
            if (etDeliveries != null) {
                for (EstimatedTimetableDeliveryStructure etDelivery : etDeliveries) {
                    List<EstimatedVersionFrameStructure> estimatedJourneyVersionFrames = etDelivery.getEstimatedJourneyVersionFrames();
                    for (EstimatedVersionFrameStructure estimatedJourneyVersionFrame : estimatedJourneyVersionFrames) {

                        List<EstimatedVehicleJourney> estimatedVehicleJourneies = estimatedJourneyVersionFrame.getEstimatedVehicleJourneies();

                        List<EstimatedVehicleJourney> extraJourneysToRemove = new ArrayList<>();

                        for (EstimatedVehicleJourney estimatedVehicleJourney : estimatedVehicleJourneies) {
                            if (isTrue(estimatedVehicleJourney.isExtraJourney())) {
                                String estimatedVehicleJourneyCode = estimatedVehicleJourney.getEstimatedVehicleJourneyCode();
                                try {

                                    if (serviceJourneyIdExists(estimatedVehicleJourneyCode)) {
                                        throw new AlreadyExistsException(estimatedVehicleJourneyCode);
                                    }



                                    final List<VehicleModesEnumeration> vehicleModes = estimatedVehicleJourney
                                        .getVehicleModes();

                                    final EstimatedVehicleJourney.RecordedCalls recordedCalls = estimatedVehicleJourney
                                        .getRecordedCalls();
                                    if (recordedCalls != null && recordedCalls.getRecordedCalls() != null) {
                                        final List<RecordedCall> calls = recordedCalls.getRecordedCalls();
                                        for (
                                            int i = 0; i < calls.size() - 1; i++
                                        ) {
                                            final RecordedCall thisCall = calls.get(i);
                                            final RecordedCall nextCall = calls.get(i + 1);

                                            if (thisCall.getStopPointRef() != null &&
                                                nextCall.getStopPointRef() != null) {

                                                final String fromStop = getMappedId(thisCall
                                                    .getStopPointRef()
                                                    .getValue());
                                                final String toStop = getMappedId(nextCall
                                                    .getStopPointRef()
                                                    .getValue());

                                                Pair<ZonedDateTime, ZonedDateTime> times = getTimes(thisCall,
                                                    nextCall
                                                );
                                                validateContents( estimatedVehicleJourney,
                                                        vehicleModes,
                                                        fromStop,
                                                        toStop,
                                                        times
                                                );
                                            }
                                        }
                                    }
                                    final EstimatedVehicleJourney.EstimatedCalls estimatedCalls = estimatedVehicleJourney
                                        .getEstimatedCalls();
                                    if (estimatedCalls != null && estimatedCalls.getEstimatedCalls() != null) {
                                        final List<EstimatedCall> calls = estimatedCalls.getEstimatedCalls();
                                        for (
                                            int i = 0; i < calls.size() - 1; i++
                                        ) {
                                            final EstimatedCall thisCall = calls.get(i);
                                            final EstimatedCall nextCall = calls.get(i + 1);

                                            if (thisCall.getStopPointRef() != null &&
                                                nextCall.getStopPointRef() != null) {

                                                final String fromStop = getMappedId(thisCall
                                                    .getStopPointRef()
                                                    .getValue());
                                                final String toStop = getMappedId(nextCall
                                                    .getStopPointRef()
                                                    .getValue());

                                                Pair<ZonedDateTime, ZonedDateTime> times = getTimes(thisCall,
                                                    nextCall
                                                );

                                                validateContents(estimatedVehicleJourney, vehicleModes,
                                                    fromStop,
                                                    toStop,
                                                    times
                                                );
                                            }
                                        }
                                    }
                                } catch (TooFastException e) {
                                    getMetricsService().registerDataMapping(SiriDataType.ESTIMATED_TIMETABLE, datasetId, EXTRA_JOURNEY_TOO_FAST, 1);
                                    logger.info("Removing {}, cause: {}", estimatedVehicleJourneyCode, e.getMessage());
                                    extraJourneysToRemove.add(estimatedVehicleJourney);
                                } catch (InvalidVehicleModeForStopException e) {
                                    getMetricsService().registerDataMapping(SiriDataType.ESTIMATED_TIMETABLE, datasetId, EXTRA_JOURNEY_INVALID_MODE, 1);
                                    logger.info("Removing {}, cause: {}", estimatedVehicleJourneyCode, e.getMessage());
                                    extraJourneysToRemove.add(estimatedVehicleJourney);
                                } catch (AlreadyExistsException e) {
                                    getMetricsService().registerDataMapping(SiriDataType.ESTIMATED_TIMETABLE, datasetId, EXTRA_JOURNEY_ID_EXISTS, 1);
                                    logger.info("Removing {}, cause: {}", estimatedVehicleJourneyCode, e.getMessage());
                                    extraJourneysToRemove.add(estimatedVehicleJourney);
                                }
                            }
                        }

                        if (!extraJourneysToRemove.isEmpty()) {
                            estimatedVehicleJourneies.removeAll(extraJourneysToRemove);
                        }
                    }
                }
            }
        }

    }

    private void validateContents(
            EstimatedVehicleJourney estimatedVehicleJourney, List<VehicleModesEnumeration> vehicleModes, String fromStop, String toStop,
            Pair<ZonedDateTime, ZonedDateTime> times
    ) throws TooFastException, InvalidVehicleModeForStopException {
        if (!StopsUtil.doesVehicleModeMatchStopMode(vehicleModes, fromStop)) {
            logger.warn( "Vehicle mode {} does not match Stop-mode for stop {}",
                vehicleModes,
                fromStop
            );
            throw new InvalidVehicleModeForStopException(estimatedVehicleJourney, vehicleModes, fromStop);
        }
        if (!StopsUtil.doesVehicleModeMatchStopMode(vehicleModes, toStop)) {
            logger.warn("Vehicle mode {} does not match Stop-mode for stop {}",
                vehicleModes,
                toStop
            );
            throw new InvalidVehicleModeForStopException(estimatedVehicleJourney, vehicleModes, toStop);
        }

        final ZonedDateTime fromTime = times.getLeft();
        final ZonedDateTime toTime = times.getRight();

        if (fromTime != null && toTime != null &&
            toTime.isAfter(fromTime)) {
            final int kph = StopsUtil.calculateSpeedKph(fromStop, toStop,
                fromTime,
                toTime
            );

            if (kph > SANE_SPEED_LIMIT) {
                logger.warn(
                    "Calculated speed between {} and {}: {} kph", fromStop, toStop,
                    kph
                );
                throw new TooFastException(estimatedVehicleJourney, fromStop, toStop, fromTime, toTime);
            }
            else {
                logger.debug(
                    "Calculated speed between {} and {}: {} kph", fromStop, toStop,
                    kph
                );
            }
        }
    }

    private Pair<ZonedDateTime, ZonedDateTime> getTimes(
        RecordedCall thisCall, RecordedCall nextCall
    ) {
        ZonedDateTime fromTime;
        ZonedDateTime toTime;

        // Only use comparable times
        if (thisCall.getActualDepartureTime() != null && nextCall.getActualArrivalTime() != null) {
            fromTime = thisCall.getActualDepartureTime();
            toTime = nextCall.getActualArrivalTime();
        } else if (thisCall.getExpectedDepartureTime() != null && nextCall.getExpectedArrivalTime() != null) {
            fromTime = thisCall.getExpectedDepartureTime();
            toTime = nextCall.getExpectedArrivalTime();
        } else {
            fromTime = thisCall.getAimedDepartureTime();
            toTime = nextCall.getAimedArrivalTime();
        }

        return Pair.of(fromTime, toTime);
    }

    private Pair<ZonedDateTime, ZonedDateTime> getTimes(
        EstimatedCall thisCall, EstimatedCall nextCall
    ) {
        ZonedDateTime fromTime;
        ZonedDateTime toTime;

        // Only use comparable times
        if (thisCall.getExpectedDepartureTime() != null && nextCall.getExpectedArrivalTime() != null) {
            fromTime = thisCall.getExpectedDepartureTime();
            toTime = nextCall.getExpectedArrivalTime();
        } else {
            fromTime = thisCall.getAimedDepartureTime();
            toTime = nextCall.getAimedArrivalTime();
        }

        return Pair.of(fromTime, toTime);
    }
}
