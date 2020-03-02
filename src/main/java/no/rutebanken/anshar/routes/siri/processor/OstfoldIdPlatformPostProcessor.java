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
import no.rutebanken.anshar.subscription.SubscriptionSetup;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.org.siri.siri20.EstimatedCall;
import uk.org.siri.siri20.EstimatedTimetableDeliveryStructure;
import uk.org.siri.siri20.EstimatedVehicleJourney;
import uk.org.siri.siri20.EstimatedVersionFrameStructure;
import uk.org.siri.siri20.RecordedCall;
import uk.org.siri.siri20.Siri;
import uk.org.siri.siri20.StopPointRef;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static no.rutebanken.anshar.routes.siri.transformer.impl.OutboundIdAdapter.createCombinedId;

public class OstfoldIdPlatformPostProcessor extends ValueAdapter implements PostProcessor {

    private final Logger logger = LoggerFactory.getLogger(OstfoldIdPlatformPostProcessor.class);

    private final static Set<String> unmappedStopPlacePlatform = new HashSet<>();
    private static boolean listUpdated;
    private final StopPlaceRegisterMapper stopPlaceRegisterMapper;

    private static List<String> loggingLineNumberFilter;
    static {
        loggingLineNumberFilter = new ArrayList<>();
        for (int i = 455; i < 494; i++) {
            loggingLineNumberFilter.add("" + i);
        }
    }

    public OstfoldIdPlatformPostProcessor(SubscriptionSetup subscriptionSetup) {
        stopPlaceRegisterMapper = new StopPlaceRegisterMapper(subscriptionSetup.getSubscriptionType(),
                subscriptionSetup.getDatasetId(), StopPointRef.class, subscriptionSetup.getIdMappingPrefixes());
    }

    private String getNsrId(String stopPointRef, String platform) {
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

                                // Temporarily logging every ET with monitoring status
                                String publishedLineName = estimatedVehicleJourney.getPublishedLineNames().get(0).getValue();
                                if (loggingLineNumberFilter.contains(publishedLineName)) {
                                    logger.info("Monitored: {}, DatedVehicleJourneyRef: {}, PublishedLineName: {}",
                                            estimatedVehicleJourney.isMonitored(),
                                            estimatedVehicleJourney.getFramedVehicleJourneyRef().getDatedVehicleJourneyRef(),
                                            publishedLineName);
                                }

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
                                            et.getStopPointRef().setValue(createCombinedId(stopPointRefValue, nsrId));
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
                                            rc.getStopPointRef().setValue(createCombinedId(stopPointRefValue, nsrId));
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
