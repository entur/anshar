package no.rutebanken.anshar.routes.admin;

import no.rutebanken.anshar.messages.collections.DistributedCollection;
import no.rutebanken.anshar.routes.outbound.ServerSubscriptionManager;
import no.rutebanken.anshar.subscription.SubscriptionManager;
import no.rutebanken.anshar.subscription.SubscriptionMonitor;
import no.rutebanken.anshar.subscription.SubscriptionSetup;
import org.apache.camel.builder.RouteBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import javax.servlet.http.HttpServletRequest;

@Configuration
public class AdministrationRoute extends RouteBuilder {
    private Logger logger = LoggerFactory.getLogger(this.getClass());

    @Value("${anshar.incoming.port}")
    private String inboundPort;

    @Autowired
    DistributedCollection distributedCollection;

    @Override
    public void configure() throws Exception {

        //Return subscription status
        from("jetty:http://0.0.0.0:" + inboundPort + "/anshar/stats")
                .process(p-> {
                    p.getOut().setBody(SubscriptionManager.buildStats());
                })
                .to("freemarker:templates/stats.ftl")
        ;

        //Restart subscription
        from("jetty:http://0.0.0.0:" + inboundPort + "/anshar/stop?httpMethodRestrict=PUT")
                .process(p -> {
                    HttpServletRequest request = p.getIn().getBody(HttpServletRequest.class);
                    String subscriptionId = request.getParameter("subscriptionId");
                    if (subscriptionId != null &&
                            !subscriptionId.isEmpty()) {

                        SubscriptionSetup subscriptionSetup = SubscriptionManager.getActiveSubscriptions().get(subscriptionId);
                        if (subscriptionSetup != null) {
                            subscriptionSetup.setActive(false);
                            SubscriptionManager.getActiveSubscriptions().put(subscriptionId, subscriptionSetup);

                            SubscriptionMonitor.cancelSubscription(subscriptionSetup);
                            logger.info("Handled request to cancel subscription ", subscriptionSetup);
                        }
                    }

                })
        ;
        //Restart subscription
        from("jetty:http://0.0.0.0:" + inboundPort + "/anshar/start?httpMethodRestrict=PUT")
                .process(p -> {
                    HttpServletRequest request = p.getIn().getBody(HttpServletRequest.class);
                    String subscriptionId = request.getParameter("subscriptionId");
                    if (subscriptionId != null &&
                            !subscriptionId.isEmpty()) {

                        SubscriptionSetup subscriptionSetup = SubscriptionManager.getPendingSubscriptions().get(subscriptionId);
                        if (subscriptionSetup != null) {

                            subscriptionSetup.setActive(true);
                            SubscriptionManager.addPendingSubscription(subscriptionId, subscriptionSetup);
                            logger.info("Handled request to start subscription ", subscriptionSetup);
                        }
                    }

                })
        ;

        //Return subscription status
        from("jetty:http://0.0.0.0:" + inboundPort + "/anshar/subscriptions")
                .process(p -> {
                    p.getOut().setBody(ServerSubscriptionManager.getSubscriptionsAsJson());
                })
                .to("freemarker:templates/subscriptions.ftl")
        ;
        //Return subscription status
        from("jetty:http://0.0.0.0:" + inboundPort + "/anshar/clusterstats")
                .process(p -> {
                    p.getOut().setBody(distributedCollection.listNodes());
                })
        ;

    }
}
