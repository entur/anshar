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

import no.rutebanken.anshar.routes.health.HealthManager;
import no.rutebanken.anshar.routes.mapping.BaneNorIdPlatformUpdaterService;
import no.rutebanken.anshar.routes.siri.transformer.ApplicationContextHolder;
import no.rutebanken.anshar.routes.siri.transformer.ValueAdapter;
import no.rutebanken.anshar.routes.siri.transformer.impl.OutboundIdAdapter;
import no.rutebanken.anshar.subscription.SiriDataType;
import uk.org.siri.siri21.EstimatedCall;
import uk.org.siri.siri21.EstimatedTimetableDeliveryStructure;
import uk.org.siri.siri21.EstimatedVehicleJourney;
import uk.org.siri.siri21.EstimatedVersionFrameStructure;
import uk.org.siri.siri21.NaturalLanguageStringStructure;
import uk.org.siri.siri21.RecordedCall;
import uk.org.siri.siri21.Siri;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static no.rutebanken.anshar.routes.siri.transformer.MappingNames.STOP_AND_PLATFORM_TO_NSR;

public class BaneNorIdPlatformPostProcessor extends ValueAdapter implements PostProcessor {

    private static BaneNorIdPlatformUpdaterService stopPlaceService;
    private static HealthManager healthManager;
    private final SiriDataType type;

    private static Set<String> unmappedAlreadyAdded;
    private final String datasetId;

    public BaneNorIdPlatformPostProcessor(SiriDataType type, String datasetId) {
        this.datasetId = datasetId;
        this.type = type;
        unmappedAlreadyAdded = new HashSet<>();
    }


    private String getNsrId(String stopPointRefValue, NaturalLanguageStringStructure arrivalPlatformName, NaturalLanguageStringStructure departurePlatformName) {
        String platform = null;
        if (arrivalPlatformName != null) {
            platform = arrivalPlatformName.getValue();
        } else if (departurePlatformName != null) {
            platform = departurePlatformName.getValue();
        }

        if (stopPlaceService == null) {
            stopPlaceService = ApplicationContextHolder.getContext().getBean(BaneNorIdPlatformUpdaterService.class);
        }
        if (healthManager == null) {
            healthManager = ApplicationContextHolder.getContext().getBean(HealthManager.class);
        }


        String id = stopPointRefValue + ":" + platform;
        String nsrId = stopPlaceService.get(id);
        if (nsrId == null) {
            if (unmappedAlreadyAdded.add(id)) {
                healthManager.addUnmappedId(type, datasetId, id);
            }
            return null;
        } else if (unmappedAlreadyAdded.contains(id)) {
            healthManager.removeUnmappedId(type, datasetId, id);
            unmappedAlreadyAdded.remove(id);
        }
        return OutboundIdAdapter.createCombinedId(stopPointRefValue, nsrId);
    }

    @Override
    public void process(Siri siri) {
        if (siri != null && siri.getServiceDelivery() != null) {
            processEtDeliveries(siri.getServiceDelivery().getEstimatedTimetableDeliveries());
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

                                        String updatedStopPointRef = getNsrId(stopPointRefValue, et.getArrivalPlatformName(), et.getDeparturePlatformName());
                                        if (updatedStopPointRef != null) {
                                            et.getStopPointRef().setValue(updatedStopPointRef);
                                            getMetricsService().registerDataMapping(SiriDataType.ESTIMATED_TIMETABLE, datasetId, STOP_AND_PLATFORM_TO_NSR, 1);
                                        }
                                    }
                                }

                                EstimatedVehicleJourney.RecordedCalls recordedCalls = estimatedVehicleJourney.getRecordedCalls();
                                if (recordedCalls != null) {
                                    for (RecordedCall rc : recordedCalls.getRecordedCalls()) {
                                        String stopPointRefValue = rc.getStopPointRef().getValue();

                                        String updatedStopPointRef = getNsrId(stopPointRefValue, rc.getArrivalPlatformName(), rc.getDeparturePlatformName());
                                        if (updatedStopPointRef != null) {
                                            rc.getStopPointRef().setValue(updatedStopPointRef);
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
