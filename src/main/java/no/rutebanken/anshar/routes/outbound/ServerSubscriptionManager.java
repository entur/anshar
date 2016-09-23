package no.rutebanken.anshar.routes.outbound;

import no.rutebanken.anshar.messages.EstimatedTimetables;
import no.rutebanken.anshar.messages.Situations;
import no.rutebanken.anshar.messages.VehicleActivities;
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
            obj.put("address",subscriptionRequest.getConsumerAddress());
            obj.put("requestReceived",subscriptionRequest.getRequestTimestamp());
            obj.put("initialTerminationTime",findInitialTerminationTime(subscriptionRequest));

            stats.add(obj);
        }

        return stats.toJSONString();
    }

    public void handleSubscriptionRequest(SubscriptionRequest subscriptionRequest) {

        String subscriptionRef = findSubscriptionIdentifier(subscriptionRequest);

        subscriptions.put(subscriptionRef, subscriptionRequest);

        final String consumerAddress = subscriptionRequest.getConsumerAddress();
        final ZonedDateTime initialTerminationTime = findInitialTerminationTime(subscriptionRequest);
        boolean hasError = false;
        String errorText = null;

        if (consumerAddress == null) {
            hasError = true;
            errorText = errorConsumerAddressMissing;
        } else if (initialTerminationTime == null || initialTerminationTime.isBefore(ZonedDateTime.now())) {
            //Subscription has already expired
            hasError = true;
            errorText = initialTerminationTimePassed;
        }
        if (hasError) {
            Siri subscriptionResponse = SiriObjectFactory.createSubscriptionResponse(subscriptionRef, false, errorText);
            pushSiriData(subscriptionResponse, consumerAddress, false);
        } else {
            if (subscriptionRequest.getSubscriptionContext() != null) {
                startHeartbeatNotifier(subscriptionRef, consumerAddress, initialTerminationTime, subscriptionRequest.getSubscriptionContext().getHeartbeatInterval());
            }

            Siri subscriptionResponse = SiriObjectFactory.createSubscriptionResponse(subscriptionRef, true, null);
            pushSiriData(subscriptionResponse, consumerAddress, false);

            //Send initial ServiceDelivery
            Siri delivery = findInitialDeliveryData(subscriptionRequest);

            if (delivery != null) {
                logger.info("Sending initial delivery to " + consumerAddress);
                pushSiriData(delivery, consumerAddress, false);
            }
        }
    }

    private Siri findInitialDeliveryData(SubscriptionRequest subscriptionRequest) {
        Siri delivery = null;
        if (containsValues(subscriptionRequest.getSituationExchangeSubscriptionRequests())) {

            logger.info("SX-subscription - {} elements returned", Situations.getAll().size());
            delivery = SiriObjectFactory.createSXServiceDelivery(Situations.getAll());
        } else if (containsValues(subscriptionRequest.getVehicleMonitoringSubscriptionRequests())) {

            logger.info("VM-subscription - {} elements returned", VehicleActivities.getAll().size());
            delivery = SiriObjectFactory.createVMServiceDelivery(VehicleActivities.getAll());
        } else if (containsValues(subscriptionRequest.getEstimatedTimetableSubscriptionRequests())) {

            logger.info("ET-subscription - {} elements returned", EstimatedTimetables.getAll().size());
            delivery = SiriObjectFactory.createETServiceDelivery(EstimatedTimetables.getAll());
        }
        return delivery;
    }

    private boolean containsValues(List list) {
        return (list != null && !list.isEmpty());
    }

    private void startHeartbeatNotifier(final String subscriptionRef,
                                        final String consumerAddress,
                                        final ZonedDateTime initialTerminationTime,
                                        Duration heartbeatIntervalDuration) {

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
                                       pushSiriData(heartbeatNotification, consumerAddress, false);
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
        if (containsValues(subscriptionRequest.getSituationExchangeSubscriptionRequests())) {

            SituationExchangeSubscriptionStructure situationExchangeSubscriptionStructure = subscriptionRequest.
                    getSituationExchangeSubscriptionRequests().get(0);

            return getSubscriptionIdentifier(situationExchangeSubscriptionStructure);

        } else if (containsValues(subscriptionRequest.getVehicleMonitoringSubscriptionRequests())) {

            VehicleMonitoringSubscriptionStructure vehicleMonitoringSubscriptionStructure =
                    subscriptionRequest.getVehicleMonitoringSubscriptionRequests().get(0);

            return getSubscriptionIdentifier(vehicleMonitoringSubscriptionStructure);

        } else if (containsValues(subscriptionRequest.getEstimatedTimetableSubscriptionRequests())) {

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
        if (containsValues(subscriptionRequest.getSituationExchangeSubscriptionRequests())) {

            return subscriptionRequest.getSituationExchangeSubscriptionRequests().get(0).getInitialTerminationTime();
        } else if (containsValues(subscriptionRequest.getVehicleMonitoringSubscriptionRequests())) {

            return subscriptionRequest.getVehicleMonitoringSubscriptionRequests().get(0).getInitialTerminationTime();
        } else if (containsValues(subscriptionRequest.getEstimatedTimetableSubscriptionRequests())) {

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

            pushSiriData(terminateSubscriptionResponse, subscriptionRequest.getConsumerAddress(), false);
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
        String consumerAddress = request.getConsumerAddress();

        Siri checkStatusResponse = SiriObjectFactory.createCheckStatusResponse();
        pushSiriData(checkStatusResponse, consumerAddress, false);
        return null;
    }

    public void pushUpdatedVehicleActivities(List<VehicleActivityStructure> addedOrUpdated) {

        Siri delivery = SiriObjectFactory.createVMServiceDelivery(addedOrUpdated);

        subscriptions.values().stream().filter(subscriptionRequest ->
                        (containsValues(subscriptionRequest.getVehicleMonitoringSubscriptionRequests()))

        ).forEach(subscription ->
                        pushSiriData(delivery, subscription.getConsumerAddress(), false)
        );

    }

    public void pushUpdatedSituations(List<PtSituationElement> addedOrUpdated) {

        Siri delivery = SiriObjectFactory.createSXServiceDelivery(addedOrUpdated);

        subscriptions.values().stream().filter(subscriptionRequest ->
                        (containsValues(subscriptionRequest.getSituationExchangeSubscriptionRequests()))

        ).forEach(subscription ->
                        pushSiriData(delivery, subscription.getConsumerAddress(), false)
        );
    }

    public void pushUpdatedProductionTimetables(List<ProductionTimetableDeliveryStructure> addedOrUpdated) {

        Siri delivery = SiriObjectFactory.createPTServiceDelivery(addedOrUpdated);

        subscriptions.values().stream().filter(subscriptionRequest ->
                        (containsValues(subscriptionRequest.getProductionTimetableSubscriptionRequests()))

        ).forEach(subscription ->
                        pushSiriData(delivery, subscription.getConsumerAddress(), false)
        );
    }

    public void pushUpdatedEstimatedTimetables(List<EstimatedVehicleJourney> addedOrUpdated) {

        Siri delivery = SiriObjectFactory.createETServiceDelivery(addedOrUpdated);

        subscriptions.values().stream().filter(subscriptionRequest ->
                        (containsValues(subscriptionRequest.getEstimatedTimetableSubscriptionRequests()))

        ).forEach(subscription ->
                        pushSiriData(delivery, subscription.getConsumerAddress(), false)
        );

    }
}
