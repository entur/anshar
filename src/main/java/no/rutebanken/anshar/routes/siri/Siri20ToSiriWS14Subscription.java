package no.rutebanken.anshar.routes.siri;

import com.sun.xml.bind.marshaller.NamespacePrefixMapper;
import no.rutebanken.anshar.subscription.RequestType;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import no.rutebanken.anshar.subscription.SubscriptionManager;
import no.rutebanken.anshar.subscription.SubscriptionSetup;
import org.apache.camel.component.http4.HttpMethods;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.org.siri.siri20.Siri;
import uk.org.siri.siri20.SubscriptionResponseStructure;

import javax.xml.bind.PropertyException;
import java.util.Map;

public class Siri20ToSiriWS14Subscription extends SiriSubscriptionRouteBuilder {
    private Logger logger = LoggerFactory.getLogger(this.getClass());

    public Siri20ToSiriWS14Subscription(SubscriptionSetup subscriptionSetup) {

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

        Map<String, String> urlMap = subscriptionSetup.getUrlMap();

        //Start subscription
        from("direct:start" + uniqueRouteName)
                .bean(this, "marshalSiriSubscriptionRequest", false)
                .setExchangePattern(ExchangePattern.InOut) // Make sure we wait for a response
                .setHeader("SOAPAction", constant(RequestType.SUBSCRIBE))
                .setHeader("operatorNamespace", constant(subscriptionSetup.getOperatorNamespace())) // Need to make SOAP request with endpoint specific element namespace
                .to("xslt:xsl/siri_20_14.xsl") // Convert from SIRI 2.0 to SIRI 1.4
                .to("xslt:xsl/siri_raw_soap.xsl") // Convert SIRI raw request to SOAP version
                .to("log:sent:" + getClass().getSimpleName() + "?showAll=true&multiline=true")
                .removeHeaders("CamelHttp*") // Remove any incoming HTTP headers as they interfere with the outgoing definition
                .setHeader(Exchange.CONTENT_TYPE, constant("text/xml;charset=UTF-8")) // Necessary when talking to Microsoft web services
                .setHeader(Exchange.HTTP_METHOD, constant(HttpMethods.POST))
                .to("http4://" + urlMap.get(RequestType.SUBSCRIBE))

                .to("xslt:xsl/siri_soap_raw.xsl?saxon=true&allowStAX=false") // Extract SOAP version and convert to raw SIRI
                .to("xslt:xsl/siri_14_20.xsl?saxon=true&allowStAX=false") // Convert from v1.4 to 2.0
                .to("log:received:" + getClass().getSimpleName() + "?showAll=true&multiline=true")
                .process(p -> {
                    Siri siri = handleSiriResponse(p.getIn().getBody(String.class));
                    SubscriptionResponseStructure response = siri.getSubscriptionResponse();

                    handleSubscriptionResponse(response, p.getIn().getHeader("CamelHttpResponseCode", String.class));

                })
        ;

        //Cancel subscription
        from("direct:cancel" + uniqueRouteName)
                .bean(this, "marshalSiriTerminateSubscriptionRequest", false)
                .setExchangePattern(ExchangePattern.InOut) // Make sure we wait for a response
                .setProperty(Exchange.LOG_DEBUG_BODY_STREAMS, constant("true"))
                .setHeader("SOAPAction", constant("DeleteSubscription")) // set SOAPAction Header (Microsoft requirement)
                .setHeader("operatorNamespace", constant(subscriptionSetup.getOperatorNamespace())) // Need to make SOAP request with endpoint specific element namespace
                .to("xslt:xsl/siri_20_14.xsl") // Convert from SIRI 2.0 to SIRI 1.4
                .to("xslt:xsl/siri_raw_soap.xsl") // Convert SIRI raw request to SOAP version
                .to("log:sent:" + getClass().getSimpleName() + "?showAll=true&multiline=true")
                .removeHeaders("CamelHttp*") // Remove any incoming HTTP headers as they interfere with the outgoing definition
                .setHeader(Exchange.CONTENT_TYPE, constant("text/xml;charset=UTF-8")) // Necessary when talking to Microsoft web services
                .setHeader(Exchange.HTTP_METHOD, constant(org.apache.camel.component.http4.HttpMethods.POST))
                .to("http4://" + urlMap.get(RequestType.DELETE_SUBSCRIPTION))
                .to("xslt:xsl/siri_soap_raw.xsl?saxon=true&allowStAX=false") // Extract SOAP version and convert to raw SIRI
                .to("xslt:xsl/siri_14_20.xsl?saxon=true&allowStAX=false") // Convert from v1.4 to 2.0
                .to("log:received:" + getClass().getSimpleName() + "?showAll=true&multiline=true")
                .process(p -> {
                    handleSiriResponse(p.getIn().getBody(String.class));
                    SubscriptionManager.removeSubscription(subscriptionSetup.getSubscriptionId());
                })
        ;

        initShedulerRoute();

    }

}
