package no.rutebanken.anshar.routes.siri.processor;

import no.rutebanken.anshar.routes.siri.transformer.ApplicationContextHolder;
import no.rutebanken.anshar.routes.siri.transformer.SiriValueTransformer;
import no.rutebanken.anshar.routes.siri.transformer.ValueAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.org.siri.siri20.*;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class BaneNorIdPlatformPostProcessor extends ValueAdapter implements PostProcessor {

    private Logger logger = LoggerFactory.getLogger(BaneNorIdPlatformPostProcessor.class);

    private static BaneNorIdPlatformUpdaterService stopPlaceService;

    private static Set<String> unmappedJbvCodePlatform = new HashSet<>();
    private static boolean listUpdated;

    public String getNsrId(String jbvCode, String platform) {

        if (stopPlaceService == null) {
            stopPlaceService = ApplicationContextHolder.getContext().getBean(BaneNorIdPlatformUpdaterService.class);
        }

        String nsrId = stopPlaceService.get(jbvCode + ":" + platform);
        if (nsrId == null) {
            listUpdated = listUpdated | unmappedJbvCodePlatform.add(jbvCode + ":" + platform);
        }
        return nsrId;
    }

    @Override
    public void process(Siri siri) {
        listUpdated = false;
        List<EstimatedTimetableDeliveryStructure> estimatedTimetableDeliveries = siri.getServiceDelivery().getEstimatedTimetableDeliveries();
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

                                        String nsrId = getNsrId(stopPointRefValue, platform);
                                        if (nsrId != null) {
                                            et.getStopPointRef().setValue(stopPointRefValue + SiriValueTransformer.SEPARATOR + nsrId);
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

                                        String nsrId = getNsrId(stopPointRefValue, platform);
                                        if (nsrId != null) {
                                            rc.getStopPointRef().setValue(stopPointRefValue + SiriValueTransformer.SEPARATOR + nsrId);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        if (listUpdated) {
            logger.warn("Unable to find mapped value for jbvCode/platforms: {}", unmappedJbvCodePlatform);
            listUpdated = false;
        }

    }

    @Override
    protected String apply(String value) {
        return null;
    }
}
