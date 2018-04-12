package no.rutebanken.anshar.routes.validation;

import no.rutebanken.anshar.config.AnsharConfiguration;
import no.rutebanken.anshar.subscription.SubscriptionManager;
import no.rutebanken.anshar.subscription.SubscriptionSetup;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Service;

@Service
@Configuration
public class ValidationRoute extends RouteBuilder {

    @Autowired
    private AnsharConfiguration configuration;

    @Autowired
    private SiriXmlValidator siriXmlValidator;

    @Autowired
    private SubscriptionManager subscriptionManager;


    @Override
    public void configure() throws Exception {

        restConfiguration("jetty")
                .port(configuration.getInboundPort());

        rest("/anshar/validation")
                .get("/{codespace}").produces("text/html").to("direct:validation.list")
                .get("/report").produces("text/html").to("direct:validation.report")
                .put("/toggle").produces("text/html").to("direct:validation.toggle")
                .get("/siri").produces("application/xml").to("direct:validation.siri")
        ;

        from("direct:validation.toggle")
                .filter(header("subscriptionId").isNotNull())
                .process(p -> toggleValidation((String) p.getIn().getHeader("subscriptionId")))
                .routeId("admin.validation.toggle")
        ;

        from("direct:validation.list")
                .bean(subscriptionManager, "getSubscriptionsForCodespace(${header.codespace})")
                .to("freemarker:templates/validation.ftl")
                .routeId("admin.validation.list")
        ;
        from("direct:validation.report")
                .bean(siriXmlValidator, "getValidationResults(${header.subscriptionId})")
                .to("freemarker:templates/validation-report.ftl")
                .routeId("admin.validation.report")
        ;

        from("direct:validation.siri")
                .bean(siriXmlValidator, "getValidatedSiri(${header.validationRef})")
                .setHeader("Content-Disposition", simple("attachment; filename=\"SIRI.xml\""))
                .routeId("admin.validation.siri")
        ;

    }

    private void toggleValidation(String subscriptionId) {
        SubscriptionSetup subscriptionSetup = subscriptionManager.get(subscriptionId);
        if (subscriptionSetup != null) {
            subscriptionSetup.setValidation(! subscriptionSetup.isValidation());
            if (subscriptionSetup.isValidation()) {
                //Validation has now been switched on - clear previous results
                siriXmlValidator.clearValidationResults(subscriptionId);
            }
            subscriptionManager.updateSubscription(subscriptionSetup);
        }
    }

}
