package no.rutebanken.anshar.routes.outbound;

import no.rutebanken.anshar.messages.EstimatedTimetables;
import no.rutebanken.anshar.messages.Situations;
import no.rutebanken.anshar.messages.VehicleActivities;
import no.rutebanken.anshar.routes.siri.SiriObjectFactory;
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

    private Map<String, SubscriptionRequest> subscriptions = new HashMap<>();
    private Map<String, Timer> heartbeatTimerMap = new HashMap<>();


    @Value("${anshar.outbound.heartbeatinterval.minimum}")
    private static final long minimumHeartbeatInterval = 60000;

    @Value("${anshar.outbound.error.consumeraddress}")
    private String errorConsumerAddressMissing = "Error";

    @Value("${anshar.outbound.error.initialtermination}")
    private String initialTerminationTimePassed = "Error";

    public ServerSubscriptionManager() {

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
        } else if (initialTerminationTime.isBefore(ZonedDateTime.now())) {
            //Subscription has already expired
            hasError = true;
            errorText = initialTerminationTimePassed;
        }
        if (hasError) {
            Siri subscriptionResponse = SiriObjectFactory.createSubscriptionResponse(subscriptionRef, false, errorText);
            pushSiriData(subscriptionResponse, consumerAddress);
        } else {
            if (subscriptionRequest.getSubscriptionContext() != null) {
                startHeartbeatNotifier(subscriptionRef, consumerAddress, initialTerminationTime, subscriptionRequest.getSubscriptionContext().getHeartbeatInterval());
            }

            Siri subscriptionResponse = SiriObjectFactory.createSubscriptionResponse(subscriptionRef, true, null);
            pushSiriData(subscriptionResponse, consumerAddress);

            //Send initial ServiceDelivery
            Siri delivery = findInitialDeliveryData(subscriptionRequest);

            if (delivery != null) {
                logger.info("Sending initial delivery to " + consumerAddress);
                pushSiriData(delivery, consumerAddress);
            }
        }
    }

    private Siri findInitialDeliveryData(SubscriptionRequest subscriptionRequest) {
        Siri delivery = null;
        if (subscriptionRequest.getSituationExchangeSubscriptionRequests() != null &&
                !subscriptionRequest.getSituationExchangeSubscriptionRequests().isEmpty()) {

            logger.info("SX-subscription - {} elements returned", Situations.getAll().size());
            delivery = SiriObjectFactory.createSXServiceDelivery(Situations.getAll());
        } else if (subscriptionRequest.getVehicleMonitoringSubscriptionRequests() != null &&
                !subscriptionRequest.getVehicleMonitoringSubscriptionRequests().isEmpty()) {

            logger.info("VM-subscription - {} elements returned", VehicleActivities.getAll().size());
            delivery = SiriObjectFactory.createVMServiceDelivery(VehicleActivities.getAll());
        } else if (subscriptionRequest.getEstimatedTimetableSubscriptionRequests() != null &&
                !subscriptionRequest.getEstimatedTimetableSubscriptionRequests().isEmpty()) {

            logger.info("ET-subscription - {} elements returned", EstimatedTimetables.getAll().size());
            delivery = SiriObjectFactory.createETServiceDelivery(EstimatedTimetables.getAll());
        }
        return delivery;
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
                                       //Subscription has expired, and will be terminated
                                       terminateSubscription(subscriptionRef);
                                   } else {
                                       Siri heartbeatNotification = SiriObjectFactory.createHeartbeatNotification(subscriptionRef);
                                       pushSiriData(heartbeatNotification, consumerAddress);
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
        if (subscriptionRequest.getSituationExchangeSubscriptionRequests() != null &&
                !subscriptionRequest.getSituationExchangeSubscriptionRequests().isEmpty()) {

            SituationExchangeSubscriptionStructure situationExchangeSubscriptionStructure = subscriptionRequest.
                    getSituationExchangeSubscriptionRequests().get(0);

            return getSubscriptionIdentifier(situationExchangeSubscriptionStructure);

        } else if (subscriptionRequest.getVehicleMonitoringSubscriptionRequests() != null &&
                !subscriptionRequest.getVehicleMonitoringSubscriptionRequests().isEmpty()) {

            VehicleMonitoringSubscriptionStructure vehicleMonitoringSubscriptionStructure =
                    subscriptionRequest.getVehicleMonitoringSubscriptionRequests().get(0);

            return getSubscriptionIdentifier(vehicleMonitoringSubscriptionStructure);

        } else if (subscriptionRequest.getEstimatedTimetableSubscriptionRequests() != null &&
                !subscriptionRequest.getEstimatedTimetableSubscriptionRequests().isEmpty()) {

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
        if (subscriptionRequest.getSituationExchangeSubscriptionRequests() != null &&
                !subscriptionRequest.getSituationExchangeSubscriptionRequests().isEmpty()) {

            return subscriptionRequest.getSituationExchangeSubscriptionRequests().get(0).getInitialTerminationTime();

        } else if (subscriptionRequest.getVehicleMonitoringSubscriptionRequests() != null &&
                !subscriptionRequest.getVehicleMonitoringSubscriptionRequests().isEmpty()) {

            return subscriptionRequest.getVehicleMonitoringSubscriptionRequests().get(0).getInitialTerminationTime();

        } else if (subscriptionRequest.getEstimatedTimetableSubscriptionRequests() != null &&
                !subscriptionRequest.getEstimatedTimetableSubscriptionRequests().isEmpty()) {

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

        Siri terminateSubscriptionResponse = SiriObjectFactory.createTerminateSubscriptionResponse(subscriptionRef);
        pushSiriData(terminateSubscriptionResponse, subscriptionRequest.getConsumerAddress());
    }

    public Siri handleCheckStatusRequest(CheckStatusRequestStructure checkStatusRequest) {
        String requestorRef = checkStatusRequest.getRequestorRef().getValue();

        SubscriptionRequest request = subscriptions.get(requestorRef);
        if (request == null) {

            return SiriObjectFactory.createCheckStatusResponse();
        }
        String consumerAddress = request.getConsumerAddress();

        Siri checkStatusResponse = SiriObjectFactory.createCheckStatusResponse();
        pushSiriData(checkStatusResponse, consumerAddress);
        return null;
    }

    private void pushSiriData(Siri payload, String consumerAddress) {
        try {

            SiriPushRouteBuilder siriPushRouteBuilder = new SiriPushRouteBuilder(consumerAddress, false);
            String routeId = addSiriPushRoute(siriPushRouteBuilder);

            executeSiriPushRoute(payload, siriPushRouteBuilder.getRouteName());
            stopAndRemoveSiriPushRoute(routeId);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void pushUpdated(List<VehicleActivityStructure> addedOrUpdated) {

        Siri delivery = SiriObjectFactory.createVMServiceDelivery(addedOrUpdated);

        subscriptions.values().stream().filter(subscriptionRequest ->
                (subscriptionRequest.getVehicleMonitoringSubscriptionRequests() != null &&
                        !subscriptionRequest.getVehicleMonitoringSubscriptionRequests().isEmpty())
        ).forEach(subscription ->
                        pushSiriData(delivery, subscription.getConsumerAddress())
        );

    }
}
