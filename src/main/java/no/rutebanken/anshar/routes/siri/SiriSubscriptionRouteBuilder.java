package no.rutebanken.anshar.routes.siri;

import com.sun.xml.bind.marshaller.NamespacePrefixMapper;
import no.rutebanken.anshar.subscription.SubscriptionSetup;
import org.apache.camel.builder.RouteBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class SiriSubscriptionRouteBuilder extends RouteBuilder {

    private Logger logger = LoggerFactory.getLogger(this.getClass());

    NamespacePrefixMapper customNamespacePrefixMapper;

    SubscriptionSetup subscriptionSetup;

    String getTimeout() {
        long heartbeatIntervalMillis = subscriptionSetup.getHeartbeatInterval().toMillis();
        int timeout = (int) heartbeatIntervalMillis / 2;

        return "?httpClient.socketTimeout=" + timeout + "&httpClient.connectTimeout=" + timeout;
    }

    void initTriggerRoutes() {
        from("direct:" + subscriptionSetup.getStartSubscriptionRouteName())
                .routeId(subscriptionSetup.getStartSubscriptionRouteName())
                .log("Triggering start of " + subscriptionSetup)
                .to("direct:delayedStart" + subscriptionSetup.getSubscriptionId());

        from("direct:" + subscriptionSetup.getCancelSubscriptionRouteName())
                .routeId(subscriptionSetup.getCancelSubscriptionRouteName())
                .log("Triggering cancel of " + subscriptionSetup)
                .to("direct:delayedCancel"+subscriptionSetup.getSubscriptionId());
    }
}
