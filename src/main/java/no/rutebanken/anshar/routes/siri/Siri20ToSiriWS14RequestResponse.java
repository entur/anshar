package no.rutebanken.anshar.routes.siri;

import no.rutebanken.anshar.dataformat.SiriDataFormatHelper;
import no.rutebanken.anshar.routes.CamelConfiguration;
import no.rutebanken.anshar.subscription.SubscriptionManager;
import no.rutebanken.anshar.subscription.SubscriptionSetup;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;

import static no.rutebanken.anshar.routes.siri.SiriIncomingReceiver.TRANSFORM_SOAP;
import static no.rutebanken.anshar.routes.siri.SiriIncomingReceiver.TRANSFORM_VERSION;

public class Siri20ToSiriWS14RequestResponse extends SiriSubscriptionRouteBuilder {

    public Siri20ToSiriWS14RequestResponse(CamelConfiguration config, SubscriptionSetup subscriptionSetup, SubscriptionManager subscriptionManager) {
        super(config, subscriptionManager);

        this.subscriptionSetup = subscriptionSetup;
    }

    @Override
    public void configure() throws Exception {

        long heartbeatIntervalMillis = subscriptionSetup.getHeartbeatInterval().toMillis();

        SiriRequestFactory helper = new SiriRequestFactory(subscriptionSetup);

        String httpOptions = getTimeout();

        String monitoringRouteId = "monitor.ws.14." + subscriptionSetup.getSubscriptionType() + "." + subscriptionSetup.getVendor();
        boolean releaseLeadershipOnError;
        if (subscriptionSetup.getSubscriptionMode() == SubscriptionSetup.SubscriptionMode.REQUEST_RESPONSE |
                subscriptionSetup.getSubscriptionMode() == SubscriptionSetup.SubscriptionMode.POLLING_FETCHED_DELIVERY) {

            releaseLeadershipOnError = true;
            singletonFrom("quartz2://anshar/monitor_" + subscriptionSetup.getRequestResponseRouteName() + "?fireNow=true&trigger.repeatInterval=" + heartbeatIntervalMillis,
                    monitoringRouteId)
                    .choice()
                    .when(p -> requestData(subscriptionSetup.getSubscriptionId(), p.getFromRouteId()))
                    .to("direct:" + subscriptionSetup.getServiceRequestRouteName())
                    .endChoice()
            ;
        } else {
            releaseLeadershipOnError = false;
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
                .doTry()
                    .to(getRequestUrl(subscriptionSetup) + httpOptions)
                    .setHeader("CamelHttpPath", constant("/appContext" + subscriptionSetup.buildUrl(false)))
                    .log("Got response " + subscriptionSetup.toString())
                    //.to("log:response:" + getClass().getSimpleName() + "?showAll=true&multiline=true")
                    .setHeader(TRANSFORM_VERSION, constant(TRANSFORM_VERSION))
                    .setHeader(TRANSFORM_SOAP, constant(TRANSFORM_SOAP))
                    .to("activemq:queue:" + CamelConfiguration.TRANSFORM_QUEUE + "?disableReplyTo=true&timeToLive=" + getTimeToLive())
                .doCatch(Exception.class)
                    .log("Caught exception - releasing leadership: " + subscriptionSetup.toString())
                    .to("log:response:" + getClass().getSimpleName() + "?showCaughtException=true&showAll=true&multiline=true")
                    .process(p -> {
                        if (releaseLeadershipOnError) {
                            releaseLeadership(monitoringRouteId);
                        }
                    })
                .endDoTry()
                .routeId("request.ws.14." + subscriptionSetup.getSubscriptionType() + "." + subscriptionSetup.getVendor())
        ;

    }
}
