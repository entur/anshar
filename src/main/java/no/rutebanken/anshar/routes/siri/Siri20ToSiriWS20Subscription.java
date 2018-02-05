package no.rutebanken.anshar.routes.siri;

import com.sun.xml.bind.marshaller.NamespacePrefixMapper;
import no.rutebanken.anshar.config.AnsharConfiguration;
import no.rutebanken.anshar.routes.dataformat.SiriDataFormatHelper;
import no.rutebanken.anshar.routes.siri.handlers.SiriHandler;
import no.rutebanken.anshar.routes.siri.helpers.SiriRequestFactory;
import no.rutebanken.anshar.subscription.SubscriptionManager;
import no.rutebanken.anshar.subscription.SubscriptionSetup;
import no.rutebanken.anshar.subscription.helpers.RequestType;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.component.http4.HttpMethods;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.Map;

import static no.rutebanken.anshar.routes.siri.helpers.SiriRequestFactory.getCamelUrl;

public class Siri20ToSiriWS20Subscription extends SiriSubscriptionRouteBuilder {
    private Logger logger = LoggerFactory.getLogger(this.getClass());

    private SiriHandler handler;

    public Siri20ToSiriWS20Subscription(AnsharConfiguration config, SiriHandler handler, SubscriptionSetup subscriptionSetup, SubscriptionManager subscriptionManager) {
        super(config, subscriptionManager);
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
        SiriRequestFactory helper = new SiriRequestFactory(subscriptionSetup);

        String endpointUrl = urlMap.get(RequestType.SUBSCRIBE);
        if (endpointUrl.startsWith("https4://")) {
            endpointUrl = endpointUrl.replaceFirst("https4", "https");
        } else {
            endpointUrl = "http://" + endpointUrl;
        }

        //Start subscription
        from("direct:" + subscriptionSetup.getStartSubscriptionRouteName())
                .log("Starting subscription " + subscriptionSetup.toString())
                .bean(helper, "createSiriSubscriptionRequest", false)
                .marshal(SiriDataFormatHelper.getSiriJaxbDataformat(customNamespacePrefixMapper))
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
                .to(getCamelUrl(urlMap.get(RequestType.SUBSCRIBE), getTimeout()))
                .choice().when(simple("${in.body} != null"))
                    .to("log:received:" + getClass().getSimpleName() + "?showAll=true&multiline=true")
                    .to("xslt:xsl/siri_soap_raw.xsl?saxon=true&allowStAX=false&resultHandlerFactory=#streamResultHandlerFactory") // Extract SOAP version and convert to raw SIRI
                .end()
                .to("log:received:" + getClass().getSimpleName() + "?showAll=true&multiline=true")
                .process(p -> {
                    InputStream body = p.getIn().getBody(InputStream.class);
                    handler.handleIncomingSiri(subscriptionSetup.getSubscriptionId(), body);
                })
                .choice()
                .when(p -> subscriptionSetup.isDataSupplyRequestForInitialDelivery())
                    .log("Requesting DataSupplyRequest " + subscriptionSetup)
                    .to("seda:"+subscriptionSetup.getServiceRequestRouteName())
                .end()
                .routeId("start.ws.20.subscription."+subscriptionSetup.getVendor())
        ;

        //Check status-request checks the server status - NOT the subscription
        from("direct:" + subscriptionSetup.getCheckStatusRouteName())
                .bean(helper, "createSiriCheckStatusRequest", false)
                .marshal(SiriDataFormatHelper.getSiriJaxbDataformat(customNamespacePrefixMapper))
                .setExchangePattern(ExchangePattern.InOut) // Make sure we wait for a response
                .setHeader("SOAPAction", constant("CheckStatus"))
                .setHeader("operatorNamespace", constant(subscriptionSetup.getOperatorNamespace())) // Need to make SOAP request with endpoint specific element namespace
                .setHeader("endpointUrl", constant(endpointUrl)) // Need to make SOAP request with endpoint specific element namespace
                .setHeader("soapEnvelopeNamespace", constant(subscriptionSetup.getSoapenvNamespace())) // Need to make SOAP request with endpoint specific element namespace
                .to("xslt:xsl/siri_raw_soap.xsl") // Convert SIRI raw request to SOAP version
                .removeHeaders("CamelHttp*") // Remove any incoming HTTP headers as they interfere with the outgoing definition
                .setHeader(Exchange.CONTENT_TYPE, constant(subscriptionSetup.getContentType())) // Necessary when talking to Microsoft web services
                .setHeader(Exchange.HTTP_METHOD, constant(org.apache.camel.http.common.HttpMethods.POST))
                .to("log:cs:" + getClass().getSimpleName() + "?showAll=true&multiline=true")
                .to(getCamelUrl(urlMap.get(RequestType.CHECK_STATUS), getTimeout()))
                .choice().when(simple("${in.body} != null"))
                    .to("xslt:xsl/siri_soap_raw.xsl?saxon=true&allowStAX=false&resultHandlerFactory=#streamResultHandlerFactory") // Extract SOAP version and convert to raw SIRI
                .end()
                .process(p -> {

                    String responseCode = p.getIn().getHeader("CamelHttpResponseCode", String.class);
                    if ("200" .equals(responseCode)) {
                        InputStream body = p.getIn().getBody(InputStream.class);
                        if (body != null && body.available() > 0) {
                            handler.handleIncomingSiri(subscriptionSetup.getSubscriptionId(), body);
                        }
                    } else {
                        logger.info("CheckStatus NOT OK - Remote service is down [{}]", subscriptionSetup.buildUrl());
                    }

                })
                .routeId("check.status.rs.20.subscription."+subscriptionSetup.getVendor())
        ;

        //Cancel subscription
        from("direct:" + subscriptionSetup.getCancelSubscriptionRouteName())
                .log("Cancelling subscription " + subscriptionSetup.toString())
                .bean(helper, "createSiriTerminateSubscriptionRequest", false)
                .marshal(SiriDataFormatHelper.getSiriJaxbDataformat(customNamespacePrefixMapper))
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
                .to(getCamelUrl(urlMap.get(RequestType.DELETE_SUBSCRIPTION), getTimeout()))
                .choice().when(simple("${in.body} != null"))
                    .to("xslt:xsl/siri_soap_raw.xsl?saxon=true&allowStAX=false&resultHandlerFactory=#streamResultHandlerFactory") // Extract SOAP version and convert to raw SIRI
                .end()
                .to("log:received:" + getClass().getSimpleName() + "?showAll=true&multiline=true")
                .process(p -> {
                    InputStream body = p.getIn().getBody(InputStream.class);
                    logger.info("Response body [{}]", body);
                    if (body != null && body.available() > 0) {
                        handler.handleIncomingSiri(subscriptionSetup.getSubscriptionId(), body);
                    }
                })
                .routeId("cancel.ws.20.subscription."+subscriptionSetup.getVendor())
        ;

        initTriggerRoutes();
    }

}
