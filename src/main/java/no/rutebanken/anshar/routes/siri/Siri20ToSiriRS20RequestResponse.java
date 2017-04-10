package no.rutebanken.anshar.routes.siri;

import no.rutebanken.anshar.routes.BaseRouteBuilder;
import no.rutebanken.anshar.subscription.SubscriptionManager;
import no.rutebanken.anshar.subscription.SubscriptionSetup;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.component.http4.HttpMethods;
import org.rutebanken.siri20.util.SiriXml;
import uk.org.siri.siri20.Siri;

import java.time.ZonedDateTime;

public class Siri20ToSiriRS20RequestResponse extends BaseRouteBuilder {
    private final Siri request;
    private final SubscriptionSetup subscriptionSetup;

    public Siri20ToSiriRS20RequestResponse(SubscriptionSetup subscriptionSetup, SubscriptionManager subscriptionManager) {
        super(subscriptionManager);
        if (subscriptionSetup.getSubscriptionMode() == SubscriptionSetup.SubscriptionMode.FETCHED_DELIVERY) {
            this.request = SiriObjectFactory.createDataSupplyRequest(subscriptionSetup);
        } else {
            this.request = SiriObjectFactory.createServiceRequest(subscriptionSetup);
        }

        this.subscriptionSetup = subscriptionSetup;
    }

    @Override
    public void configure() throws Exception {
        String siriXml = SiriXml.toXml(request);

        long heartbeatIntervalMillis = subscriptionSetup.getHeartbeatInterval().toMillis();

        int timeout = (int) heartbeatIntervalMillis / 2;

        String httpOptions = "?httpClient.socketTimeout=" + timeout + "&httpClient.connectTimeout=" + timeout;

        singletonFrom("quartz2://monitor_" + subscriptionSetup.getRequestResponseRouteName() + "?fireNow=true&deleteJob=false&durableJob=true&recoverableJob=true&trigger.repeatInterval=" + heartbeatIntervalMillis)
                .choice()
                    .when(p -> requestData(subscriptionSetup.getSubscriptionId()))
                    .to("direct:" + subscriptionSetup.getServiceRequestRouteName())
                .endChoice()
        ;

        from("direct:" + subscriptionSetup.getServiceRequestRouteName())
                .log("Retrieving data " + subscriptionSetup.toString())
                .process(p -> {
                    System.out.println("Running " + ZonedDateTime.now());
                })
                .setBody(simple(siriXml))
                .setExchangePattern(ExchangePattern.InOut) // Make sure we wait for a response
                .setHeader("SOAPAction", simple(getSoapAction(subscriptionSetup))) // extract and compute SOAPAction (Microsoft requirement)
                .setHeader("operatorNamespace", constant(subscriptionSetup.getOperatorNamespace())) // Need to make SOAP request with endpoint specific element namespace
                .removeHeaders("CamelHttp*") // Remove any incoming HTTP headers as they interfere with the outgoing definition
                .setHeader(Exchange.CONTENT_TYPE, constant("text/xml;charset=UTF-8")) // Necessary when talking to Microsoft web services
                .setHeader(Exchange.HTTP_METHOD, constant(HttpMethods.POST))
                        // Header routing
                .to(getRequestUrl(subscriptionSetup) + httpOptions)
                .setHeader("CamelHttpPath", constant("/appContext" + subscriptionSetup.buildUrl(false)))
                .log("Got response " + subscriptionSetup.toString())
                .to("activemq:queue:" + SiriIncomingReceiver.TRANSFORM_QUEUE + "?disableReplyTo=true&timeToLive=" + timeout)
        ;
    }

}
