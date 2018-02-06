package no.rutebanken.anshar.routes.siri.processor;

import no.rutebanken.anshar.routes.health.HealthManager;
import no.rutebanken.anshar.routes.siri.transformer.ApplicationContextHolder;
import no.rutebanken.anshar.routes.siri.transformer.SiriValueTransformer;
import no.rutebanken.anshar.routes.siri.transformer.ValueAdapter;
import no.rutebanken.anshar.subscription.SubscriptionSetup;
import uk.org.siri.siri20.*;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class BaneNorIdPlatformPostProcessor extends ValueAdapter implements PostProcessor {

    private static BaneNorIdPlatformUpdaterService stopPlaceService;
    private static HealthManager healthManager;
    private final SubscriptionSetup.SubscriptionType type;

    private static Set<String> unmappedAlreadyAdded;
    private final String datasetId;

    public BaneNorIdPlatformPostProcessor(SubscriptionSetup.SubscriptionType type, String datasetId) {
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
        return stopPointRefValue + SiriValueTransformer.SEPARATOR + nsrId;
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
