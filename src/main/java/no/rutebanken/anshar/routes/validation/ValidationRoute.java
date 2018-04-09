package no.rutebanken.anshar.routes.validation;

import no.rutebanken.anshar.config.AnsharConfiguration;
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

    @Override
    public void configure() throws Exception {

        restConfiguration("jetty")
                .port(configuration.getInboundPort());

        rest("/anshar/validation")
                .get("/").produces("text/html").to("direct:validation")
                .get("/siri").produces("application/xml").to("direct:validation-siri")
        ;
        from("direct:validation")
                .bean(siriXmlValidator, "getValidationResults(${header.subscriptionId})")
                .to("freemarker:templates/validation.ftl")
                .routeId("admin.validation")
        ;

        from("direct:validation-siri")
                .bean(siriXmlValidator, "getValidatedSiri(${header.validationRef})")
                .setHeader("Content-Disposition", simple("attachment; filename=\"SIRI.xml\""))
                .routeId("admin.validation.siri")
        ;

    }


}
