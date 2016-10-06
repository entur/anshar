package no.rutebanken.anshar.routes.outbound;

import org.apache.camel.*;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.RouteDefinition;
import org.rutebanken.siri20.util.SiriXml;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import uk.org.siri.siri20.*;

import javax.xml.bind.JAXBException;
import java.net.SocketException;
import java.util.List;
import java.util.UUID;

import static no.rutebanken.anshar.routes.outbound.SiriHelper.containsValues;
import static no.rutebanken.anshar.routes.outbound.SiriHelper.getFilter;
import static no.rutebanken.anshar.routes.outbound.SiriHelper.splitDeliveries;

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
     * @param isSoapRequest
     */
    public void pushSiriData(Siri payload, SubscriptionRequest subscriptionRequest, boolean isSoapRequest) {
        String consumerAddress = subscriptionRequest.getConsumerAddress();
        if (consumerAddress == null) {
            logger.info("ConsumerAddress is null - ignoring data.");
            return;
        }

        Siri filteredPayload = SiriHelper.filterSiriPayload(payload, getFilter(subscriptionRequest));

        List<Siri> splitSiri = splitDeliveries(filteredPayload, 1000);

        for (Siri siri : splitSiri) {
            Thread r = new Thread() {
                @Override
                public void run() {
                    try {

                        SiriPushRouteBuilder siriPushRouteBuilder = new SiriPushRouteBuilder(consumerAddress, isSoapRequest);
                        String routeId = addSiriPushRoute(siriPushRouteBuilder);
                        executeSiriPushRoute(siri, siriPushRouteBuilder.getRouteName());
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
        if (!serviceDeliveryContainsData(payload)) {
            return;
        }
        String xml = SiriXml.toXml(payload);

        ProducerTemplate template = camelContext.createProducerTemplate();
        template.sendBody(routeName, xml);
    }

    private boolean serviceDeliveryContainsData(Siri payload) {
        if (payload.getServiceDelivery() != null) {
            ServiceDelivery serviceDelivery = payload.getServiceDelivery();
            if (containsValues(serviceDelivery.getSituationExchangeDeliveries())) {
                //SX-delivery - verify that there are situations in payload
                SituationExchangeDeliveryStructure deliveryStructure = serviceDelivery.getSituationExchangeDeliveries().get(0);
                return (deliveryStructure.getSituations() != null &&
                        containsValues(deliveryStructure.getSituations().getPtSituationElements()));
            }
            if (containsValues(serviceDelivery.getVehicleMonitoringDeliveries())) {
                //SX-delivery - verify that there are situations in payload
                VehicleMonitoringDeliveryStructure deliveryStructure = serviceDelivery.getVehicleMonitoringDeliveries().get(0);
                return (deliveryStructure.getVehicleActivities() != null &&
                        containsValues(deliveryStructure.getVehicleActivities()));
            }
            if (containsValues(serviceDelivery.getSituationExchangeDeliveries())) {
                //SX-delivery - verify that there are situations in payload
                SituationExchangeDeliveryStructure deliveryStructure = serviceDelivery.getSituationExchangeDeliveries().get(0);
                return (deliveryStructure.getSituations() != null &&
                        containsValues(deliveryStructure.getSituations().getPtSituationElements()));
            }
        }
        return true;
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
                    .routeId("errorhandler:"+remoteEndPoint) //One errorhandler per subscription/endpoint
                    .log("Error sending data to url " + remoteEndPoint);


            if (soapRequest) {
                definition = from(routeName)
                        .log(LoggingLevel.INFO, "POST data (SOAP) to " + remoteEndPoint)
                        .to("xslt:xsl/siri_raw_soap.xsl") // Convert SIRI raw request to SOAP version
                        .setHeader("CamelHttpMethod", constant("POST"))
                        .marshal().string("UTF-8")
                        .to("http4://" + remoteEndPoint);
            } else {
                definition = from(routeName)
                        .log(LoggingLevel.INFO, "POST data to " + remoteEndPoint)
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
