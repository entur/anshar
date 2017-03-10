package no.rutebanken.anshar.routes.siri;

import no.rutebanken.anshar.routes.ServiceNotSupportedException;
import no.rutebanken.anshar.subscription.RequestType;
import no.rutebanken.anshar.subscription.SubscriptionSetup;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.builder.xml.Namespaces;
import org.apache.camel.component.http4.HttpMethods;
import org.rutebanken.siri20.util.SiriXml;
import uk.org.siri.siri20.Siri;

import java.util.Map;

import static no.rutebanken.anshar.routes.siri.RouteHelper.getCamelUrl;

public class Siri20ToSiriRS20RequestResponse extends RouteBuilder {
    private final Siri request;
    private final SubscriptionSetup subscriptionSetup;

    public Siri20ToSiriRS20RequestResponse(SubscriptionSetup subscriptionSetup) {

        this.request = SiriObjectFactory.createServiceRequest(subscriptionSetup);

        this.subscriptionSetup = subscriptionSetup;
    }

    @Override
    public void configure() throws Exception {
        String siriXml = SiriXml.toXml(request);

        Map<RequestType, String> urlMap = subscriptionSetup.getUrlMap();

        Namespaces ns = new Namespaces("siri", "http://www.siri.org.uk/siri")
                .add("xsd", "http://www.w3.org/2001/XMLSchema");

        long heartbeatIntervalMillis = subscriptionSetup.getHeartbeatInterval().toMillis();

        int timeout = (int) heartbeatIntervalMillis / 2;

        String httpOptions = "?httpClient.socketTimeout=" + timeout + "&httpClient.connectTimeout=" + timeout;

        from("quartz2://" + subscriptionSetup.getRequestResponseRouteName() + "?fireNow=true&deleteJob=false&durableJob=true&recoverableJob=true&trigger.repeatInterval=" + heartbeatIntervalMillis)
                .routeId(subscriptionSetup.getRequestResponseRouteName())
                .autoStartup(false)
                .log("Retrieving data " + subscriptionSetup.toString())
                .setBody(simple(siriXml))
                .setExchangePattern(ExchangePattern.InOut) // Make sure we wait for a response
                .setHeader("SOAPAction", ns.xpath("concat('Get',substring-before(/siri:Siri/siri:ServiceRequest/*[@version]/local-name(),'Request'))", String.class)) // extract and compute SOAPAction (Microsoft requirement)
                .setHeader("operatorNamespace", constant(subscriptionSetup.getOperatorNamespace())) // Need to make SOAP request with endpoint specific element namespace
                .removeHeaders("CamelHttp*") // Remove any incoming HTTP headers as they interfere with the outgoing definition
                .setHeader(Exchange.CONTENT_TYPE, constant("text/xml;charset=UTF-8")) // Necessary when talking to Microsoft web services
                .setHeader(Exchange.HTTP_METHOD, constant(HttpMethods.POST))
                        // Header routing
                .choice()
                .when(header("SOAPAction").isEqualTo("GetVehicleMonitoring"))
                .to(getCamelUrl(urlMap.get(RequestType.GET_VEHICLE_MONITORING)) + httpOptions)
                .when(header("SOAPAction").isEqualTo("GetSituationExchange"))
                .to(getCamelUrl(urlMap.get(RequestType.GET_SITUATION_EXCHANGE)) + httpOptions)
                .when(header("SOAPAction").isEqualTo("GetEstimatedTimetable"))
                .to(getCamelUrl(urlMap.get(RequestType.GET_ESTIMATED_TIMETABLE)) + httpOptions)
                .otherwise()
                .throwException(new ServiceNotSupportedException())
                .end()
                .setHeader("CamelHttpPath", constant("/appContext" + subscriptionSetup.buildUrl(false)))
                .log("Got response " + subscriptionSetup.toString())
                .to("activemq:queue:" + SiriIncomingReceiver.TRANSFORM_QUEUE + "?disableReplyTo=true")
        ;
    }


}
