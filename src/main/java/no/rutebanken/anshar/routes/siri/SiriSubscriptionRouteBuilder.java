package no.rutebanken.anshar.routes.siri;

import com.sun.xml.bind.marshaller.NamespacePrefixMapper;
import no.rutebanken.anshar.routes.BaseRouteBuilder;
import no.rutebanken.anshar.subscription.SubscriptionManager;
import no.rutebanken.anshar.subscription.SubscriptionSetup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class SiriSubscriptionRouteBuilder extends BaseRouteBuilder {

    private Logger logger = LoggerFactory.getLogger(this.getClass());

    NamespacePrefixMapper customNamespacePrefixMapper;

    SubscriptionSetup subscriptionSetup;
    private boolean hasBeenStarted;

    public SiriSubscriptionRouteBuilder(SubscriptionManager subscriptionManager) {
        super(subscriptionManager);
    }

    String getTimeout() {
        long heartbeatIntervalMillis = subscriptionSetup.getHeartbeatInterval().toMillis();
        int timeout = (int) heartbeatIntervalMillis / 2;

        return "?httpClient.socketTimeout=" + timeout + "&httpClient.connectTimeout=" + timeout;
    }

    void initTriggerRoutes() {

        singletonFrom("quartz2://monitor_" + subscriptionSetup.getSubscriptionId() + "?fireNow=true&deleteJob=false&durableJob=true&recoverableJob=true&trigger.repeatInterval=" + 5000)
                .choice()
                .when(p -> shouldBeStarted())
                    .to("direct:" + subscriptionSetup.getStartSubscriptionRouteName()) // Start subscription
                    .process(p -> hasBeenStarted = true)
                .when(p -> shouldBeCancelled())
                    .to("direct:" + subscriptionSetup.getCancelSubscriptionRouteName()) // Cancel
                    .process(p -> hasBeenStarted = false)
                .end()
        ;

    }

    private boolean shouldBeStarted() {
        boolean isActive = subscriptionManager.get(subscriptionSetup.getSubscriptionId()).isActive();

        boolean shouldBeStarted = (isActive & !hasBeenStarted);
        return shouldBeStarted;
    }
    private boolean shouldBeCancelled() {
        boolean isActive = subscriptionManager.get(subscriptionSetup.getSubscriptionId()).isActive();
        boolean isHealthy = subscriptionManager.isSubscriptionHealthy(subscriptionSetup.getSubscriptionId());

        boolean shouldBeCancelled = (hasBeenStarted & !isActive) | (hasBeenStarted & isActive & !isHealthy);

        return shouldBeCancelled;
    }
}
