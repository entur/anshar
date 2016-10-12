package no.rutebanken.anshar.routes.siri;

import no.rutebanken.anshar.subscription.RequestType;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import no.rutebanken.anshar.subscription.SubscriptionManager;
import no.rutebanken.anshar.subscription.SubscriptionSetup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.org.siri.siri20.Siri;
import uk.org.siri.siri20.SubscriptionResponseStructure;

public class Siri20ToSiriRS14Subscription extends SiriSubscriptionRouteBuilder {
    private Logger logger = LoggerFactory.getLogger(this.getClass());

    public Siri20ToSiriRS14Subscription(SubscriptionSetup subscriptionSetup) {
        this.subscriptionSetup = subscriptionSetup;
    }


    @Override
    public void configure() throws Exception {

        //Start subscription
        from("activemq:start" + uniqueRouteName + "?asyncConsumer=true")
                .bean(this, "marshalSiriSubscriptionRequest", false)
                .setExchangePattern(ExchangePattern.InOut) // Make sure we wait for a response
                .setHeader("SOAPAction", constant("Subscribe"))
                .setHeader("operatorNamespace", constant(subscriptionSetup.getOperatorNamespace())) // Need to make SOAP request with endpoint specific element namespace
                .to("xslt:xsl/siri_20_14.xsl") // Convert from SIRI 2.0 to SIRI 1.4
                .to("log:sent:" + getClass().getSimpleName() + "?showAll=true&multiline=true")
                .removeHeaders("CamelHttp*") // Remove any incoming HTTP headers as they interfere with the outgoing definition
                .setHeader(Exchange.CONTENT_TYPE, constant("text/xml;charset=UTF-8")) // Necessary when talking to Microsoft web services
                .setHeader(Exchange.HTTP_METHOD, constant(org.apache.camel.component.http4.HttpMethods.POST))
                .to("http4://" + subscriptionSetup.getUrlMap().get(RequestType.SUBSCRIBE))
                .to("xslt:xsl/siri_14_20.xsl?saxon=true&allowStAX=false") // Convert from v1.4 to 2.0
                .to("log:received:" + getClass().getSimpleName() + "?showAll=true&multiline=true")
                .process(p -> {
                    Siri siri = handleSiriResponse(p.getIn().getBody(String.class));
                    SubscriptionResponseStructure response = siri.getSubscriptionResponse();

                    handleSubscriptionResponse(response, p.getIn().getHeader("CamelHttpResponseCode", String.class));
                })
        ;

        //Cancel subscription
        from("activemq:cancel" + uniqueRouteName + "?asyncConsumer=true")
                .bean(this, "marshalSiriTerminateSubscriptionRequest", false)
                .setExchangePattern(ExchangePattern.InOut) // Make sure we wait for a response
                .setProperty(Exchange.LOG_DEBUG_BODY_STREAMS, constant("true"))
                .setHeader("SOAPAction", constant(RequestType.DELETE_SUBSCRIPTION)) // set SOAPAction Header (Microsoft requirement)
                .setHeader("operatorNamespace", constant(subscriptionSetup.getOperatorNamespace())) // Need to make SOAP request with endpoint specific element namespace
                .to("xslt:xsl/siri_20_14.xsl") // Convert from SIRI 2.0 to SIRI 1.4
                .to("log:sent:" + getClass().getSimpleName() + "?showAll=true&multiline=true")
                .removeHeaders("CamelHttp*") // Remove any incoming HTTP headers as they interfere with the outgoing definition
                .setHeader(Exchange.CONTENT_TYPE, constant("text/xml;charset=UTF-8")) // Necessary when talking to Microsoft web services
                .setHeader(Exchange.HTTP_METHOD, constant(org.apache.camel.component.http4.HttpMethods.POST))
                .to("http4://" + subscriptionSetup.getUrlMap().get(RequestType.DELETE_SUBSCRIPTION))
//                .to("log:raw:" + getClass().getSimpleName() + "?showAll=true&multiline=true")
//                .to("xslt:xsl/siri_14_20.xsl?saxon=true&allowStAX=false") // Convert from v1.4 to 2.0
                .to("log:received:" + getClass().getSimpleName() + "?showAll=true&multiline=true")
                .process(p -> {
                    String body = p.getIn().getBody(String.class);
                    logger.info("Response body [{}]", body);
                    if (body != null && !body.isEmpty()) {
                        handleSiriResponse(body);
                    }

                    SubscriptionManager.removeSubscription(subscriptionSetup.getSubscriptionId());
                })
        ;

        initShedulerRoute();
    }

}
