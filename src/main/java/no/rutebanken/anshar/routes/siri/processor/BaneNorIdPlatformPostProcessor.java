package no.rutebanken.anshar.routes.siri.processor;

import no.rutebanken.anshar.routes.siri.transformer.ApplicationContextHolder;
import no.rutebanken.anshar.routes.siri.transformer.SiriValueTransformer;
import no.rutebanken.anshar.routes.siri.transformer.ValueAdapter;
import uk.org.siri.siri20.*;

import java.util.ArrayList;
import java.util.List;

public class BaneNorIdPlatformPostProcessor extends ValueAdapter implements PostProcessor {

    public static String getNsrId(String jbvCode, String platform) {

        BaneNorIdPlatformUpdaterService stopPlaceService = ApplicationContextHolder.getContext().getBean(BaneNorIdPlatformUpdaterService.class);

        return stopPlaceService.get(jbvCode + ":" + platform);
    }

    @Override
    public void process(Siri siri) {
        List<String> notFound = new ArrayList<>();
        List<EstimatedTimetableDeliveryStructure> estimatedTimetableDeliveries = siri.getServiceDelivery().getEstimatedTimetableDeliveries();
        if (estimatedTimetableDeliveries != null) {
            for (EstimatedTimetableDeliveryStructure estimatedTimetableDelivery : estimatedTimetableDeliveries) {
                List<EstimatedVersionFrameStructure> estimatedJourneyVersionFrames = estimatedTimetableDelivery.getEstimatedJourneyVersionFrames();
                if (estimatedJourneyVersionFrames != null) {
                    estimatedJourneyVersionFrames.stream()
                            .forEach(estimatedVersionFrameStructure -> {
                                estimatedVersionFrameStructure.getEstimatedVehicleJourneies().forEach(estimatedVehicleJourney -> {
                                    EstimatedVehicleJourney.EstimatedCalls estimatedCalls = estimatedVehicleJourney.getEstimatedCalls();
                                    if (estimatedCalls != null) {
                                        estimatedCalls.getEstimatedCalls().stream()
                                                .forEach(et -> {
                                                    String stopPointRefValue = et.getStopPointRef().getValue();

                                                    String platform = null;
                                                    if (et.getArrivalPlatformName() != null) {
                                                        platform = et.getArrivalPlatformName().getValue();
                                                    } else if (et.getDeparturePlatformName() != null) {
                                                        platform = et.getDeparturePlatformName().getValue();
                                                    }
                                                    if (platform == null) {
                                                        platform = "1";
                                                    }
                                                    String nsrId = getNsrId(stopPointRefValue, platform);
                                                    if (nsrId != null) {
                                                        StopPointRef stopPointRef = new StopPointRef();
                                                        stopPointRef.setValue(stopPointRefValue + SiriValueTransformer.SEPARATOR + nsrId);
                                                        et.setStopPointRef(stopPointRef);
                                                    }
                                                });
                                    }

                                    EstimatedVehicleJourney.RecordedCalls recordedCalls = estimatedVehicleJourney.getRecordedCalls();
                                    if (recordedCalls != null) {
                                        recordedCalls.getRecordedCalls().stream()
                                                .forEach(rc -> {
                                                    String stopPointRefValue = rc.getStopPointRef().getValue();

                                                    String platform = null;
                                                    if (rc.getArrivalPlatformName() != null) {
                                                        platform = rc.getArrivalPlatformName().getValue();
                                                    } else if (rc.getDeparturePlatformName() != null) {
                                                        platform = rc.getDeparturePlatformName().getValue();
                                                    }
                                                    if (platform == null) {
                                                        platform = "1";
                                                    }
                                                    StopPointRef stopPointRef = new StopPointRef();
                                                    String nsrId = getNsrId(stopPointRefValue, platform);
                                                    stopPointRef.setValue(stopPointRefValue + SiriValueTransformer.SEPARATOR + nsrId);
                                                    if (nsrId != null) {
                                                        rc.setStopPointRef(stopPointRef);
                                                    }
                                                });
                                    }
                                });
                            });
                }
            }
        }
    }

    @Override
    protected String apply(String value) {
        return null;
    }
}
