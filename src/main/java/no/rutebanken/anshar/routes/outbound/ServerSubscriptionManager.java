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

package no.rutebanken.anshar.routes.outbound;

import com.hazelcast.map.IMap;
import no.rutebanken.anshar.routes.siri.handlers.OutboundIdMappingPolicy;
import no.rutebanken.anshar.routes.siri.helpers.SiriObjectFactory;
import no.rutebanken.anshar.subscription.SiriDataType;
import no.rutebanken.anshar.subscription.helpers.MappingAdapterPresets;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.entur.siri.validator.SiriValidator;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Service;
import uk.org.siri.siri21.AbstractSubscriptionStructure;
import uk.org.siri.siri21.CheckStatusRequestStructure;
import uk.org.siri.siri21.EstimatedTimetableSubscriptionStructure;
import uk.org.siri.siri21.EstimatedVehicleJourney;
import uk.org.siri.siri21.PtSituationElement;
import uk.org.siri.siri21.Siri;
import uk.org.siri.siri21.SituationExchangeSubscriptionStructure;
import uk.org.siri.siri21.SubscriptionRequest;
import uk.org.siri.siri21.VehicleActivityStructure;
import uk.org.siri.siri21.VehicleMonitoringSubscriptionStructure;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import static java.time.temporal.ChronoUnit.MILLIS;
import static no.rutebanken.anshar.routes.kafka.KafkaConfig.CODESPACE_ID_KAFKA_HEADER_NAME;
import static no.rutebanken.anshar.routes.outbound.SiriHelper.resolveSiriVersionStr;

@SuppressWarnings("unchecked")
@Service
@Configuration
public class ServerSubscriptionManager {

    private final Logger logger = LoggerFactory.getLogger(ServerSubscriptionManager.class);

    @Autowired
    IMap<String, OutboundSubscriptionSetup> subscriptions;

    @Autowired
    @Qualifier("getFailTrackerMap")
    private IMap<String, Instant> failTrackerMap;

    @Autowired
    @Qualifier("getHeartbeatTimestampMap")
    private IMap<String, Instant> heartbeatTimestampMap;

    @Autowired
    private SiriObjectFactory siriObjectFactory;

    @Value("${anshar.outbound.heartbeatinterval.minimum}")
    private long minimumHeartbeatInterval = 10000;

    @Value("${anshar.outbound.heartbeatinterval.maximum}")
    private long maximumHeartbeatInterval = 300000;

    @Value("${anshar.outbound.error.consumeraddress}")
    private String errorConsumerAddressMissing = "Error";

    @Value("${anshar.outbound.error.initialtermination}")
    private String initialTerminationTimePassed = "Error";

    @Value("${anshar.outbound.pubsub.vm.topic.enabled}")
    private boolean pushToVmTopicEnabled;
    @Value("${anshar.outbound.pubsub.et.topic.enabled}")
    private boolean pushToEtTopicEnabled;
    @Value("${anshar.outbound.pubsub.sx.topic.enabled}")
    private boolean pushToSxTopicEnabled;

    @Autowired
    private MappingAdapterPresets mappingAdapterPresets;

    ExecutorService outboundSenderExecutorService = Executors.newFixedThreadPool(100);

    @Produce(value = "direct:send.to.pubsub.topic.estimated_timetable")
    protected ProducerTemplate siriEtTopicProducer;

    @Produce(value = "direct:send.to.pubsub.topic.vehicle_monitoring")
    protected ProducerTemplate siriVmTopicProducer;

    @Produce(value = "direct:send.to.pubsub.topic.situation_exchange")
    protected ProducerTemplate siriSxTopicProducer;

    @Autowired
    private CamelRouteManager camelRouteManager;

    @Autowired
    private SiriHelper siriHelper;


    public Collection getSubscriptions() {
        return Collections.unmodifiableCollection(subscriptions.values());
    }

    public JSONArray getSubscriptionsAsJson() {

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

        JSONArray stats = new JSONArray();

        for (String key : subscriptions.keySet()) {

            OutboundSubscriptionSetup subscription = subscriptions.get(key);

            JSONObject obj = new JSONObject();
            obj.put("subscriptionRef", key);
            obj.put("subscriptionType",""+subscription.getSubscriptionType());
            obj.put("version", subscription.getSiriVersion().equals(SiriValidator.Version.VERSION_2_1) ? "2.1" : "2.0");
            obj.put("address", subscription.getAddress());
            obj.put("heartbeatInterval", (subscription.getHeartbeatInterval()/1000) + " s");
            obj.put("datasetId",subscription.getDatasetId()!=null ? subscription.getDatasetId():"");
            obj.put("requestReceived", formatter.format(subscription.getRequestTimestamp()));
            obj.put("initialTerminationTime",formatter.format(subscription.getInitialTerminationTime()));
            obj.put("clientTrackingName",subscription.getClientTrackingName()!=null ? subscription.getClientTrackingName():"");

            stats.add(obj);
        }

        return stats;
    }

    public Siri handleSubscriptionRequest(SubscriptionRequest subscriptionRequest, String datasetId, OutboundIdMappingPolicy outboundIdMappingPolicy, String clientTrackingName) {

        OutboundSubscriptionSetup subscription = createSubscription(subscriptionRequest, datasetId, outboundIdMappingPolicy, clientTrackingName);

        boolean hasError = false;
        String errorText = null;

        if (subscription.getAddress() == null) {
            hasError = true;
            errorText = errorConsumerAddressMissing;
        } else if (subscription.getInitialTerminationTime() == null || subscription.getInitialTerminationTime().isBefore(ZonedDateTime.now())) {
            //Subscription has already expired
            hasError = true;
            errorText = initialTerminationTimePassed;
        }

        if (subscriptions.containsKey(subscription.getSubscriptionId())) {

            final OutboundSubscriptionSetup subscriptionSetup = subscriptions.get(subscription.getSubscriptionId());

            if (subscription.getSubscriptionType() != subscriptionSetup.getSubscriptionType()) {
                hasError = true;
                errorText = "A different subscription with id=" + subscription.getSubscriptionId() + " already exists";
            }
        }

        if (hasError) {
            return siriObjectFactory.createSubscriptionResponse(
                    subscription.getSubscriptionId(),
                    false,
                    errorText,
                    resolveSiriVersionStr(subscription.getSiriVersion())
            );
        } else {
            addSubscription(subscription);

            Siri subscriptionResponse = siriObjectFactory.createSubscriptionResponse(
                    subscription.getSubscriptionId(),
                    true,
                    null,
                    resolveSiriVersionStr(subscription.getSiriVersion()))
                    ;

            //Send initial ServiceDelivery
            logger.debug("Find initial delivery for {}", subscription);
            Siri delivery = siriHelper.findInitialDeliveryData(subscription);

            if (delivery != null) {
                logger.info("Sending initial delivery to {}", subscription.getAddress());
                camelRouteManager.pushSiriData(delivery, subscription, false);
            } else {
                logger.info("No initial delivery found for {}", subscription);
            }

            return subscriptionResponse;
        }
    }



    private OutboundSubscriptionSetup createSubscription(SubscriptionRequest subscriptionRequest, String datasetId, OutboundIdMappingPolicy outboundIdMappingPolicy, String clientTrackingName) {

        return new OutboundSubscriptionSetup(
                ZonedDateTime.now(),
                getSubscriptionType(subscriptionRequest),
                subscriptionRequest.getConsumerAddress() != null ? subscriptionRequest.getConsumerAddress():subscriptionRequest.getAddress(),
                getHeartbeatInterval(subscriptionRequest),
                getChangeBeforeUpdates(subscriptionRequest),
                siriHelper.getFilter(subscriptionRequest),
                mappingAdapterPresets.getOutboundAdapters(outboundIdMappingPolicy),
                findSubscriptionIdentifier(subscriptionRequest),
                findSubscriberIdentifier(subscriptionRequest),
                findInitialTerminationTime(subscriptionRequest),
                datasetId,
                clientTrackingName,
                resolveSiriVersion(subscriptionRequest, outboundIdMappingPolicy)
                );
    }

    private String findSubscriberIdentifier(SubscriptionRequest subscriptionRequest) {

        if (subscriptionRequest.getVehicleMonitoringSubscriptionRequests() != null &&
        !subscriptionRequest.getVehicleMonitoringSubscriptionRequests().isEmpty()) {
            if (subscriptionRequest.getVehicleMonitoringSubscriptionRequests().get(0).getSubscriberRef() != null) {
                return subscriptionRequest.getVehicleMonitoringSubscriptionRequests().get(0).getSubscriberRef().getValue();
            }
        } else if (subscriptionRequest.getEstimatedTimetableSubscriptionRequests() != null &&
                !subscriptionRequest.getEstimatedTimetableSubscriptionRequests().isEmpty()) {
            if (subscriptionRequest.getEstimatedTimetableSubscriptionRequests().get(0).getSubscriberRef() != null) {
                return subscriptionRequest.getEstimatedTimetableSubscriptionRequests().get(0).getSubscriberRef().getValue();
            }
        } else if (subscriptionRequest.getSituationExchangeSubscriptionRequests() != null &&
                !subscriptionRequest.getSituationExchangeSubscriptionRequests().isEmpty()) {
            if (subscriptionRequest.getSituationExchangeSubscriptionRequests().get(0).getSubscriberRef() != null) {
                return subscriptionRequest.getSituationExchangeSubscriptionRequests().get(0).getSubscriberRef().getValue();
            }
        }
        return subscriptionRequest.getRequestorRef().getValue();
    }

    private static SiriValidator.Version resolveSiriVersion(SubscriptionRequest subscriptionRequest, OutboundIdMappingPolicy outboundIdMappingPolicy) {
        // Check
        String version = null;
        if (SiriHelper.containsValues(subscriptionRequest.getSituationExchangeSubscriptionRequests())) {
            version = subscriptionRequest.getSituationExchangeSubscriptionRequests().get(0).getSituationExchangeRequest().getVersion();
        } else if (SiriHelper.containsValues(subscriptionRequest.getVehicleMonitoringSubscriptionRequests())) {
            version = subscriptionRequest.getVehicleMonitoringSubscriptionRequests().get(0).getVehicleMonitoringRequest().getVersion();
        } else if (SiriHelper.containsValues(subscriptionRequest.getEstimatedTimetableSubscriptionRequests())) {
            version = subscriptionRequest.getEstimatedTimetableSubscriptionRequests().get(0).getEstimatedTimetableRequest().getVersion();
        }

        // Subscriber requests SIRI 2.1 version in request
        if ("2.1".equals(version)) {
            return SiriValidator.Version.VERSION_2_1;
        }

        // Checking header-override
        SiriValidator.Version siriVersion = outboundIdMappingPolicy.equals(OutboundIdMappingPolicy.SIRI_2_1) ?
                SiriValidator.Version.VERSION_2_1 : SiriValidator.Version.VERSION_2_0;
        return siriVersion;
    }

    // public for unittest
    public long getHeartbeatInterval(SubscriptionRequest subscriptionRequest) {
        long heartbeatInterval = 0;
        if (subscriptionRequest.getSubscriptionContext() != null &&
                subscriptionRequest.getSubscriptionContext().getHeartbeatInterval() != null) {
            Duration interval = subscriptionRequest.getSubscriptionContext().getHeartbeatInterval();
            heartbeatInterval = interval.toMillis();
        }
        heartbeatInterval = Math.max(heartbeatInterval, minimumHeartbeatInterval);
        heartbeatInterval = Math.min(heartbeatInterval, maximumHeartbeatInterval);

        return heartbeatInterval;
    }

    private SiriDataType getSubscriptionType(SubscriptionRequest subscriptionRequest) {
        if (SiriHelper.containsValues(subscriptionRequest.getSituationExchangeSubscriptionRequests())) {
            return SiriDataType.SITUATION_EXCHANGE;
        } else if (SiriHelper.containsValues(subscriptionRequest.getVehicleMonitoringSubscriptionRequests())) {
            return SiriDataType.VEHICLE_MONITORING;
        } else if (SiriHelper.containsValues(subscriptionRequest.getEstimatedTimetableSubscriptionRequests())) {
            return SiriDataType.ESTIMATED_TIMETABLE;
        }
        return null;
    }

    private long getChangeBeforeUpdates(SubscriptionRequest subscriptionRequest) {
        if (SiriHelper.containsValues(subscriptionRequest.getVehicleMonitoringSubscriptionRequests())) {
            return getMilliSeconds(subscriptionRequest.getVehicleMonitoringSubscriptionRequests().get(0).getChangeBeforeUpdates());
        } else if (SiriHelper.containsValues(subscriptionRequest.getEstimatedTimetableSubscriptionRequests())) {
            return getMilliSeconds(subscriptionRequest.getEstimatedTimetableSubscriptionRequests().get(0).getChangeBeforeUpdates());
        }
        return 0;
    }

    private long getMilliSeconds(Duration changeBeforeUpdates) {
        if (changeBeforeUpdates != null) {
            return changeBeforeUpdates.toMillis();
        }
        return 0;
    }

    private void addSubscription(OutboundSubscriptionSetup subscription) {
        subscriptions.put(subscription.getSubscriptionId(), subscription);
    }

    private OutboundSubscriptionSetup removeSubscription(String subscriptionId) {
        logger.info("Removing subscription {}", subscriptionId);
        failTrackerMap.delete(subscriptionId);
        heartbeatTimestampMap.remove(subscriptionId);
        return subscriptions.remove(subscriptionId);
    }

    private String findSubscriptionIdentifier(SubscriptionRequest subscriptionRequest) {
        if (SiriHelper.containsValues(subscriptionRequest.getSituationExchangeSubscriptionRequests())) {

            SituationExchangeSubscriptionStructure situationExchangeSubscriptionStructure = subscriptionRequest.
                    getSituationExchangeSubscriptionRequests().get(0);

            return getSubscriptionIdentifier(situationExchangeSubscriptionStructure);

        } else if (SiriHelper.containsValues(subscriptionRequest.getVehicleMonitoringSubscriptionRequests())) {

            VehicleMonitoringSubscriptionStructure vehicleMonitoringSubscriptionStructure =
                    subscriptionRequest.getVehicleMonitoringSubscriptionRequests().get(0);

            return getSubscriptionIdentifier(vehicleMonitoringSubscriptionStructure);

        } else if (SiriHelper.containsValues(subscriptionRequest.getEstimatedTimetableSubscriptionRequests())) {

            EstimatedTimetableSubscriptionStructure estimatedTimetableSubscriptionStructure =
                    subscriptionRequest.getEstimatedTimetableSubscriptionRequests().get(0);

            return getSubscriptionIdentifier(estimatedTimetableSubscriptionStructure);
        }
        return null;
    }

    private String getSubscriptionIdentifier(AbstractSubscriptionStructure subscriptionStructure) {
        if (subscriptionStructure != null && subscriptionStructure.getSubscriptionIdentifier() != null) {
            return subscriptionStructure.getSubscriptionIdentifier().getValue();
        }
        return null;
    }

    private ZonedDateTime findInitialTerminationTime(SubscriptionRequest subscriptionRequest) {
        if (SiriHelper.containsValues(subscriptionRequest.getSituationExchangeSubscriptionRequests())) {

            return subscriptionRequest.getSituationExchangeSubscriptionRequests().get(0).getInitialTerminationTime();
        } else if (SiriHelper.containsValues(subscriptionRequest.getVehicleMonitoringSubscriptionRequests())) {

            return subscriptionRequest.getVehicleMonitoringSubscriptionRequests().get(0).getInitialTerminationTime();
        } else if (SiriHelper.containsValues(subscriptionRequest.getEstimatedTimetableSubscriptionRequests())) {

            return subscriptionRequest.getEstimatedTimetableSubscriptionRequests().get(0).getInitialTerminationTime();
        }
        return null;
    }

    /**
     * Terminates all subscriptions for a given subscriberRef.
     * @param subscriberRef
     * @param postResponse
     */
    public void terminateSubscriptions(String subscriberRef, boolean postResponse) {
        for (OutboundSubscriptionSetup subscription : subscriptions.values()) {
            if (subscription.getRequestorRef().equals(subscriberRef)) {
                terminateSubscription(subscription.getSubscriptionId(), postResponse);
            }
        }
    }

    /**
     * Terminates a subscription based on the given subscriptionRef.
     * @param subscriptionRef
     * @param postResponse
     */
    public void terminateSubscription(String subscriptionRef, boolean postResponse) {
        OutboundSubscriptionSetup subscriptionRequest = removeSubscription(subscriptionRef);

        if (subscriptionRequest != null) {
            if (postResponse) {
                Siri terminateSubscriptionResponse = siriObjectFactory.createTerminateSubscriptionResponse(subscriptionRef);
                logger.info("Sending TerminateSubscriptionResponse to {}", subscriptionRequest.getAddress());

                camelRouteManager.pushSiriData(terminateSubscriptionResponse, subscriptionRequest, true);
            } else {
                logger.info("Subscription terminated, but no response was sent");
            }
        } else {
            logger.trace("Got TerminateSubscriptionRequest for non-existing subscription");
        }
    }

    public Siri handleCheckStatusRequest(CheckStatusRequestStructure checkStatusRequest) {
        return siriObjectFactory.createCheckStatusResponse(checkStatusRequest);
    }

    public void pushUpdatesAsync(SiriDataType datatype, List updates, String datasetId) {

        final String breadcrumbId = MDC.get("camel.breadcrumbId");

        switch (datatype) {
            case ESTIMATED_TIMETABLE:
                outboundSenderExecutorService.execute(() -> pushUpdatedEstimatedTimetables(updates, datasetId, breadcrumbId));
                break;
            case SITUATION_EXCHANGE:
                outboundSenderExecutorService.execute(() -> pushUpdatedSituations(updates, datasetId, breadcrumbId));
                break;
            case VEHICLE_MONITORING:
                outboundSenderExecutorService.execute(() -> pushUpdatedVehicleActivities(updates, datasetId, breadcrumbId));
                break;
            default:
                // Ignore
                break;
        }
    }

    private void pushUpdatedVehicleActivities(
        List<VehicleActivityStructure> addedOrUpdated, String datasetId, String breadcrumbId
    ) {
        MDC.put("camel.breadcrumbId", breadcrumbId);

        if (addedOrUpdated == null || addedOrUpdated.isEmpty()) {
            return;
        }
        Siri delivery = siriObjectFactory.createVMServiceDelivery(addedOrUpdated);

        if (pushToVmTopicEnabled) {
            siriVmTopicProducer.asyncRequestBodyAndHeader(siriVmTopicProducer.getDefaultEndpoint(), delivery, CODESPACE_ID_KAFKA_HEADER_NAME, datasetId);
        }

        final List<OutboundSubscriptionSetup> recipients = subscriptions
            .values()
            .stream()
            .filter(subscriptionRequest -> (
                    subscriptionRequest.getSubscriptionType().equals(SiriDataType.VEHICLE_MONITORING)
                        && (
                        subscriptionRequest.getDatasetId() == null || (
                            subscriptionRequest
                                .getDatasetId()
                                .equals(datasetId)
                        )
                    )
                )

            )
            .collect(Collectors.toList());

        if (!recipients.isEmpty()) {
            logger.info("Pushing {} VM updates to {} outbound subscriptions", addedOrUpdated.size(), recipients.size());

            for (OutboundSubscriptionSetup recipient : recipients) {
                camelRouteManager.pushSiriData(delivery, recipient, false);
            }
        }

        MDC.remove("camel.breadcrumbId");
    }


    private void pushUpdatedSituations(
        List<PtSituationElement> addedOrUpdated, String datasetId, String breadcrumbId
    ) {
        MDC.put("camel.breadcrumbId", breadcrumbId);

        if (addedOrUpdated == null || addedOrUpdated.isEmpty()) {
            return;
        }
        Siri delivery = siriObjectFactory.createSXServiceDelivery(addedOrUpdated);

        if (pushToSxTopicEnabled) {
            siriSxTopicProducer.asyncRequestBodyAndHeader(siriSxTopicProducer.getDefaultEndpoint(), delivery, CODESPACE_ID_KAFKA_HEADER_NAME, datasetId);
        }

        final List<OutboundSubscriptionSetup> recipients = subscriptions
            .values()
            .stream()
            .filter(subscriptionRequest -> (
                    subscriptionRequest.getSubscriptionType().equals(SiriDataType.SITUATION_EXCHANGE)
                        && (
                        subscriptionRequest.getDatasetId() == null || (
                            subscriptionRequest
                                .getDatasetId()
                                .equals(datasetId)
                        )
                    )
                )

            )
            .collect(Collectors.toList());

        if (!recipients.isEmpty()) {
            logger.info("Pushing {} SX updates to {} outbound subscriptions", addedOrUpdated.size(), recipients.size());

            for (OutboundSubscriptionSetup recipient : recipients) {
                camelRouteManager.pushSiriData(delivery, recipient, false);
            }
        }

        MDC.remove("camel.breadcrumbId");
    }

    private void pushUpdatedEstimatedTimetables(List<EstimatedVehicleJourney> addedOrUpdated, String datasetId, String breadcrumbId) {

        if (addedOrUpdated == null || addedOrUpdated.isEmpty()) {
            return;
        }

        MDC.put("camel.breadcrumbId", breadcrumbId);

        Siri delivery = siriObjectFactory.createETServiceDelivery(addedOrUpdated);

        if (pushToEtTopicEnabled) {
            siriEtTopicProducer.asyncRequestBodyAndHeader(siriEtTopicProducer.getDefaultEndpoint(), delivery, CODESPACE_ID_KAFKA_HEADER_NAME, datasetId);
        }

        final List<OutboundSubscriptionSetup> recipients = subscriptions
            .values()
            .stream()
            .filter(subscriptionRequest -> (
                    subscriptionRequest.getSubscriptionType().equals(SiriDataType.ESTIMATED_TIMETABLE)
                        && (
                        subscriptionRequest.getDatasetId() == null || (
                            subscriptionRequest
                                .getDatasetId()
                                .equals(datasetId)
                        )
                    )
                )

            )
            .collect(Collectors.toList());

        if (!recipients.isEmpty()) {
            logger.info("Pushing {} ET updates to {} outbound subscriptions", addedOrUpdated.size(), recipients.size());

            for (OutboundSubscriptionSetup recipient : recipients) {
                camelRouteManager.pushSiriData(delivery, recipient, false);
            }
        }
        MDC.remove("camel.breadcrumbId");
    }

    public void pushFailedForSubscription(String subscriptionId) {
        OutboundSubscriptionSetup outboundSubscriptionSetup = subscriptions.get(subscriptionId);
        if (outboundSubscriptionSetup != null) {

            //Grace-period is set to minimum 5 minutes
            long gracePeriod = Math.max(3*outboundSubscriptionSetup.getHeartbeatInterval(), 5*60*1000L);

            Instant firstFail = failTrackerMap.getOrDefault(subscriptionId, Instant.now());

            long terminationTime = firstFail.until(Instant.now(), MILLIS);
            if (terminationTime > gracePeriod) {
                logger.info("Cancelling outbound subscription {} that has failed for {}s.", subscriptionId, terminationTime/1000);
                removeSubscription(subscriptionId);
            } else {
                logger.info("Outbound subscription {} has not responded for {}s, will be cancelled after {}s.", subscriptionId, terminationTime/1000, gracePeriod/1000);
                failTrackerMap.set(subscriptionId, firstFail);
            }
        }
    }

    public void clearFailTracker(String subscriptionId) {
        if (failTrackerMap.containsKey(subscriptionId)) {
            logger.info("Subscription {} is now responding - clearing failtracker", subscriptionId);
            failTrackerMap.delete(subscriptionId);
        }
    }
}
