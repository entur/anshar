package no.rutebanken.anshar.routes.siri;

import com.sun.xml.bind.marshaller.NamespacePrefixMapper;
import no.rutebanken.anshar.routes.BaseRouteBuilder;
import no.rutebanken.anshar.routes.CamelConfiguration;
import no.rutebanken.anshar.subscription.RequestType;
import no.rutebanken.anshar.subscription.SubscriptionManager;
import no.rutebanken.anshar.subscription.SubscriptionSetup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

@Component
public abstract class SiriSubscriptionRouteBuilder extends BaseRouteBuilder {

    private Logger logger = LoggerFactory.getLogger(this.getClass());
    
    NamespacePrefixMapper customNamespacePrefixMapper;

    SubscriptionSetup subscriptionSetup;

    protected boolean hasBeenStarted;

    private Instant lastCheckStatus = Instant.now();

    public SiriSubscriptionRouteBuilder(CamelConfiguration config, SubscriptionManager subscriptionManager) {
        super(config, subscriptionManager);
    }

    String getTimeout() {
        int timeout;
        Duration heartbeatInterval = subscriptionSetup.getHeartbeatInterval();
        if (heartbeatInterval != null) {
            long heartbeatIntervalMillis = heartbeatInterval.toMillis();
            timeout = (int) heartbeatIntervalMillis / 2;
        } else {
            timeout = 30000;
        }

        return "?httpClient.socketTimeout=" + timeout + "&httpClient.connectTimeout=" + timeout;
    }

    String getTimeToLive() {
        return config.getTimeToLive();
    }

    void initTriggerRoutes() {
        if (!subscriptionManager.isNewSubscription(subscriptionSetup.getSubscriptionId())) {
            logger.info("Subscription is NOT new - flagging as already started if active {}", subscriptionSetup);
            hasBeenStarted = subscriptionManager.isActiveSubscription(subscriptionSetup.getSubscriptionId());
        }

        singletonFrom("quartz2://anshar/monitor_" + subscriptionSetup.getSubscriptionId() + "?fireNow=true&trigger.repeatInterval=" + 15000,
                "monitor.subscription." + subscriptionSetup.getVendor())
                .choice()
                .when(p -> shouldBeStarted(p.getFromRouteId()))
                    .log("Triggering start subscription: " + subscriptionSetup)
                    .process(p -> hasBeenStarted = true)
                    .to("direct:" + subscriptionSetup.getStartSubscriptionRouteName()) // Start subscription
                .when(p -> shouldBeCancelled(p.getFromRouteId()))
                    .log("Triggering cancel subscription: " + subscriptionSetup)
                    .process(p -> hasBeenStarted = false)
                    .to("direct:" + subscriptionSetup.getCancelSubscriptionRouteName())// Cancel subscription
                .when(p -> shouldCheckStatus(p.getFromRouteId()))
                    .log("Check status: " + subscriptionSetup)
                    .process(p -> lastCheckStatus = Instant.now())
                    .to("direct:" + subscriptionSetup.getCheckStatusRouteName()) // Check status
                .end()
        ;

    }

    private boolean shouldCheckStatus(String routeId) {
        if (!isLeader(routeId)) {
            return false;
        }
        boolean isActive = subscriptionManager.isActiveSubscription(subscriptionSetup.getSubscriptionId());
        boolean requiresCheckStatusRequest = subscriptionSetup.getUrlMap().get(RequestType.CHECK_STATUS) != null;
        boolean isTimeToCheckStatus = lastCheckStatus.isBefore(Instant.now().minus(subscriptionSetup.getHeartbeatInterval()));

        return isActive & requiresCheckStatusRequest & isTimeToCheckStatus;
    }

    private boolean shouldBeStarted(String routeId) {
        if (!isLeader(routeId)) {
            return false;
        }
        boolean isActive = subscriptionManager.isActiveSubscription(subscriptionSetup.getSubscriptionId());

        boolean shouldBeStarted = (isActive & !hasBeenStarted);
        return shouldBeStarted;
    }

    private boolean shouldBeCancelled(String routeId) {
        if (!isLeader(routeId)) {
            return false;
        }
        boolean isActive = subscriptionManager.isActiveSubscription(subscriptionSetup.getSubscriptionId());
        boolean isHealthy = subscriptionManager.isSubscriptionHealthy(subscriptionSetup.getSubscriptionId());

        boolean shouldBeCancelled = (hasBeenStarted & !isActive) | (hasBeenStarted & isActive & !isHealthy);

        return shouldBeCancelled;
    }
}
