package no.rutebanken.anshar.routes.siri;

import no.rutebanken.anshar.dataformat.SiriDataFormatHelper;
import no.rutebanken.anshar.routes.CamelConfiguration;
import no.rutebanken.anshar.routes.siri.handlers.SiriHandler;
import no.rutebanken.anshar.subscription.RequestType;
import no.rutebanken.anshar.subscription.SubscriptionManager;
import no.rutebanken.anshar.subscription.SubscriptionSetup;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.component.http4.HttpMethods;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.Map;

import static no.rutebanken.anshar.routes.siri.SiriRequestFactory.getCamelUrl;

public class Siri20ToSiriRS14Subscription extends SiriSubscriptionRouteBuilder {
    private Logger logger = LoggerFactory.getLogger(this.getClass());

    private SiriHandler handler;

    public Siri20ToSiriRS14Subscription(CamelConfiguration config, SiriHandler handler, SubscriptionSetup subscriptionSetup, SubscriptionManager subscriptionManager) {
        super(config, subscriptionManager);
        this.handler = handler;
        this.subscriptionSetup = subscriptionSetup;
    }


    @Override
    public void configure() throws Exception {
    	
    	
        Map<RequestType, String> urlMap = subscriptionSetup.getUrlMap();

        SiriRequestFactory helper = new SiriRequestFactory(subscriptionSetup);

        //Start subscription
        from("direct:" + subscriptionSetup.getStartSubscriptionRouteName())
                .log("Starting subscription " + subscriptionSetup.toString())
                .bean(helper, "createSiriSubscriptionRequest", false)
                .marshal(SiriDataFormatHelper.getSiriJaxbDataformat())
                .setExchangePattern(ExchangePattern.InOut) // Make sure we wait for a response
                .setHeader("SOAPAction", constant("Subscribe"))
                .setHeader("operatorNamespace", constant(subscriptionSetup.getOperatorNamespace())) // Need to make SOAP request with endpoint specific element namespace
                .to("xslt:xsl/siri_20_14.xsl") // Convert from SIRI 2.0 to SIRI 1.4
                .removeHeaders("CamelHttp*") // Remove any incoming HTTP headers as they interfere with the outgoing definition
                .setHeader(Exchange.CONTENT_TYPE, constant(subscriptionSetup.getContentType())) // Necessary when talking to Microsoft web services
                .setHeader(Exchange.HTTP_METHOD, constant(HttpMethods.POST))
                .to("log:sent:" + getClass().getSimpleName() + "?showAll=true&multiline=true")
                .to(getCamelUrl(urlMap.get(RequestType.SUBSCRIBE)) + getTimeout())
                .to("log:received:" + getClass().getSimpleName() + "?showAll=true&multiline=true")
                .process(p -> {

                    String responseCode = p.getIn().getHeader("CamelHttpResponseCode", String.class);
                    if ("200".equals(responseCode)) {
                        logger.info("SubscriptionResponse OK {}", subscriptionSetup);
                    }

                    InputStream body = p.getIn().getBody(InputStream.class);

                    if (body != null && body.available() > 0) {
                        handler.handleIncomingSiri(subscriptionSetup.getSubscriptionId(), body);
                    }
                })
                .routeId("start.rs.14.subscription."+subscriptionSetup.getVendor())
        ;

        //Cancel subscription
        from("direct:" + subscriptionSetup.getCancelSubscriptionRouteName())
                .log("Cancelling subscription " + subscriptionSetup.toString())
                .bean(helper, "createSiriTerminateSubscriptionRequest", false)
                .marshal(SiriDataFormatHelper.getSiriJaxbDataformat())
                .setExchangePattern(ExchangePattern.InOut) // Make sure we wait for a response
                .setProperty(Exchange.LOG_DEBUG_BODY_STREAMS, constant("true"))
                .setHeader("SOAPAction", constant("DeleteSubscription")) // set SOAPAction Header (Microsoft requirement)
                .setHeader("operatorNamespace", constant(subscriptionSetup.getOperatorNamespace())) // Need to make SOAP request with endpoint specific element namespace
                .to("xslt:xsl/siri_20_14.xsl") // Convert from SIRI 2.0 to SIRI 1.4
                .to("log:sent:" + getClass().getSimpleName() + "?showAll=true&multiline=true")
                .removeHeaders("CamelHttp*") // Remove any incoming HTTP headers as they interfere with the outgoing definition
                .setHeader(Exchange.CONTENT_TYPE, constant(subscriptionSetup.getContentType())) // Necessary when talking to Microsoft web services
                .setHeader(Exchange.HTTP_METHOD, constant(org.apache.camel.component.http4.HttpMethods.POST))
                .to(getCamelUrl(urlMap.get(RequestType.DELETE_SUBSCRIPTION)) + getTimeout())
                .to("log:received:" + getClass().getSimpleName() + "?showAll=true&multiline=true")
                .process(p -> {
                    InputStream body = p.getIn().getBody(InputStream.class);
                    logger.info("Response body [{}]", body);
                    if (body != null && body.available() > 0) {
                        handler.handleIncomingSiri(subscriptionSetup.getSubscriptionId(), body);
                    }
                })
                .routeId("cancel.rs.14.subscription."+subscriptionSetup.getVendor())
        ;
        initTriggerRoutes();
    }

}
