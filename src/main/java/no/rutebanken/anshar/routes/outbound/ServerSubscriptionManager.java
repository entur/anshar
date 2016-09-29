package no.rutebanken.anshar.routes.outbound;

import no.rutebanken.anshar.routes.siri.SiriObjectFactory;
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

@Configuration
public class ServerSubscriptionManager extends CamelRouteManager {

    private Logger logger = LoggerFactory.getLogger(this.getClass());

    private static Map<String, SubscriptionRequest> subscriptions = new HashMap<>();
    private static Map<String, Timer> heartbeatTimerMap = new HashMap<>();


    @Value("${anshar.outbound.heartbeatinterval.minimum}")
    private static final long minimumHeartbeatInterval = 60000;

    @Value("${anshar.outbound.error.consumeraddress}")
    private String errorConsumerAddressMissing = "Error";

    @Value("${anshar.outbound.error.initialtermination}")
    private String initialTerminationTimePassed = "Error";

    public ServerSubscriptionManager() {

    }

    public String getSubscriptionsAsJson() {

        JSONArray stats = new JSONArray();


        for (String key : subscriptions.keySet()) {

            SubscriptionRequest subscriptionRequest = subscriptions.get(key);

            JSONObject obj = new JSONObject();
            obj.put("subscriptionRef",""+key);
            obj.put("address",""+subscriptionRequest.getConsumerAddress());
            obj.put("requestReceived",""+subscriptionRequest.getRequestTimestamp());
            obj.put("initialTerminationTime",""+findInitialTerminationTime(subscriptionRequest));

            stats.add(obj);
        }

        return stats.toJSONString();
    }

    public void handleSubscriptionRequest(SubscriptionRequest subscriptionRequest) {

        String subscriptionRef = findSubscriptionIdentifier(subscriptionRequest);

        subscriptions.put(subscriptionRef, subscriptionRequest);

        final ZonedDateTime initialTerminationTime = findInitialTerminationTime(subscriptionRequest);
        boolean hasError = false;
        String errorText = null;

        String consumerAddress = subscriptionRequest.getConsumerAddress();
        if (consumerAddress == null) {
            subscriptionRequest.setConsumerAddress(subscriptionRequest.getAddress());
        }

        if (subscriptionRequest.getConsumerAddress() == null) {
            hasError = true;
            errorText = errorConsumerAddressMissing;
        } else if (initialTerminationTime == null || initialTerminationTime.isBefore(ZonedDateTime.now())) {
            //Subscription has already expired
            hasError = true;
            errorText = initialTerminationTimePassed;
        }
        if (hasError) {
            Siri subscriptionResponse = SiriObjectFactory.createSubscriptionResponse(subscriptionRef, false, errorText);
            pushSiriData(subscriptionResponse, subscriptionRequest, false);
        } else {
            if (subscriptionRequest.getSubscriptionContext() != null) {
                startHeartbeatNotifier(subscriptionRef, subscriptionRequest, initialTerminationTime);
            }

            Siri subscriptionResponse = SiriObjectFactory.createSubscriptionResponse(subscriptionRef, true, null);
            pushSiriData(subscriptionResponse, subscriptionRequest, false);

            //Send initial ServiceDelivery
            Siri delivery = SiriHelper.findInitialDeliveryData(subscriptionRequest);

            if (delivery != null) {
                logger.info("Sending initial delivery to " + subscriptionRequest.getConsumerAddress());
                pushSiriData(delivery, subscriptionRequest, false);
            }
        }
    }

    private void startHeartbeatNotifier(final String subscriptionRef,
                                        final SubscriptionRequest subscriptionRequest,
                                        final ZonedDateTime initialTerminationTime) {

        Duration heartbeatIntervalDuration = subscriptionRequest.getSubscriptionContext().getHeartbeatInterval();
        long heartbeatInterval = heartbeatIntervalDuration.getTimeInMillis(new Date());

        if (heartbeatInterval < minimumHeartbeatInterval) {
            heartbeatInterval = minimumHeartbeatInterval;
        }

        if (heartbeatInterval > 0) {
            Timer timer = new Timer("HeartbeatNotifier"+subscriptionRef);

            timer.schedule(new TimerTask() {
                               @Override
                               public void run() {
                                   if (ZonedDateTime.now().isAfter(initialTerminationTime)) {
                                       logger.info("Subscription [{}] has expired, and will be terminated", subscriptionRef);
                                       terminateSubscription(subscriptionRef);
                                   } else {
                                       Siri heartbeatNotification = SiriObjectFactory.createHeartbeatNotification(subscriptionRef);
                                       logger.info("Sending heartbeat to {}", subscriptionRef);
                                       pushSiriData(heartbeatNotification, subscriptionRequest, false);
                                   }
                               }
                           },
                    heartbeatInterval,
                    heartbeatInterval);

            Timer previousTimer = heartbeatTimerMap.put(subscriptionRef, timer);
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
        SubscriptionRequest subscriptionRequest = subscriptions.remove(subscriptionRef);

        Timer timer = heartbeatTimerMap.remove(subscriptionRef);
        if (timer != null) {
            timer.cancel();
        }
        if (subscriptionRequest != null) {
            Siri terminateSubscriptionResponse = SiriObjectFactory.createTerminateSubscriptionResponse(subscriptionRef);
            logger.info("Sending TerminateSubscriptionResponse to {}", subscriptionRequest.getConsumerAddress());

            pushSiriData(terminateSubscriptionResponse, subscriptionRequest, false);
        } else {
            logger.trace("Got TerminateSubscriptionRequest for non-existing subscription");
        }
    }

    public Siri handleCheckStatusRequest(CheckStatusRequestStructure checkStatusRequest) {
        String requestorRef = checkStatusRequest.getRequestorRef().getValue();

        SubscriptionRequest request = subscriptions.get(requestorRef);
        if (request == null) {

            return SiriObjectFactory.createCheckStatusResponse();
        }

        Siri checkStatusResponse = SiriObjectFactory.createCheckStatusResponse();
        pushSiriData(checkStatusResponse, request, false);
        return null;
    }

    public void pushUpdatedVehicleActivities(List<VehicleActivityStructure> addedOrUpdated) {

        if (addedOrUpdated == null || addedOrUpdated.isEmpty()) {
            return;
        }
        Siri delivery = SiriObjectFactory.createVMServiceDelivery(addedOrUpdated);

        subscriptions.values().stream().filter(subscriptionRequest ->
                        (SiriHelper.containsValues(subscriptionRequest.getVehicleMonitoringSubscriptionRequests()))

        ).forEach(subscription ->

            pushSiriData(delivery, subscription, false)
        );

    }


    public void pushUpdatedSituations(List<PtSituationElement> addedOrUpdated) {

        if (addedOrUpdated == null || addedOrUpdated.isEmpty()) {
            return;
        }
        Siri delivery = SiriObjectFactory.createSXServiceDelivery(addedOrUpdated);

        subscriptions.values().stream().filter(subscriptionRequest ->
                        (SiriHelper.containsValues(subscriptionRequest.getSituationExchangeSubscriptionRequests()))

        ).forEach(subscription ->

            pushSiriData(delivery, subscription, false)
        );
    }
    public void pushUpdatedProductionTimetables(List<ProductionTimetableDeliveryStructure> addedOrUpdated) {

        if (addedOrUpdated == null || addedOrUpdated.isEmpty()) {
            return;
        }

        Siri delivery = SiriObjectFactory.createPTServiceDelivery(addedOrUpdated);

        subscriptions.values().stream().filter(subscriptionRequest ->
                        (SiriHelper.containsValues(subscriptionRequest.getProductionTimetableSubscriptionRequests()))

        ).forEach(subscription ->
            pushSiriData(delivery, subscription, false)
        );
    }

    public void pushUpdatedEstimatedTimetables(List<EstimatedVehicleJourney> addedOrUpdated) {

        if (addedOrUpdated == null || addedOrUpdated.isEmpty()) {
            return;
        }

        Siri delivery = SiriObjectFactory.createETServiceDelivery(addedOrUpdated);

        subscriptions.values().stream().filter(subscriptionRequest ->
                        (SiriHelper.containsValues(subscriptionRequest.getEstimatedTimetableSubscriptionRequests()))

        ).forEach(subscription ->
            pushSiriData(delivery, subscription, false)
        );

    }
}
