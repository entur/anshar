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
import no.rutebanken.anshar.routes.siri.transformer.impl.StopPlaceRegisterMapper;
import no.rutebanken.anshar.subscription.SiriDataType;
import no.rutebanken.anshar.subscription.SubscriptionSetup;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.org.siri.siri21.EstimatedCall;
import uk.org.siri.siri21.EstimatedTimetableDeliveryStructure;
import uk.org.siri.siri21.EstimatedVehicleJourney;
import uk.org.siri.siri21.EstimatedVersionFrameStructure;
import uk.org.siri.siri21.MonitoredCallStructure;
import uk.org.siri.siri21.OnwardCallStructure;
import uk.org.siri.siri21.OnwardCallsStructure;
import uk.org.siri.siri21.RecordedCall;
import uk.org.siri.siri21.Siri;
import uk.org.siri.siri21.StopPointRefStructure;
import uk.org.siri.siri21.VehicleActivityStructure;
import uk.org.siri.siri21.VehicleMonitoringDeliveryStructure;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static no.rutebanken.anshar.routes.siri.transformer.MappingNames.STOP_AND_PLATFORM_TO_NSR;
import static no.rutebanken.anshar.routes.siri.transformer.impl.OutboundIdAdapter.createCombinedId;

public class OstfoldIdPlatformPostProcessor extends ValueAdapter implements PostProcessor {

    private final Logger logger = LoggerFactory.getLogger(OstfoldIdPlatformPostProcessor.class);

    private static final Set<String> unmappedStopPlacePlatform = new HashSet<>();
    private boolean listUpdated;
    private final StopPlaceRegisterMapper stopPlaceRegisterMapper;

    private final String datasetId;

    public OstfoldIdPlatformPostProcessor(SubscriptionSetup subscriptionSetup) {
        datasetId = subscriptionSetup.getDatasetId();
        stopPlaceRegisterMapper = new StopPlaceRegisterMapper(subscriptionSetup.getSubscriptionType(),
                datasetId, StopPointRefStructure.class, subscriptionSetup.getIdMappingPrefixes());

        //Disable counting metrics to avoid counting twice
        stopPlaceRegisterMapper.disableMetrics();
    }

    private String getMappedNsrId(String stopPointRef, String platform) {
        if (stopPointRef.startsWith("NSR:")) {
            return null;
        }
        String originalId = StringUtils.leftPad(stopPointRef, 8, '0') + StringUtils.leftPad(platform, 2, '0');

        String nsrId = stopPlaceRegisterMapper.apply(originalId);
        if (nsrId == null || nsrId.startsWith(stopPointRef)) {
            listUpdated = listUpdated || unmappedStopPlacePlatform.add(originalId);
        }
        return nsrId;
    }

    @Override
    public void process(Siri siri) {
        listUpdated = false;
        if (siri != null && siri.getServiceDelivery() != null) {
            processEtDeliveries(siri.getServiceDelivery().getEstimatedTimetableDeliveries());
            processVmDeliveries(siri.getServiceDelivery().getVehicleMonitoringDeliveries());
        }
        if (listUpdated) {
            logger.warn("Unable to find mapped value for stopPlace/platform: {}", unmappedStopPlacePlatform);
            listUpdated = false;
        }

    }

    private void processVmDeliveries(List<VehicleMonitoringDeliveryStructure> vehicleMonitoringDeliveries) {
        if (vehicleMonitoringDeliveries != null) {
            for (VehicleMonitoringDeliveryStructure vehicleMonitoringDeliveryStructure : vehicleMonitoringDeliveries) {
                List<VehicleActivityStructure> vehicleActivities = vehicleMonitoringDeliveryStructure.getVehicleActivities();
                if (vehicleActivities != null) {
                    for (VehicleActivityStructure vehicleActivityStructure : vehicleActivities) {
                        if (vehicleActivityStructure != null) {

                            final VehicleActivityStructure.MonitoredVehicleJourney monitoredVehicleJourney = vehicleActivityStructure.getMonitoredVehicleJourney();

                            final MonitoredCallStructure monitoredCall = monitoredVehicleJourney.getMonitoredCall();
                            if (monitoredCall != null) {
                                String stopPointRefValue = monitoredCall.getStopPointRef().getValue();

                                String platform = null;
                                if (monitoredCall.getArrivalPlatformName() != null) {
                                    platform = monitoredCall.getArrivalPlatformName().getValue();
                                } else if (monitoredCall.getDeparturePlatformName() != null) {
                                    platform = monitoredCall.getDeparturePlatformName().getValue();
                                }

                                String nsrId = getMappedNsrId(stopPointRefValue, platform);
                                if (nsrId != null) {
                                    monitoredCall.getStopPointRef().setValue(createCombinedId(stopPointRefValue, nsrId));
                                }
                            }

                            if (monitoredVehicleJourney.getOnwardCalls() != null) {
                                final OnwardCallsStructure onwardCalls = monitoredVehicleJourney.getOnwardCalls();
                                final List<OnwardCallStructure> onwardCallsList = onwardCalls.getOnwardCalls();
                                for (OnwardCallStructure call : onwardCallsList) {
                                    String stopPointRefValue = call.getStopPointRef().getValue();

                                    String platform = null;
                                    if (call.getArrivalPlatformName() != null) {
                                        platform = call.getArrivalPlatformName().getValue();
                                    } else if (call.getDeparturePlatformName() != null) {
                                        platform = call.getDeparturePlatformName().getValue();
                                    }

                                    String nsrId = getMappedNsrId(stopPointRefValue, platform);
                                    if (nsrId != null) {
                                        call.getStopPointRef().setValue(createCombinedId(stopPointRefValue, nsrId));
                                        getMetricsService().registerDataMapping(SiriDataType.VEHICLE_MONITORING, datasetId, STOP_AND_PLATFORM_TO_NSR, 1);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

    }

    private void processEtDeliveries(List<EstimatedTimetableDeliveryStructure> estimatedTimetableDeliveries) {
        if (estimatedTimetableDeliveries != null) {
            for (EstimatedTimetableDeliveryStructure estimatedTimetableDelivery : estimatedTimetableDeliveries) {
                List<EstimatedVersionFrameStructure> estimatedJourneyVersionFrames = estimatedTimetableDelivery.getEstimatedJourneyVersionFrames();
                if (estimatedJourneyVersionFrames != null) {
                    for (EstimatedVersionFrameStructure estimatedVersionFrameStructure : estimatedJourneyVersionFrames) {
                        if (estimatedVersionFrameStructure != null) {
                            for (EstimatedVehicleJourney estimatedVehicleJourney : estimatedVersionFrameStructure.getEstimatedVehicleJourneies()) {

                                EstimatedVehicleJourney.EstimatedCalls estimatedCalls = estimatedVehicleJourney.getEstimatedCalls();
                                if (estimatedCalls != null) {
                                    for (EstimatedCall et : estimatedCalls.getEstimatedCalls()) {
                                        String stopPointRefValue = et.getStopPointRef().getValue();

                                        String platform = null;
                                        if (et.getArrivalPlatformName() != null) {
                                            platform = et.getArrivalPlatformName().getValue();
                                        } else if (et.getDeparturePlatformName() != null) {
                                            platform = et.getDeparturePlatformName().getValue();
                                        }

                                        String nsrId = getMappedNsrId(stopPointRefValue, platform);
                                        if (nsrId != null) {
                                            et.getStopPointRef().setValue(createCombinedId(stopPointRefValue, nsrId));
                                            getMetricsService().registerDataMapping(SiriDataType.ESTIMATED_TIMETABLE, datasetId, STOP_AND_PLATFORM_TO_NSR, 1);
                                        }
                                    }
                                }

                                EstimatedVehicleJourney.RecordedCalls recordedCalls = estimatedVehicleJourney.getRecordedCalls();
                                if (recordedCalls != null) {
                                    for (RecordedCall rc : recordedCalls.getRecordedCalls()) {
                                        String stopPointRefValue = rc.getStopPointRef().getValue();

                                        String platform = null;
                                        if (rc.getArrivalPlatformName() != null) {
                                            platform = rc.getArrivalPlatformName().getValue();
                                        } else if (rc.getDeparturePlatformName() != null) {
                                            platform = rc.getDeparturePlatformName().getValue();
                                        }

                                        String nsrId = getMappedNsrId(stopPointRefValue, platform);
                                        if (nsrId != null) {
                                            rc.getStopPointRef().setValue(createCombinedId(stopPointRefValue, nsrId));
                                            getMetricsService().registerDataMapping(SiriDataType.ESTIMATED_TIMETABLE, datasetId, STOP_AND_PLATFORM_TO_NSR, 1);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Override
    protected String apply(String value) {
        return null;
    }
}
