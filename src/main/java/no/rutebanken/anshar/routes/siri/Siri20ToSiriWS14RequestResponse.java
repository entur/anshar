package no.rutebanken.anshar.routes.siri;

import no.rutebanken.anshar.routes.ServiceNotSupportedException;
import no.rutebanken.anshar.routes.siri.handlers.SiriHandler;
import no.rutebanken.anshar.subscription.RequestType;
import no.rutebanken.anshar.subscription.SubscriptionManager;
import no.rutebanken.anshar.subscription.SubscriptionSetup;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.builder.xml.Namespaces;
import org.rutebanken.siri20.util.SiriXml;
import uk.org.siri.siri20.Siri;

import java.util.Map;

public class Siri20ToSiriWS14RequestResponse extends RouteBuilder {
    private final Siri request;
    private final SubscriptionSetup subscriptionSetup;

    private SiriHandler handler;

    public Siri20ToSiriWS14RequestResponse(SubscriptionSetup subscriptionSetup) {

        this.request = SiriObjectFactory.createServiceRequest(subscriptionSetup);

        this.subscriptionSetup = subscriptionSetup;
        handler = new SiriHandler();
    }

    @Override
    public void configure() throws Exception {
        if (!subscriptionSetup.isActive()) {
            return;
        }
        String siriXml = SiriXml.toXml(request);

        Map<String, String> urlMap = subscriptionSetup.getUrlMap();

        Namespaces ns = new Namespaces("siri", "http://www.siri.org.uk/siri")
                .add("xsd", "http://www.w3.org/2001/XMLSchema");

        SubscriptionManager.addSubscription(subscriptionSetup.getSubscriptionId(), subscriptionSetup);

        from("quartz2://request_response_" + subscriptionSetup.getSubscriptionId() + "?fireNow=true&trigger.repeatInterval=" + (subscriptionSetup.getHeartbeatInterval().getSeconds()*1000) )
                .setBody(simple(siriXml))
                .setExchangePattern(ExchangePattern.InOut) // Make sure we wait for a response
                .setHeader("SOAPAction", ns.xpath("concat('Get',substring-before(/siri:Siri/siri:ServiceRequest/*[@version]/local-name(),'Request'))", String.class)) // extract and compute SOAPAction (Microsoft requirement)
                .setHeader("operatorNamespace", constant(subscriptionSetup.getOperatorNamespace())) // Need to make SOAP request with endpoint specific element namespace.to("xslt:xsl/siri_20_14.xsl") // Convert from SIRI 2.0 to SIRI 1.4
                .to("xslt:xsl/siri_20_14.xsl") // Convert SIRI raw request to SOAP version
                .to("xslt:xsl/siri_raw_soap.xsl") // Convert SIRI raw request to SOAP version
                .to("log:sent converted:" + getClass().getSimpleName() + "?showAll=true&multiline=true")
                .removeHeaders("CamelHttp*") // Remove any incoming HTTP headers as they interfere with the outgoing definition
                .setHeader(Exchange.CONTENT_TYPE, constant("text/xml;charset=UTF-8")) // Necessary when talking to Microsoft web services
                .setHeader(Exchange.HTTP_METHOD, constant(org.apache.camel.component.http4.HttpMethods.POST))
                        //.to("http4://" + urlMap.get("GetSituationExchange"))

                        // Header routing
                .choice()
                .when(header("SOAPAction").isEqualTo(RequestType.GET_VEHICLE_MONITORING))
                .to("http4://" + urlMap.get(RequestType.GET_VEHICLE_MONITORING))
                .when(header("SOAPAction").isEqualTo(RequestType.GET_SITUATION_EXCHANGE))
                .to("http4://" + urlMap.get(RequestType.GET_SITUATION_EXCHANGE))
                .otherwise().throwException(new ServiceNotSupportedException())
                .end()
                .to("log:sent to siri server::" + getClass().getSimpleName() + "?showAll=true&multiline=true")
                .to("xslt:xsl/siri_soap_raw.xsl?saxon=true&allowStAX=false") // Extract SOAP version and convert to raw SIRI
                .to("xslt:xsl/siri_14_20.xsl?saxon=true&allowStAX=false") // Convert from v1.4 to 2.0
                .setHeader("CamelHttpPath", constant("/appContext" + subscriptionSetup.buildUrl(false)))
                .to("activemq:queue:" + SiriIncomingReceiver.TRANSFORM_QUEUE)
//                .process(p -> {
//                    String xml = p.getIn().getBody(String.class);
//                    handler.handleIncomingSiri(subscriptionSetup.getSubscriptionId(), xml);
//                })
        ;
    }
}
