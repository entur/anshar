package no.rutebanken.anshar.routes.health;

import no.rutebanken.anshar.routes.outbound.ServerSubscriptionManager;
import no.rutebanken.anshar.subscription.SubscriptionManager;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;


@Configuration
public class LivenessReadinessRoute extends RouteBuilder {
    private Logger logger = LoggerFactory.getLogger(this.getClass());

    @Value("${anshar.incoming.port}")
    private String inboundPort;

    @Value("${anshar.healthcheck.failure.count}")
    private int unhealthyCounter = 20;

    private Map<String, Integer> healthMap = new HashMap<>();

    @Override
    public void configure() throws Exception {

        //To avoid large stacktraces in the log when fetching data using browser
        from("jetty:http://0.0.0.0:" + inboundPort + "/favicon.ico")
                .setHeader(Exchange.HTTP_RESPONSE_CODE, constant("404"))
        ;

        // Application is ready to accept traffic
        from("jetty:http://0.0.0.0:" + inboundPort + "/ready")
                .setHeader(Exchange.HTTP_RESPONSE_CODE, constant("200"))
                .setBody(constant("OK"))
        ;

        // Application is (still) alive and well
        from("jetty:http://0.0.0.0:" + inboundPort + "/up")
                //TODO: On error - POST to hubot
                // Ex: wget --post-data='{"source":"otp", "message":"Downloaded file is empty or not present. This makes OTP fail! Please check logs"}' http://hubot/hubot/say/
                .choice()
                    .when(p -> isApplicationHealthy())
                        .setHeader(Exchange.HTTP_RESPONSE_CODE, constant("200"))
                        .setBody(constant("OK"))
                    .endChoice()
                    .otherwise()
                        .setHeader(Exchange.HTTP_RESPONSE_CODE, constant("500"))
                        .setBody(simple("Not OK"))
                    .end()
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

    private boolean isApplicationHealthy() {
        healthMap.keySet().forEach(key -> {
            if (SubscriptionManager.isSubscriptionHealthy(key)) {
                resetCounter(key);
            } else {
                incrementCounter(key);
            }
        });
        SubscriptionManager.getActiveSubscriptions().keySet().forEach(key -> {
            if (SubscriptionManager.isSubscriptionHealthy(key)) {
                resetCounter(key);
            } else {
                incrementCounter(key);
            }
        });
        SubscriptionManager.getPendingSubscriptions().keySet().forEach(key -> {
            if (SubscriptionManager.isSubscriptionHealthy(key)) {
                resetCounter(key);
            } else {
                incrementCounter(key);
            }
        });

        for (String subscriptionId : healthMap.keySet()) {
            int counter = healthMap.get(subscriptionId);
            if (counter > unhealthyCounter) {
                logger.warn("Subscription with id {} has been reported as unhealthy {} times - reporting server error.", subscriptionId, counter);
                return false;
            }
        }
        return true;
    }

    private void resetCounter(String key) {
        healthMap.put(key, 0);
    }

    private void incrementCounter(String key) {
        int count = healthMap.containsKey(key) ? healthMap.get(key) : 0;
        healthMap.put(key, count + 1);
    }

}
