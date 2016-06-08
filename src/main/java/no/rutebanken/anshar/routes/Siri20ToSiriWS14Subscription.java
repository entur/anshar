package no.rutebanken.anshar.routes;

import com.sun.xml.bind.marshaller.NamespacePrefixMapper;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import no.rutebanken.anshar.subscription.SubscriptionManager;
import no.rutebanken.anshar.subscription.SubscriptionSetup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.org.siri.siri20.Siri;
import uk.org.siri.siri20.SubscriptionResponseStructure;
import uk.org.siri.siri20.TerminateSubscriptionResponseStructure;

import javax.xml.bind.PropertyException;
import java.util.Map;

public class Siri20ToSiriWS14Subscription extends SiriSubscriptionRouteBuilder {
    private Logger logger = LoggerFactory.getLogger(this.getClass());

    public Siri20ToSiriWS14Subscription(SubscriptionSetup subscriptionSetup) {

        this.subscriptionSetup = subscriptionSetup;

        try {
            jaxbMarshaller.setProperty("com.sun.xml.bind.namespacePrefixMapper", new NamespacePrefixMapper() {
                @Override
                public String getPreferredPrefix(String arg0, String arg1, boolean arg2) {
                    return "siri";
                }
            });
        } catch (PropertyException e) {
            e.printStackTrace();
        }


    }


    @Override
    public void configure() throws Exception {

        Map<String, String> urlMap = subscriptionSetup.getUrlMap();

        //Start subscription
        from("direct:start" + uniqueRouteName)
                .setBody(simple(marshalSiriSubscriptionRequest()))
                .setExchangePattern(ExchangePattern.InOut) // Make sure we wait for a response
                .setHeader("SOAPAction", constant("Subscribe"))
                .setHeader("operatorNamespace", constant(subscriptionSetup.getOperatorNamespace())) // Need to make SOAP request with endpoint specific element namespace
                .to("xslt:xsl/siri_20_14.xsl") // Convert from SIRI 2.0 to SIRI 1.4
                .to("xslt:xsl/siri_raw_soap.xsl") // Convert SIRI raw request to SOAP version
                .to("log:sent:" + getClass().getSimpleName() + "?showAll=true&multiline=true")
                .removeHeaders("CamelHttp*") // Remove any incoming HTTP headers as they interfere with the outgoing definition
                .setHeader(Exchange.CONTENT_TYPE, constant("text/xml;charset=UTF-8")) // Necessary when talking to Microsoft web services
                .setHeader(Exchange.HTTP_METHOD, constant(org.apache.camel.component.http4.HttpMethods.POST))
                .to("http4://" + urlMap.get("Subscribe"))

                .to("xslt:xsl/siri_soap_raw.xsl?saxon=true") // Extract SOAP version and convert to raw SIRI
                .to("xslt:xsl/siri_14_20.xsl?saxon=true") // Convert from v1.4 to 2.0
                .to("log:received:" + getClass().getSimpleName() + "?showAll=true&multiline=true")
                .process(p -> {
                    Siri siri = handleSiriResponse(p.getIn().getBody(String.class));
                    SubscriptionResponseStructure response = siri.getSubscriptionResponse();

                    if (response.getResponseStatuses().isEmpty()) {
                        String responseCode = p.getIn().getHeader("CamelHttpResponseCode", String.class);
                        if ("200".equals(responseCode)) {
                            SubscriptionManager.addSubscription(subscriptionSetup.getSubscriptionId(), subscriptionSetup);
                        }
                    } else {
                        response.getResponseStatuses().forEach(s -> {
                            if (s.isStatus() != null && s.isStatus()) {
                                SubscriptionManager.addSubscription(s.getSubscriptionRef().getValue(), subscriptionSetup);
                            } else if (s.getErrorCondition() != null) {
                                logger.error("Error starting subscription:  {}", s.getErrorCondition().getDescription());
                            } else {
                                SubscriptionManager.addSubscription(s.getSubscriptionRef().getValue(), subscriptionSetup);
                            }
                        });
                    }
                })
        ;

        //Cancel subscription
        from("direct:cancel" + uniqueRouteName)
                .setBody(simple(marshalSiriTerminateSubscriptionRequest()))
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
                .to("http4://" + urlMap.get("DeleteSubscription"))
                .to("xslt:xsl/siri_soap_raw.xsl?saxon=true") // Extract SOAP version and convert to raw SIRI
                .to("xslt:xsl/siri_14_20.xsl?saxon=true") // Convert from v1.4 to 2.0
                .to("log:received:" + getClass().getSimpleName() + "?showAll=true&multiline=true")
                .process(p -> {
                    Siri siri = handleSiriResponse(p.getIn().getBody(String.class));
                })
        ;

        initShedulerRoute();

    }

}
