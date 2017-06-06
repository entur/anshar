package no.rutebanken.anshar.routes.siri;

import com.sun.xml.bind.marshaller.NamespacePrefixMapper;
import no.rutebanken.anshar.routes.BaseRouteBuilder;
import no.rutebanken.anshar.subscription.SubscriptionManager;
import no.rutebanken.anshar.subscription.SubscriptionSetup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

public abstract class SiriSubscriptionRouteBuilder extends BaseRouteBuilder {

    private Logger logger = LoggerFactory.getLogger(this.getClass());

    NamespacePrefixMapper customNamespacePrefixMapper;

    SubscriptionSetup subscriptionSetup;
    private boolean hasBeenStarted;

    public SiriSubscriptionRouteBuilder(SubscriptionManager subscriptionManager) {
        super(subscriptionManager);
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

    void initTriggerRoutes() {
        if (!subscriptionManager.isNewSubscription(subscriptionSetup.getSubscriptionId())) {
            logger.info("Subscription is NOT new - flagging as already started if active {}", subscriptionSetup);
            hasBeenStarted = subscriptionManager.isActiveSubscription(subscriptionSetup.getSubscriptionId());
        }

        singletonFrom("quartz2://anshar/monitor_" + subscriptionSetup.getSubscriptionId() + "?fireNow=true&trigger.repeatInterval=" + 10000,
                "monitor_" + subscriptionSetup.getSubscriptionId())
                .choice()
                .when(p -> shouldBeStarted(p.getFromRouteId()))
                    .process(p -> hasBeenStarted = true)
                    .to("direct:" + subscriptionSetup.getStartSubscriptionRouteName()) // Start subscription
                .when(p -> shouldBeCancelled(p.getFromRouteId()))
                    .process(p -> hasBeenStarted = false)
                    .to("direct:" + subscriptionSetup.getCancelSubscriptionRouteName()) // Cancel
                .end()
        ;

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
