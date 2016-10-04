package no.rutebanken.anshar.routes.outbound;

import org.apache.camel.*;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.model.TryDefinition;
import org.rutebanken.siri20.util.SiriXml;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import uk.org.siri.siri20.Siri;
import uk.org.siri.siri20.SubscriptionRequest;

import javax.servlet.http.HttpServletResponse;
import javax.xml.bind.JAXBException;
import java.io.IOException;
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
                    String routeId = addSiriPushRoute(siriPushRouteBuilder);
                    executeSiriPushRoute(filteredPayload, siriPushRouteBuilder.getRouteName());
                    stopAndRemoveSiriPushRoute(routeId);
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

    private String addSiriPushRoute(SiriPushRouteBuilder route) throws Exception {
        camelContext.addRoutes(route);
        logger.info("Route added - CamelContext now has {} routes", camelContext.getRoutes().size());
        return route.getDefinition().getId();
    }

    private boolean stopAndRemoveSiriPushRoute(String routeId) throws Exception {
        RouteDefinition routeDefinition = camelContext.getRouteDefinition(routeId);
        camelContext.removeRouteDefinition(routeDefinition);
        logger.info("Route removed - CamelContext now has {} routes", camelContext.getRoutes().size());
        return true;
    }


    private void executeSiriPushRoute(Siri payload, String routeName) throws JAXBException {

        ProducerTemplate template = camelContext.createProducerTemplate();
        template.sendBody(routeName, payload);
    }
    private class SiriPushRouteBuilder extends RouteBuilder {

        private final boolean soapRequest;
        private String remoteEndPoint;
        private TryDefinition definition;
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

            if (soapRequest) {
                definition = from(routeName)
                        .log(LoggingLevel.INFO, "POST data (SOAP) to " + remoteEndPoint)
                        .doTry()
                                .to("xslt:xsl/siri_raw_soap.xsl") // Convert SIRI raw request to SOAP version
                                .setHeader("CamelHttpMethod", constant("POST"))
                                .process(p -> {
                                    Siri payload = p.getIn().getBody(Siri.class);

                                    HttpServletResponse out = p.getOut().getBody(HttpServletResponse.class);

                                    SiriXml.toXml(payload, null, out.getOutputStream());
                                })
                                .marshal().string("UTF-8")
                                .to("http4://" + remoteEndPoint)
                        .doCatch(IOException.class)
                                .log("Error sending data to url " + remoteEndPoint)
                        .endDoTry()
                        ;
            } else {
                definition = from(routeName)
                        .log(LoggingLevel.INFO, "POST data to " + remoteEndPoint)
                        .doTry()
                            .setHeader("CamelHttpMethod", constant("POST"))
                            .process(p -> {
                                Siri payload = p.getIn().getBody(Siri.class);

                                HttpServletResponse out = p.getOut().getBody(HttpServletResponse.class);

                                SiriXml.toXml(payload, null, out.getOutputStream());
                            })
                            .marshal().string("UTF-8")
                            .to("http4://" + remoteEndPoint)
                        .doCatch(IOException.class)
                            .log("Error sending data to url " + remoteEndPoint)
                        .endDoTry()
                ;
            }

        }

        public TryDefinition getDefinition() {
            return definition;
        }

        public String getRouteName() {
            return routeName;
        }
    }
}
