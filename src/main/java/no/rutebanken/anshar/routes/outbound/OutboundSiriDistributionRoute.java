package no.rutebanken.anshar.routes.outbound;

import no.rutebanken.anshar.metrics.PrometheusMetricsService;
import no.rutebanken.anshar.routes.dataformat.SiriDataFormatHelper;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.entur.siri.validator.SiriValidator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.org.siri.siri21.Siri;

import javax.ws.rs.core.MediaType;

import static no.rutebanken.anshar.routes.HttpParameter.SIRI_VERSION_HEADER_NAME;
import static no.rutebanken.anshar.routes.RestRouteBuilder.downgradeSiriVersion;

@Service
public class OutboundSiriDistributionRoute extends RouteBuilder {

    @Autowired
    private ServerSubscriptionManager subscriptionManager;

    @Autowired
    private PrometheusMetricsService metrics;

    @Override
    public void configure() {

        int timeout = 15000;

        onException(Exception.class)
            .maximumRedeliveries(2)
            .redeliveryDelay(3000) //milliseconds
            .logRetryAttempted(true)
            .log("Retry triggered")
        ;

        from("direct:send.to.external.subscription")
                .routeId("send.to.external.subscription")
                .log(LoggingLevel.INFO, "POST data to ${header.SubscriptionId}")
                .setHeader("CamelHttpMethod", constant("POST"))
                .setHeader(Exchange.CONTENT_TYPE, constant(MediaType.APPLICATION_XML))
                .bean(metrics, "countOutgoingData(${body}, SUBSCRIBE)")
                .to("direct:siri.transform.data")
                .choice()
                    .when(header(SIRI_VERSION_HEADER_NAME).isEqualTo(SiriValidator.Version.VERSION_2_1))
                        .marshal(SiriDataFormatHelper.getSiriJaxbDataformat(SiriValidator.Version.VERSION_2_1))
                    .endChoice()
                    .otherwise()
                        .process(p -> {
                            p.getMessage().setBody(downgradeSiriVersion(p.getIn().getBody(Siri.class)));
                        })
                        .marshal(SiriDataFormatHelper.getSiriJaxbDataformat(SiriValidator.Version.VERSION_2_0))
                .end()
                .setHeader("httpClient.socketTimeout", constant(timeout))
                .setHeader("httpClient.connectTimeout", constant(timeout))
                .choice()
                .when(header("showBody").isEqualTo(true))
                        .to("log:push:" + getClass().getSimpleName() + "?showAll=true&multiline=true")
                .endChoice()
                    .otherwise()
                        .to("log:push:" + getClass().getSimpleName() + "?showAll=false&showExchangeId=true&showHeaders=true&showException=true&multiline=true&showBody=false")
                .end()
                .removeHeader("showBody")
                .toD("${header.endpoint}")
                .bean(subscriptionManager, "clearFailTracker(${header.SubscriptionId})")
                .log(LoggingLevel.INFO, "POST complete ${header.SubscriptionId} - Response: [${header.CamelHttpResponseCode} ${header.CamelHttpResponseText}]");

    }
}
