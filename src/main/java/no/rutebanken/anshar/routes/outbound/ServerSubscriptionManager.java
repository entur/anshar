package no.rutebanken.anshar.routes.outbound;

import no.rutebanken.anshar.routes.siri.SiriObjectFactory;
import no.rutebanken.anshar.subscription.SubscriptionSetup;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import uk.org.siri.siri20.*;

import javax.xml.datatype.Duration;
import java.time.ZonedDateTime;
import java.util.*;

import static no.rutebanken.anshar.routes.outbound.SiriHelper.getFilter;

@Configuration
public class ServerSubscriptionManager extends CamelRouteManager {

    private Logger logger = LoggerFactory.getLogger(this.getClass());

    private static Map<String, OutboundSubscriptionSetup> subscriptions = new HashMap<>();
    private static Map<String, Timer> heartbeatTimerMap = new HashMap<>();


    @Value("${anshar.outbound.heartbeatinterval.minimum}")
    private long minimumHeartbeatInterval = 60000;

    @Value("${anshar.outbound.error.consumeraddress}")
    private String errorConsumerAddressMissing = "Error";

    @Value("${anshar.outbound.error.initialtermination}")
    private String initialTerminationTimePassed = "Error";

    public ServerSubscriptionManager() {

    }

    public String getSubscriptionsAsJson() {

        JSONArray stats = new JSONArray();


        for (String key : subscriptions.keySet()) {

            OutboundSubscriptionSetup subscription = subscriptions.get(key);

            JSONObject obj = new JSONObject();
            obj.put("subscriptionRef",""+key);
            obj.put("address",""+subscription.getAddress());
            obj.put("requestReceived",""+subscription.getRequestTimestamp());
            obj.put("initialTerminationTime",""+subscription.getInitialTerminationTime());

            stats.add(obj);
        }

        return stats.toJSONString();
    }

    public void handleSubscriptionRequest(SubscriptionRequest subscriptionRequest, String datasetId) {

        OutboundSubscriptionSetup subscription = createSubscription(subscriptionRequest, datasetId);

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
            Siri subscriptionResponse = SiriObjectFactory.createSubscriptionResponse(subscription.getSubscriptionId(), false, errorText);
            pushSiriData(subscriptionResponse, subscription);
        } else {
            subscriptions.put(subscription.getSubscriptionId(), subscription);

            if (subscriptionRequest.getSubscriptionContext() != null) {
                startHeartbeatNotifier(subscription);
            }

            Siri subscriptionResponse = SiriObjectFactory.createSubscriptionResponse(subscription.getSubscriptionId(), true, null);
            pushSiriData(subscriptionResponse, subscription);

            //Send initial ServiceDelivery
            Siri delivery = SiriHelper.findInitialDeliveryData(subscription);

            if (delivery != null) {
                logger.info("Sending initial delivery to " + subscription.getAddress());
                pushSiriData(delivery, subscription);
            }
        }
    }

    private OutboundSubscriptionSetup createSubscription(SubscriptionRequest subscriptionRequest, String datasetId) {

        OutboundSubscriptionSetup setup = new OutboundSubscriptionSetup(
                subscriptionRequest.getRequestTimestamp(),
                getSubscriptionType(subscriptionRequest),
                SubscriptionSetup.SubscriptionMode.SUBSCRIBE,
                subscriptionRequest.getConsumerAddress() != null ? subscriptionRequest.getConsumerAddress():subscriptionRequest.getAddress(),
                getHeartbeatInterval(subscriptionRequest),
                SubscriptionSetup.ServiceType.REST,
                getFilter(subscriptionRequest),
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
        if (SiriHelper.containsValues(subscriptionRequest.getSituationExchangeSubscriptionRequests())) {
            return SubscriptionSetup.SubscriptionType.SITUATION_EXCHANGE;
        } else if (SiriHelper.containsValues(subscriptionRequest.getVehicleMonitoringSubscriptionRequests())) {
            return SubscriptionSetup.SubscriptionType.VEHICLE_MONITORING;
        } else if (SiriHelper.containsValues(subscriptionRequest.getEstimatedTimetableSubscriptionRequests())) {
            return SubscriptionSetup.SubscriptionType.ESTIMATED_TIMETABLE;
        }
        return null;
    }

    private void startHeartbeatNotifier(final OutboundSubscriptionSetup subscription) {

        long heartbeatInterval = subscription.getHeartbeatInterval();

        if (heartbeatInterval < minimumHeartbeatInterval) {
            heartbeatInterval = minimumHeartbeatInterval;
        }

        if (heartbeatInterval > 0) {
            Timer timer = new Timer("HeartbeatNotifier"+subscription.getSubscriptionId());

            timer.schedule(new TimerTask() {
                               @Override
                               public void run() {
                                   if (ZonedDateTime.now().isAfter(subscription.getInitialTerminationTime())) {
                                       logger.info("Subscription [{}] has expired, and will be terminated", subscription.getSubscriptionId());
                                       terminateSubscription(subscription.getSubscriptionId());
                                   } else {
                                       Siri heartbeatNotification = SiriObjectFactory.createHeartbeatNotification(subscription.getSubscriptionId());
                                       logger.info("Sending heartbeat to {}", subscription.getSubscriptionId());
                                       pushSiriData(heartbeatNotification, subscription);
                                   }
                               }
                           },
                    heartbeatInterval,
                    heartbeatInterval);

            Timer previousTimer = heartbeatTimerMap.put(subscription.getSubscriptionId(), timer);
            if (previousTimer != null) {
                previousTimer.cancel();
            }
        }
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

    public void terminateSubscription(TerminateSubscriptionRequestStructure terminateSubscriptionRequest) {

        String subscriptionRef = terminateSubscriptionRequest.getSubscriptionReves().get(0).getValue();

        terminateSubscription(subscriptionRef);
    }

    private void terminateSubscription(String subscriptionRef) {
        OutboundSubscriptionSetup subscriptionRequest = subscriptions.remove(subscriptionRef);

        Timer timer = heartbeatTimerMap.remove(subscriptionRef);
        if (timer != null) {
            timer.cancel();
        }
        if (subscriptionRequest != null) {
            Siri terminateSubscriptionResponse = SiriObjectFactory.createTerminateSubscriptionResponse(subscriptionRef);
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

            return SiriObjectFactory.createCheckStatusResponse();
        }

        Siri checkStatusResponse = SiriObjectFactory.createCheckStatusResponse();
        pushSiriData(checkStatusResponse, request);
        return null;
    }

    public void pushUpdatedVehicleActivities(List<VehicleActivityStructure> addedOrUpdated, String datasetId) {

        if (addedOrUpdated == null || addedOrUpdated.isEmpty()) {
            return;
        }
        Siri delivery = SiriObjectFactory.createVMServiceDelivery(addedOrUpdated);

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
        Siri delivery = SiriObjectFactory.createSXServiceDelivery(addedOrUpdated);

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

        Siri delivery = SiriObjectFactory.createPTServiceDelivery(addedOrUpdated);

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

        Siri delivery = SiriObjectFactory.createETServiceDelivery(addedOrUpdated);

        subscriptions.values().stream().filter(subscriptionRequest ->
                        (subscriptionRequest.getSubscriptionMode().equals(SubscriptionSetup.SubscriptionMode.SUBSCRIBE) &
                                subscriptionRequest.getSubscriptionType().equals(SubscriptionSetup.SubscriptionType.ESTIMATED_TIMETABLE) &
                                (subscriptionRequest.getDatasetId() == null || (subscriptionRequest.getDatasetId().equals(datasetId))))

        ).forEach(subscription ->
                        pushSiriData(delivery, subscription)
        );
    }
}
