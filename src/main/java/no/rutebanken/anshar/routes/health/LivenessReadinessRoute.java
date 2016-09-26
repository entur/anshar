package no.rutebanken.anshar.routes.health;

import com.hazelcast.core.HazelcastInstanceNotActiveException;
import no.rutebanken.anshar.messages.DistributedCollection;
import no.rutebanken.anshar.routes.outbound.ServerSubscriptionManager;
import no.rutebanken.anshar.subscription.SubscriptionManager;
import no.rutebanken.anshar.subscription.SubscriptionSetup;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.util.Map;
import java.util.Set;


@Configuration
public class LivenessReadinessRoute extends RouteBuilder {
    private Logger logger = LoggerFactory.getLogger(this.getClass());

    @Value("${anshar.incoming.port}")
    private String inboundPort;

    @Override
    public void configure() throws Exception {

        //To avoid large stacktraces in the log when fetching data using browser
        from("jetty:http://0.0.0.0:" + inboundPort + "/favicon.ico")
                .setHeader(Exchange.HTTP_RESPONSE_CODE, constant("404"))
        ;

        // Alive and ready
        from("jetty:http://0.0.0.0:" + inboundPort + "/ready")
                .setHeader(Exchange.HTTP_RESPONSE_CODE, constant("200"))
                .setBody(constant("OK"))
        ;
        from("jetty:http://0.0.0.0:" + inboundPort + "/up")
                .process(p -> {
                    boolean healthy = true;
                    try {
                        Map<String, SubscriptionSetup> activeSubscriptionsMap = DistributedCollection.getActiveSubscriptionsMap();
                        Set<String> keys = activeSubscriptionsMap.keySet();
                    } catch (HazelcastInstanceNotActiveException hz) {
                        healthy = false;
                    }
                    if (healthy) {
                        p.getOut().setHeader(Exchange.HTTP_RESPONSE_CODE, constant("200"));
                        p.getOut().setBody(constant("OK"));
                    } else {
                        p.getOut().setHeader(Exchange.HTTP_RESPONSE_CODE, constant("500"));
                        p.getOut().setBody(constant("Hazelcast is down - caused by OutOfMemoryError?"));
                    }
                })
        ;

        //Return subscription status
        from("jetty:http://0.0.0.0:" + inboundPort + "/anshar/stats")
                .process(p-> {
                    p.getOut().setBody(SubscriptionManager.buildStats());
                })
        ;

        //Return subscription status
        from("jetty:http://0.0.0.0:" + inboundPort + "/anshar/subscriptions")
                .process(p -> {
                    p.getOut().setBody(new ServerSubscriptionManager().getSubscriptionsAsJson());
                })
        ;

    }

}
