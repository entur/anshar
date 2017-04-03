package no.rutebanken.anshar.routes.outbound;

import com.hazelcast.core.IMap;
import no.rutebanken.anshar.routes.siri.SiriObjectFactory;
import no.rutebanken.anshar.routes.siri.handlers.OutboundIdMappingPolicy;
import no.rutebanken.anshar.subscription.MappingAdapterPresets;
import no.rutebanken.anshar.subscription.SubscriptionSetup;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Service;
import uk.org.siri.siri20.*;

import javax.annotation.PostConstruct;
import javax.xml.datatype.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
@Configuration
public class ServerSubscriptionManager extends CamelRouteManager {

    private static final java.lang.String ANSHAR_HEARTBEAT_KEY = "ANSHAR_HEARTBEAT_LOCK_KEY";

    private Logger logger = LoggerFactory.getLogger(ServerSubscriptionManager.class);

    @Autowired
    private IMap<String, OutboundSubscriptionSetup> subscriptions;

    @Autowired
    @Qualifier("getHeartbeatTimestampMap")
    private IMap<String, Instant> heartbeatTimestampMap;

    @Autowired
    @Qualifier("getLockMap")
    private IMap<String, Instant> lockMap;

    @Autowired
    private SiriObjectFactory siriObjectFactory;

    @Autowired
    private MappingAdapterPresets mappingAdapterPresets;

    @Value("${anshar.outbound.heartbeatinterval.minimum}")
    private long minimumHeartbeatInterval = 60000;

    @Value("${anshar.outbound.error.consumeraddress}")
    private String errorConsumerAddressMissing = "Error";

    @Value("${anshar.outbound.error.initialtermination}")
    private String initialTerminationTimePassed = "Error";

    @Value("${anshar.outbound.activemq.topic.prefix}")
    private String activeMqTopicPrefix = "activemq:topic:";

    @Value("${anshar.outbound.activemq.topic.timeToLive.millisec}")
    private int activeMqTopicTimeToLive = 30000;

    @Value("${anshar.outbound.activemq.topic.id.mapping.policy}")
    private OutboundIdMappingPolicy outboundIdMappingPolicy;

    @Value("${anshar.outbound.activemq.topic.enabled}")
    private boolean activeMqTopicEnabled;

    @Autowired
    private SiriHelper siriHelper;
    private OutboundSubscriptionSetup activeMQ_SX;
    private OutboundSubscriptionSetup activeMQ_VM;
    private OutboundSubscriptionSetup activeMQ_ET;
    private OutboundSubscriptionSetup activeMQ_PT;


    @PostConstruct
    private void initializeActiveMqProducers() {
        activeMQ_SX = createActiveMQSubscription(SubscriptionSetup.SubscriptionType.SITUATION_EXCHANGE);
        activeMQ_VM = createActiveMQSubscription(SubscriptionSetup.SubscriptionType.VEHICLE_MONITORING);
        activeMQ_ET = createActiveMQSubscription(SubscriptionSetup.SubscriptionType.ESTIMATED_TIMETABLE);
        activeMQ_PT = createActiveMQSubscription(SubscriptionSetup.SubscriptionType.PRODUCTION_TIMETABLE);
    }

    private OutboundSubscriptionSetup createActiveMQSubscription(SubscriptionSetup.SubscriptionType type) {
        return new OutboundSubscriptionSetup(
                type,
                activeMqTopicPrefix + type.name().toLowerCase(),
                activeMqTopicTimeToLive,
                mappingAdapterPresets.getOutboundAdapters(outboundIdMappingPolicy));
    }

    @PostConstruct
    private void startHeartbeatManager() {
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

        executor.scheduleAtFixedRate(() -> {
                    boolean acquiredLock = lockMap.tryLock(ANSHAR_HEARTBEAT_KEY);

                    if (acquiredLock) {
                        try {
                            if (!heartbeatTimestampMap.isEmpty()) {
                                heartbeatTimestampMap.keySet().forEach(key -> {

                                    OutboundSubscriptionSetup subscription = subscriptions.get(key);

                                    if (LocalDateTime.now().isAfter(subscription.getInitialTerminationTime().toLocalDateTime())) {
                                        logger.info("Subscription [{}] expired at {}, and will be terminated", subscription.getSubscriptionId(), subscription.getInitialTerminationTime());
                                        terminateSubscription(subscription.getSubscriptionId());

                                    } else if (heartbeatTimestampMap.get(key).isBefore(Instant.now().minusMillis(subscription.getHeartbeatInterval()))) {
                                        // More than "heartbeatinterval" since last heartbeat
                                        Siri heartbeatNotification = siriObjectFactory.createHeartbeatNotification(subscription.getSubscriptionId());
                                        pushSiriData(heartbeatNotification, subscription);
                                        heartbeatTimestampMap.put(key, Instant.now());
                                    }
                                });
                            }
                        } finally {
                            lockMap.unlock(ANSHAR_HEARTBEAT_KEY);
                        }
                    }
                },
                5000, 5000, TimeUnit.MILLISECONDS);
    }

    public JSONArray getSubscriptionsAsJson() {

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

        JSONArray stats = new JSONArray();

        for (String key : subscriptions.keySet()) {

            OutboundSubscriptionSetup subscription = subscriptions.get(key);

            JSONObject obj = new JSONObject();
            obj.put("subscriptionRef",""+key);
            obj.put("subscriptionType",""+subscription.getSubscriptionType());
            obj.put("address",""+subscription.getAddress());
            obj.put("heartbeatInterval",""+subscription.getHeartbeatInterval());
            obj.put("requestReceived", formatter.format(subscription.getRequestTimestamp()));
            obj.put("initialTerminationTime",formatter.format(subscription.getInitialTerminationTime()));

            stats.add(obj);
        }

        return stats;
    }

    public void handleSubscriptionRequest( SubscriptionRequest subscriptionRequest, String datasetId, OutboundIdMappingPolicy outboundIdMappingPolicy) {

        OutboundSubscriptionSetup subscription = createSubscription(subscriptionRequest, datasetId, outboundIdMappingPolicy);

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

        if (hasError) {
            Siri subscriptionResponse = siriObjectFactory.createSubscriptionResponse(subscription.getSubscriptionId(), false, errorText);
            pushSiriData(subscriptionResponse, subscription);
        } else {
            addSubscription(subscription);

            Siri subscriptionResponse = siriObjectFactory.createSubscriptionResponse(subscription.getSubscriptionId(), true, null);
            pushSiriData(subscriptionResponse, subscription);

            //Send initial ServiceDelivery
            Siri delivery = siriHelper.findInitialDeliveryData(subscription);

            if (delivery != null) {
                logger.info("Sending initial delivery to " + subscription.getAddress());
                pushSiriData(delivery, subscription);
            }
        }
    }



    private OutboundSubscriptionSetup createSubscription(SubscriptionRequest subscriptionRequest, String datasetId, OutboundIdMappingPolicy outboundIdMappingPolicy) {

        OutboundSubscriptionSetup setup = new OutboundSubscriptionSetup(
                subscriptionRequest.getRequestTimestamp(),
                getSubscriptionType(subscriptionRequest),
                SubscriptionSetup.SubscriptionMode.SUBSCRIBE,
                subscriptionRequest.getConsumerAddress() != null ? subscriptionRequest.getConsumerAddress():subscriptionRequest.getAddress(),
                getHeartbeatInterval(subscriptionRequest),
                getChangeBeforeUpdates(subscriptionRequest),
                SubscriptionSetup.ServiceType.REST,
                siriHelper.getFilter(subscriptionRequest),
                mappingAdapterPresets.getOutboundAdapters(outboundIdMappingPolicy),
                findSubscriptionIdentifier(subscriptionRequest),
                subscriptionRequest.getRequestorRef().getValue(),
                findInitialTerminationTime(subscriptionRequest),
                datasetId,
                true
                );
        return setup;
    }

    private long getHeartbeatInterval(SubscriptionRequest subscriptionRequest) {
        long heartbeatInterval = 0;
        if (subscriptionRequest.getSubscriptionContext() != null &&
                subscriptionRequest.getSubscriptionContext().getHeartbeatInterval() != null) {
            Duration interval = subscriptionRequest.getSubscriptionContext().getHeartbeatInterval();
            heartbeatInterval = interval.getTimeInMillis(new Date(0));
        }
        return Math.max(heartbeatInterval, minimumHeartbeatInterval);
    }

    private SubscriptionSetup.SubscriptionType getSubscriptionType(SubscriptionRequest subscriptionRequest) {
        if (siriHelper.containsValues(subscriptionRequest.getSituationExchangeSubscriptionRequests())) {
            return SubscriptionSetup.SubscriptionType.SITUATION_EXCHANGE;
        } else if (siriHelper.containsValues(subscriptionRequest.getVehicleMonitoringSubscriptionRequests())) {
            return SubscriptionSetup.SubscriptionType.VEHICLE_MONITORING;
        } else if (siriHelper.containsValues(subscriptionRequest.getEstimatedTimetableSubscriptionRequests())) {
            return SubscriptionSetup.SubscriptionType.ESTIMATED_TIMETABLE;
        } else if (siriHelper.containsValues(subscriptionRequest.getProductionTimetableSubscriptionRequests())) {
            return SubscriptionSetup.SubscriptionType.PRODUCTION_TIMETABLE;
        }
        return null;
    }

    private long getChangeBeforeUpdates(SubscriptionRequest subscriptionRequest) {
        if (siriHelper.containsValues(subscriptionRequest.getVehicleMonitoringSubscriptionRequests())) {
            return getMilliSeconds(subscriptionRequest.getVehicleMonitoringSubscriptionRequests().get(0).getChangeBeforeUpdates());
        } else if (siriHelper.containsValues(subscriptionRequest.getEstimatedTimetableSubscriptionRequests())) {
            return getMilliSeconds(subscriptionRequest.getEstimatedTimetableSubscriptionRequests().get(0).getChangeBeforeUpdates());
        } else if (siriHelper.containsValues(subscriptionRequest.getProductionTimetableSubscriptionRequests())) {
            return getMilliSeconds(subscriptionRequest.getEstimatedTimetableSubscriptionRequests().get(0).getChangeBeforeUpdates());
        }
        return 0;
    }

    private long getMilliSeconds(Duration changeBeforeUpdates) {
        if (changeBeforeUpdates != null) {
            return changeBeforeUpdates.getSeconds()*1000;
        }
        return 0;
    }

    private void addSubscription(OutboundSubscriptionSetup subscription) {
        subscriptions.put(subscription.getSubscriptionId(), subscription);
        heartbeatTimestampMap.put(subscription.getSubscriptionId(), Instant.now());
    }

    public OutboundSubscriptionSetup removeSubscription(String subscriptionId) {
        logger.info("Removing subscription {}" + subscriptionId);
        heartbeatTimestampMap.remove(subscriptionId);
        return subscriptions.remove(subscriptionId);
    }

    private String findSubscriptionIdentifier(SubscriptionRequest subscriptionRequest) {
        if (siriHelper.containsValues(subscriptionRequest.getSituationExchangeSubscriptionRequests())) {

            SituationExchangeSubscriptionStructure situationExchangeSubscriptionStructure = subscriptionRequest.
                    getSituationExchangeSubscriptionRequests().get(0);

            return getSubscriptionIdentifier(situationExchangeSubscriptionStructure);

        } else if (siriHelper.containsValues(subscriptionRequest.getVehicleMonitoringSubscriptionRequests())) {

            VehicleMonitoringSubscriptionStructure vehicleMonitoringSubscriptionStructure =
                    subscriptionRequest.getVehicleMonitoringSubscriptionRequests().get(0);

            return getSubscriptionIdentifier(vehicleMonitoringSubscriptionStructure);

        } else if (siriHelper.containsValues(subscriptionRequest.getEstimatedTimetableSubscriptionRequests())) {

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
        if (siriHelper.containsValues(subscriptionRequest.getSituationExchangeSubscriptionRequests())) {

            return subscriptionRequest.getSituationExchangeSubscriptionRequests().get(0).getInitialTerminationTime();
        } else if (siriHelper.containsValues(subscriptionRequest.getVehicleMonitoringSubscriptionRequests())) {

            return subscriptionRequest.getVehicleMonitoringSubscriptionRequests().get(0).getInitialTerminationTime();
        } else if (siriHelper.containsValues(subscriptionRequest.getEstimatedTimetableSubscriptionRequests())) {

            return subscriptionRequest.getEstimatedTimetableSubscriptionRequests().get(0).getInitialTerminationTime();
        }
        return null;
    }

    public void terminateSubscription(TerminateSubscriptionRequestStructure terminateSubscriptionRequest) {

        String subscriptionRef = terminateSubscriptionRequest.getSubscriptionReves().get(0).getValue();

        terminateSubscription(subscriptionRef);
    }

    private void terminateSubscription(String subscriptionRef) {
        OutboundSubscriptionSetup subscriptionRequest = removeSubscription(subscriptionRef);

        if (subscriptionRequest != null) {
            Siri terminateSubscriptionResponse = siriObjectFactory.createTerminateSubscriptionResponse(subscriptionRef);
            logger.info("Sending TerminateSubscriptionResponse to {}", subscriptionRequest.getAddress());

            pushSiriData(terminateSubscriptionResponse, subscriptionRequest);
        } else {
            logger.trace("Got TerminateSubscriptionRequest for non-existing subscription");
        }
    }

    public Siri handleCheckStatusRequest(CheckStatusRequestStructure checkStatusRequest) {
        String requestorRef = checkStatusRequest.getRequestorRef().getValue();

        OutboundSubscriptionSetup request = subscriptions.get(requestorRef);
        if (request == null) {

            return siriObjectFactory.createCheckStatusResponse();
        }

        Siri checkStatusResponse = siriObjectFactory.createCheckStatusResponse();
        pushSiriData(checkStatusResponse, request);
        return null;
    }

    public void pushUpdatedVehicleActivities(List<VehicleActivityStructure> addedOrUpdated, String datasetId) {

        if (addedOrUpdated == null || addedOrUpdated.isEmpty()) {
            return;
        }
        Siri delivery = siriObjectFactory.createVMServiceDelivery(addedOrUpdated);

        if (activeMqTopicEnabled) {
            pushSiriData(delivery, activeMQ_VM);
        }

        subscriptions.values().stream().filter(subscriptionRequest ->
                        (subscriptionRequest.getSubscriptionMode().equals(SubscriptionSetup.SubscriptionMode.SUBSCRIBE) &
                                subscriptionRequest.getSubscriptionType().equals(SubscriptionSetup.SubscriptionType.VEHICLE_MONITORING) &
                                (subscriptionRequest.getDatasetId() == null || (subscriptionRequest.getDatasetId().equals(datasetId))))

        ).forEach(subscription ->

                        pushSiriData(delivery, subscription)
        );

    }


    public void pushUpdatedSituations(List<PtSituationElement> addedOrUpdated, String datasetId) {

        if (addedOrUpdated == null || addedOrUpdated.isEmpty()) {
            return;
        }
        Siri delivery = siriObjectFactory.createSXServiceDelivery(addedOrUpdated);

        if (activeMqTopicEnabled) {
            pushSiriData(delivery, activeMQ_SX);
        }

        subscriptions.values().stream().filter(subscriptionRequest ->
                        (subscriptionRequest.getSubscriptionMode().equals(SubscriptionSetup.SubscriptionMode.SUBSCRIBE) &
                                subscriptionRequest.getSubscriptionType().equals(SubscriptionSetup.SubscriptionType.SITUATION_EXCHANGE) &
                                (subscriptionRequest.getDatasetId() == null || (subscriptionRequest.getDatasetId().equals(datasetId))))

        ).forEach(subscription ->

                        pushSiriData(delivery, subscription)
        );
    }
    public void pushUpdatedProductionTimetables(List<ProductionTimetableDeliveryStructure> addedOrUpdated, String datasetId) {

        if (addedOrUpdated == null || addedOrUpdated.isEmpty()) {
            return;
        }

        Siri delivery = siriObjectFactory.createPTServiceDelivery(addedOrUpdated);

        if (activeMqTopicEnabled) {
            pushSiriData(delivery, activeMQ_PT);
        }

        subscriptions.values().stream().filter(subscriptionRequest ->
                        (subscriptionRequest.getSubscriptionMode().equals(SubscriptionSetup.SubscriptionMode.SUBSCRIBE) &
                                subscriptionRequest.getSubscriptionType().equals(SubscriptionSetup.SubscriptionType.PRODUCTION_TIMETABLE) &
                                (subscriptionRequest.getDatasetId() == null || (subscriptionRequest.getDatasetId().equals(datasetId))))

        ).forEach(subscription ->
            pushSiriData(delivery, subscription)
        );
    }

    public void pushUpdatedEstimatedTimetables(List<EstimatedVehicleJourney> addedOrUpdated, String datasetId) {

        if (addedOrUpdated == null || addedOrUpdated.isEmpty()) {
            return;
        }

        Siri delivery = siriObjectFactory.createETServiceDelivery(addedOrUpdated);

        if (activeMqTopicEnabled) {
            pushSiriData(delivery, activeMQ_ET);
        }

        subscriptions.values().stream().filter(subscriptionRequest ->
                        (subscriptionRequest.getSubscriptionMode().equals(SubscriptionSetup.SubscriptionMode.SUBSCRIBE) &
                                subscriptionRequest.getSubscriptionType().equals(SubscriptionSetup.SubscriptionType.ESTIMATED_TIMETABLE) &
                                (subscriptionRequest.getDatasetId() == null || (subscriptionRequest.getDatasetId().equals(datasetId))))

        ).forEach(subscription ->
                        pushSiriData(delivery, subscription)
        );
    }
}
