package no.rutebanken.anshar.routes.outbound;

import no.rutebanken.anshar.metrics.PrometheusMetricsService;
import no.rutebanken.anshar.routes.dataformat.SiriDataFormatHelper;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.ws.rs.core.MediaType;

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
                .marshal(SiriDataFormatHelper.getSiriJaxbDataformat())
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
                .to("log:push-resp:" + getClass().getSimpleName() + "?showAll=true&multiline=true")
                .log(LoggingLevel.INFO, "POST complete ${header.SubscriptionId}");

    }
}
