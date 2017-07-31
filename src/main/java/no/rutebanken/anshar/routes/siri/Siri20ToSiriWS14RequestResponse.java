package no.rutebanken.anshar.routes.siri;

import no.rutebanken.anshar.dataformat.SiriDataFormatHelper;
import no.rutebanken.anshar.routes.BaseRouteBuilder;
import no.rutebanken.anshar.subscription.SubscriptionManager;
import no.rutebanken.anshar.subscription.SubscriptionSetup;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;

public class Siri20ToSiriWS14RequestResponse extends BaseRouteBuilder {
    private final SubscriptionSetup subscriptionSetup;

    public Siri20ToSiriWS14RequestResponse(SubscriptionSetup subscriptionSetup, SubscriptionManager subscriptionManager) {
        super(subscriptionManager);

        this.subscriptionSetup = subscriptionSetup;
    }

    @Override
    public void configure() throws Exception {

        long heartbeatIntervalMillis = subscriptionSetup.getHeartbeatInterval().toMillis();

        int timeout = (int) heartbeatIntervalMillis / 2;

        SiriRequestFactory helper = new SiriRequestFactory(subscriptionSetup);

        String httpOptions = "?httpClient.socketTimeout=" + timeout + "&httpClient.connectTimeout=" + timeout;

        if (subscriptionSetup.getSubscriptionMode() == SubscriptionSetup.SubscriptionMode.REQUEST_RESPONSE) {
            singletonFrom("quartz2://anshar/monitor_" + subscriptionSetup.getRequestResponseRouteName() + "?fireNow=true&trigger.repeatInterval=" + heartbeatIntervalMillis,
                    "monitor_" + subscriptionSetup.getRequestResponseRouteName())
                    .choice()
                    .when(p -> requestData(subscriptionSetup.getSubscriptionId(), p.getFromRouteId()))
                    .to("direct:" + subscriptionSetup.getServiceRequestRouteName())
                    .endChoice()
            ;
        }

        from("direct:" + subscriptionSetup.getServiceRequestRouteName())
                .log("Retrieving data " + subscriptionSetup.toString())
                .bean(helper, "createSiriDataRequest", false)
                .marshal(SiriDataFormatHelper.getSiriJaxbDataformat())
                .setExchangePattern(ExchangePattern.InOut) // Make sure we wait for a response
                .setHeader("SOAPAction", simple(getSoapAction(subscriptionSetup))) // extract and compute SOAPAction (Microsoft requirement)
                .setHeader("operatorNamespace", constant(subscriptionSetup.getOperatorNamespace())) // Need to make SOAP request with endpoint specific element namespace
                .to("xslt:xsl/siri_20_14.xsl") // Convert SIRI raw request to SOAP version
                .to("xslt:xsl/siri_raw_soap.xsl") // Convert SIRI raw request to SOAP version
                .removeHeaders("CamelHttp*") // Remove any incoming HTTP headers as they interfere with the outgoing definition
                .setHeader(Exchange.CONTENT_TYPE, constant(subscriptionSetup.getContentType())) // Necessary when talking to Microsoft web services
                .setHeader(Exchange.HTTP_METHOD, constant(org.apache.camel.component.http4.HttpMethods.POST))
                .to("log:request:" + getClass().getSimpleName() + "?showAll=true&multiline=true")
                .to(getRequestUrl(subscriptionSetup) + httpOptions)
                .setHeader("CamelHttpPath", constant("/appContext" + subscriptionSetup.buildUrl(false)))
                .log("Got response " + subscriptionSetup.toString())
                //.to("log:response:" + getClass().getSimpleName() + "?showAll=true&multiline=true")
                .to("activemq:queue:" + SiriIncomingReceiver.TRANSFORM_QUEUE  + "?disableReplyTo=true&timeToLive="+timeout)
        ;
    }
}
