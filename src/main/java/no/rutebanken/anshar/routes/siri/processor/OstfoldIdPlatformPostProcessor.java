package no.rutebanken.anshar.routes.siri.processor;

import no.rutebanken.anshar.routes.siri.transformer.SiriValueTransformer;
import no.rutebanken.anshar.routes.siri.transformer.ValueAdapter;
import no.rutebanken.anshar.routes.siri.transformer.impl.StopPlaceRegisterMapper;
import no.rutebanken.anshar.subscription.SubscriptionSetup;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.org.siri.siri20.*;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class OstfoldIdPlatformPostProcessor extends ValueAdapter implements PostProcessor {

    private final Logger logger = LoggerFactory.getLogger(OstfoldIdPlatformPostProcessor.class);

    private final static Set<String> unmappedStopPlacePlatform = new HashSet<>();
    private static boolean listUpdated;
    private final StopPlaceRegisterMapper stopPlaceRegisterMapper;

    public OstfoldIdPlatformPostProcessor(SubscriptionSetup subscriptionSetup) {
        stopPlaceRegisterMapper = new StopPlaceRegisterMapper(subscriptionSetup.getSubscriptionType(),
                subscriptionSetup.getDatasetId(), StopPointRef.class, subscriptionSetup.getIdMappingPrefixes());
    }

    private String getNsrId(String stopPointRef, String platform) {
        String originalId = StringUtils.leftPad(stopPointRef, 8, '0') + StringUtils.leftPad(platform, 2, '0');

        String nsrId = stopPlaceRegisterMapper.apply(originalId);
        if (nsrId == null || nsrId.startsWith(stopPointRef)) {
            listUpdated = listUpdated | unmappedStopPlacePlatform.add(originalId);
        }
        return nsrId;
    }

    @Override
    public void process(Siri siri) {
        listUpdated = false;
        if (siri != null && siri.getServiceDelivery() != null) {
            processEtDeliveries(siri.getServiceDelivery().getEstimatedTimetableDeliveries());
        }
        if (listUpdated) {
            logger.warn("Unable to find mapped value for stopPlace/platform: {}", unmappedStopPlacePlatform);
            listUpdated = false;
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
    }

    @Override
    protected String apply(String value) {
        return null;
    }
}
