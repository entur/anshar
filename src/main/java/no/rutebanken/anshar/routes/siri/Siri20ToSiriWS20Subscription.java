package no.rutebanken.anshar.routes.siri;

import static no.rutebanken.anshar.routes.siri.RouteHelper.getCamelUrl;

import java.io.InputStream;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.component.http4.HttpMethods;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.xml.bind.marshaller.NamespacePrefixMapper;

import no.rutebanken.anshar.routes.siri.handlers.SiriHandler;
import no.rutebanken.anshar.subscription.RequestType;
import no.rutebanken.anshar.subscription.SubscriptionManager;
import no.rutebanken.anshar.subscription.SubscriptionSetup;

public class Siri20ToSiriWS20Subscription extends SiriSubscriptionRouteBuilder {
    private Logger logger = LoggerFactory.getLogger(this.getClass());

    private SiriHandler handler;

    public Siri20ToSiriWS20Subscription(SiriHandler handler, SubscriptionSetup subscriptionSetup, SubscriptionManager subscriptionManager) {
        super(subscriptionManager);
        this.handler = handler;
        this.subscriptionSetup = subscriptionSetup;

        this.customNamespacePrefixMapper = new NamespacePrefixMapper() {
            @Override
            public String getPreferredPrefix(String arg0, String arg1, boolean arg2) {
                return "siri";
            }
        };
    }


    @Override
    public void configure() throws Exception {

        Map<RequestType, String> urlMap = subscriptionSetup.getUrlMap();

        RouteHelper helper = new RouteHelper(subscriptionSetup, customNamespacePrefixMapper);

        String endpointUrl = urlMap.get(RequestType.SUBSCRIBE);
        if (endpointUrl.startsWith("https4://")) {
            endpointUrl.replaceFirst("https4", "https");
        } else {
            endpointUrl = "http://" + endpointUrl;
        }

        //Start subscription
        from("direct:" + subscriptionSetup.getStartSubscriptionRouteName())
                .log("Starting subscription " + subscriptionSetup.toString())
                .bean(helper, "marshalSiriSubscriptionRequest", false)
                .setExchangePattern(ExchangePattern.InOut) // Make sure we wait for a response
                .setHeader("SOAPAction", constant("Subscribe"))
                .setHeader("operatorNamespace", constant(subscriptionSetup.getOperatorNamespace())) // Need to make SOAP request with endpoint specific element namespace
                .setHeader("endpointUrl", constant(endpointUrl)) // Need to make SOAP request with endpoint specific element namespace
                .setHeader("soapEnvelopeNamespace", constant(subscriptionSetup.getSoapenvNamespace())) // Need to make SOAP request with endpoint specific element namespace
                .to("xslt:xsl/siri_raw_soap.xsl") // Convert SIRI raw request to SOAP version
                .to("xslt:xsl/siri_14_20.xsl") // Convert SIRI raw request to SOAP version
                .removeHeaders("CamelHttp*") // Remove any incoming HTTP headers as they interfere with the outgoing definition
                .setHeader(Exchange.CONTENT_TYPE, constant(subscriptionSetup.getContentType())) // Necessary when talking to Microsoft web services
                .setHeader(Exchange.HTTP_METHOD, constant(HttpMethods.POST))
                .to("log:sent:" + getClass().getSimpleName() + "?showAll=true&multiline=true")
                .to(getCamelUrl(urlMap.get(RequestType.SUBSCRIBE)) + getTimeout())
                .choice().when(simple("${in.body} != null"))
                .to("log:received:" + getClass().getSimpleName() + "?showAll=true&multiline=true")
                .to("xslt:xsl/siri_soap_raw.xsl?saxon=true&allowStAX=false") // Extract SOAP version and convert to raw SIRI
                .end()
                .to("log:received:" + getClass().getSimpleName() + "?showAll=true&multiline=true")
                .process(p -> {
                    InputStream body = p.getIn().getBody(InputStream.class);
                    handler.handleIncomingSiri(subscriptionSetup.getSubscriptionId(), body);

                })
        ;

        //Cancel subscription
        from("direct:" + subscriptionSetup.getCancelSubscriptionRouteName())
                .log("Cancelling subscription " + subscriptionSetup.toString())
                .bean(helper, "marshalSiriTerminateSubscriptionRequest", false)
                .setExchangePattern(ExchangePattern.InOut) // Make sure we wait for a response
                .setProperty(Exchange.LOG_DEBUG_BODY_STREAMS, constant("true"))
                .setHeader("SOAPAction", constant("DeleteSubscription")) // set SOAPAction Header (Microsoft requirement)
                .setHeader("operatorNamespace", constant(subscriptionSetup.getOperatorNamespace())) // Need to make SOAP request with endpoint specific element namespace
                .setHeader("endpointUrl", constant(endpointUrl)) // Need to make SOAP request with endpoint specific element namespace
                .to("xslt:xsl/siri_raw_soap.xsl") // Convert SIRI raw request to SOAP version
                .to("xslt:xsl/siri_14_20.xsl") // Convert SIRI raw request to SOAP version
                .removeHeaders("CamelHttp*") // Remove any incoming HTTP headers as they interfere with the outgoing definition
                .setHeader(Exchange.CONTENT_TYPE, constant(subscriptionSetup.getContentType())) // Necessary when talking to Microsoft web services
                .setHeader(Exchange.HTTP_METHOD, constant(HttpMethods.POST))
                .to("log:sent:" + getClass().getSimpleName() + "?showAll=true&multiline=true")
                .to(getCamelUrl(urlMap.get(RequestType.DELETE_SUBSCRIPTION)) + getTimeout())
                .choice().when(simple("${in.body} != null"))
                .to("xslt:xsl/siri_soap_raw.xsl?saxon=true&allowStAX=false") // Extract SOAP version and convert to raw SIRI
                .end()
                .to("log:received:" + getClass().getSimpleName() + "?showAll=true&multiline=true")
                .process(p -> {
                    InputStream body = p.getIn().getBody(InputStream.class);
                    logger.info("Response body [{}]", body);
                    if (body != null && body.available() > 0) {
                        handler.handleIncomingSiri(subscriptionSetup.getSubscriptionId(), body);
                    }
                })
        ;

        initTriggerRoutes();
    }

}
