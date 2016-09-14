package no.rutebanken.anshar.routes.outbound;

import no.rutebanken.anshar.messages.EstimatedTimetables;
import no.rutebanken.anshar.messages.Situations;
import no.rutebanken.anshar.messages.VehicleActivities;
import no.rutebanken.anshar.routes.siri.SiriObjectFactory;
import uk.org.siri.siri20.CheckStatusRequestStructure;
import uk.org.siri.siri20.Siri;
import uk.org.siri.siri20.SubscriptionRequest;
import uk.org.siri.siri20.TerminateSubscriptionRequestStructure;

import javax.xml.datatype.Duration;
import java.util.*;

public class ServerSubscriptionManager extends CamelRouteManager {


    private Map<String, SubscriptionRequest> subscriptions = new HashMap<>();
    private Map<String, Timer> heartbeatTimerMap = new HashMap<>();
    private static final long minimumHeartbeatInterval = 60000;

    public ServerSubscriptionManager() {
    }

    public void addSubscription(SubscriptionRequest subscriptionRequest) {
        String requestorRef = subscriptionRequest.getRequestorRef().getValue();
        subscriptions.put(requestorRef, subscriptionRequest);

        final String consumerAddress = subscriptionRequest.getConsumerAddress();

        if (subscriptionRequest.getSubscriptionContext() != null) {
            Duration heartbeatIntervalDuration = subscriptionRequest.getSubscriptionContext().getHeartbeatInterval();

            long heartbeatInterval = heartbeatIntervalDuration.getTimeInMillis(new Date());
            if (heartbeatInterval < minimumHeartbeatInterval) {
                heartbeatInterval = minimumHeartbeatInterval;
            }

            if (heartbeatInterval > 0) {
                Timer timer = new Timer(requestorRef, true);

                timer.scheduleAtFixedRate(new TimerTask() {
                                  @Override
                                  public void run() {
                                      Siri heartbeatNotification = SiriObjectFactory.createHeartbeatNotification(requestorRef);
                                      pushSiriData(heartbeatNotification, consumerAddress);
                                  }
                              },
                        heartbeatInterval,
                        heartbeatInterval);

                Timer previousTimer = heartbeatTimerMap.put(requestorRef, timer);
                if (previousTimer != null) {
                    previousTimer.cancel();
                }
            }
        }

        //Send initial ServiceDelivery
        Siri delivery = null;
        if (subscriptionRequest.getSituationExchangeSubscriptionRequests() != null &&
                !subscriptionRequest.getSituationExchangeSubscriptionRequests().isEmpty()) {

            delivery = SiriObjectFactory.createSXSiriObject(Situations.getAll());
        } else if (subscriptionRequest.getVehicleMonitoringSubscriptionRequests() != null &&
                !subscriptionRequest.getVehicleMonitoringSubscriptionRequests().isEmpty()) {

            delivery = SiriObjectFactory.createVMSiriObject(VehicleActivities.getAll());
        } else if (subscriptionRequest.getEstimatedTimetableSubscriptionRequests() != null &&
                !subscriptionRequest.getEstimatedTimetableSubscriptionRequests().isEmpty()) {

            delivery = SiriObjectFactory.createETSiriObject(EstimatedTimetables.getAll());
        }

        if (delivery != null) {
            System.out.println("Sending initial delivery to " + consumerAddress);
            pushSiriData(delivery, consumerAddress);
        }
    }

    public void terminateSubscription(TerminateSubscriptionRequestStructure terminateSubscriptionRequest) {
        String requestorRef = terminateSubscriptionRequest.getRequestorRef().getValue();
        subscriptions.remove(requestorRef);

        Timer timer = heartbeatTimerMap.remove(requestorRef);
        if (timer != null) {
            timer.cancel();
        }
    }

    public void handleCheckStatusRequest(CheckStatusRequestStructure checkStatusRequest) {
        String requestorRef = checkStatusRequest.getRequestorRef().getValue();

        SubscriptionRequest request = subscriptions.get(requestorRef);
        String consumerAddress = request.getConsumerAddress();

        Siri checkStatusResponse = SiriObjectFactory.createCheckStatusResponse();
        pushSiriData(checkStatusResponse, consumerAddress);
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
}
