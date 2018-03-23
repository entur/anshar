package no.rutebanken.anshar.routes.admin;

import no.rutebanken.anshar.config.AnsharConfiguration;
import no.rutebanken.anshar.data.collections.ExtendedHazelcastService;
import no.rutebanken.anshar.routes.health.HealthManager;
import no.rutebanken.anshar.routes.outbound.ServerSubscriptionManager;
import no.rutebanken.anshar.subscription.SubscriptionManager;
import org.apache.camel.builder.RouteBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Service;

@Service
@Configuration
public class AdministrationRoute extends RouteBuilder {
    private Logger logger = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private AnsharConfiguration configuration;

    @Autowired
    private ExtendedHazelcastService extendedHazelcastService;

    @Autowired
    private SubscriptionManager subscriptionManager;

    @Autowired
    private ServerSubscriptionManager serverSubscriptionManager;

    @Autowired
    private HealthManager healthManager;

    @Override
    public void configure() throws Exception {

        restConfiguration("jetty")
                .port(configuration.getInboundPort());

        rest("/anshar")
                .get("/stats").produces("text/html").to("direct:stats")
                .put("/stop").to("direct:stop")
                .put("/start").to("direct:start")
                .get("/subscriptions").produces("text/html").to("direct:subscriptions")
                .get("/clusterstats").produces("application/json").to("direct:clusterstats")
                .get("/validation").produces("application/json").to("direct:validation")
                .get("/unmapped").produces("text/html").to("direct:unmapped");

        //Return subscription status
        from("direct:stats")
                .bean(subscriptionManager, "buildStats")
                .to("freemarker:templates/stats.ftl")
                .routeId("admin.stats")
        ;

        //Stop subscription
        from("direct:stop")
                .bean(subscriptionManager, "stopSubscription(${header.subscriptionId})")
                .routeId("admin.stop")
        ;

        //Start subscription
        from("direct:start")
                .bean(subscriptionManager, "startSubscription(${header.subscriptionId})")
                .routeId("admin.start")
        ;

        //Return subscription status
        from("direct:subscriptions")
                .bean(serverSubscriptionManager, "getSubscriptionsAsJson")
                .to("freemarker:templates/subscriptions.ftl")
                .routeId("admin.subscriptions")
        ;

        //Return subscription status
        from("direct:clusterstats")
                .bean(extendedHazelcastService, "listNodes(${header.stats})")
                .routeId("admin.clusterstats")
        ;

        //Return subscription status
        from("direct:validation")
                .bean(healthManager, "getValidationResults(${header.subscriptionId})")
                .to("freemarker:templates/validation.ftl")
                .routeId("admin.validation")
        ;

        //Return subscription status
        from("direct:unmapped")
                .filter(header("datasetId").isNotNull())
                .bean(healthManager, "getUnmappedIdsAsJson(${header.datasetId})")
                .to("freemarker:templates/unmapped.ftl")
                .routeId("admin.unmapped")
        ;

    }
}
