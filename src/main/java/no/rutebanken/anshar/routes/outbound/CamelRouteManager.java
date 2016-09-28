package no.rutebanken.anshar.routes.outbound;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.Route;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.RouteDefinition;
import org.rutebanken.siri20.util.SiriXml;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import uk.org.siri.siri20.Siri;
import uk.org.siri.siri20.SubscriptionRequest;

import javax.xml.bind.JAXBException;
import java.net.SocketException;
import java.util.UUID;

import static no.rutebanken.anshar.routes.outbound.SiriHelper.getFilter;

@Service
public class CamelRouteManager implements CamelContextAware {
    private Logger logger = LoggerFactory.getLogger(this.getClass());

    protected static CamelContext camelContext;

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }


    /**
     * Creates a new ad-hoc route that sends the SIRI payload to supplied address, executes it, and finally terminates and removes it.
     * @param payload
     * @param subscriptionRequest
     * @param soapRequest
     */
    public void pushSiriData(Siri payload, SubscriptionRequest subscriptionRequest, boolean soapRequest) {
        String consumerAddress = subscriptionRequest.getConsumerAddress();
        if (consumerAddress == null) {
            logger.info("ConsumerAddress is null - ignoring data.");
            return;
        }

        Siri filteredPayload = SiriHelper.filterSiriPayload(payload, getFilter(subscriptionRequest));

        Thread r = new Thread() {
            @Override
            public void run() {
                try {

                    SiriPushRouteBuilder siriPushRouteBuilder = new SiriPushRouteBuilder(consumerAddress, soapRequest);
                    addSiriPushRoute(siriPushRouteBuilder);
                    executeSiriPushRoute(filteredPayload, siriPushRouteBuilder.getRouteName());
                    stopAndRemoveSiriPushRoute(siriPushRouteBuilder);
                } catch (Exception e) {
                    if (e.getCause() instanceof SocketException) {
                        logger.info("Recipient is unreachable - ignoring");
                    } else {
                        logger.warn("Exception caught when pushing SIRI-data", e);
                    }
                }
            }
        };
        r.start();
    }

    private void addSiriPushRoute(SiriPushRouteBuilder route) throws Exception {
        camelContext.addRouteDefinition(route.getDefinition());
        logger.info("Route added - CamelContext now has {} routes, {} definitions", camelContext.getRoutes().size(), camelContext.getRouteDefinitions().size());
    }

    private void stopAndRemoveSiriPushRoute(SiriPushRouteBuilder route) throws Exception {
        camelContext.removeRouteDefinition(route.getDefinition());
        logger.info("Route removed - CamelContext now has {} routes, {} definitions", camelContext.getRoutes().size(), camelContext.getRouteDefinitions().size());
    }


    private void executeSiriPushRoute(Siri payload, String routeName) throws JAXBException {
        String xml = SiriXml.toXml(payload);

        ProducerTemplate template = camelContext.createProducerTemplate();
        template.sendBody(routeName, xml);
    }
    private class SiriPushRouteBuilder extends RouteBuilder {

        private final boolean soapRequest;
        private String remoteEndPoint;
        private RouteDefinition definition;
        private String routeName;

        public SiriPushRouteBuilder(String remoteEndPoint, boolean soapRequest) {
            this.remoteEndPoint=remoteEndPoint;
            this.soapRequest = soapRequest;
        }

        @Override
        public void configure() throws Exception {

            if (remoteEndPoint.startsWith("http://")) {
                //Translating URL to camel-format
                remoteEndPoint = remoteEndPoint.substring("http://".length());
            }

            routeName = String.format("direct:%s", UUID.randomUUID().toString());

            errorHandler(
                    deadLetterChannel("activemq:queue:error")
            );

            from("activemq:queue:error")
                    .log("Request to endpoint failed: " + remoteEndPoint);

            if (soapRequest) {
                definition = from(routeName)
                        .routeId(remoteEndPoint)
                        .to("xslt:xsl/siri_raw_soap.xsl") // Convert SIRI raw request to SOAP version
                        .setHeader("CamelHttpMethod", constant("POST"))
                        .marshal().string("UTF-8")
                        .to("http4://" + remoteEndPoint);
            } else {
                definition = from(routeName)
                        .routeId(remoteEndPoint)
                        .setHeader("CamelHttpMethod", constant("POST"))
                        .marshal().string("UTF-8")
                        .to("http4://" + remoteEndPoint);
            }

        }

        public RouteDefinition getDefinition() {
            return definition;
        }

        public String getRouteName() {
            return routeName;
        }
    }
}
