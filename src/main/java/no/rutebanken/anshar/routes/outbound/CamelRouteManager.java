package no.rutebanken.anshar.routes.outbound;

import no.rutebanken.anshar.routes.dataformat.SiriDataFormatHelper;
import no.rutebanken.anshar.routes.siri.transformer.SiriValueTransformer;
import org.apache.camel.*;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.RouteDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import uk.org.siri.siri20.*;

import java.net.ConnectException;
import java.net.SocketException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class CamelRouteManager implements CamelContextAware {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private CamelContext camelContext;
    
    @Autowired
    private SiriHelper siriHelper;

    @Value("${anshar.outbound.maximumSizePerDelivery:1000}")
    private int maximumSizePerDelivery;

    private final Map<OutboundSubscriptionSetup, SiriPushRouteBuilder> outboundRoutes = new HashMap<>();

    @Autowired
    private ServerSubscriptionManager subscriptionManager;

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
     */
    void pushSiriData(Siri payload, OutboundSubscriptionSetup subscriptionRequest) {
        String consumerAddress = subscriptionRequest.getAddress();
        if (consumerAddress == null) {
            logger.info("ConsumerAddress is null - ignoring data.");
            return;
        }

        ExecutorService executorService = Executors.newSingleThreadExecutor();
        executorService.submit(() -> {
            try {

                Siri filteredPayload = SiriHelper.filterSiriPayload(payload, subscriptionRequest.getFilterMap());

                // Use original/mapped ids based on subscription
                filteredPayload = SiriValueTransformer.transform(filteredPayload, subscriptionRequest.getValueAdapters());


                int deliverySize = this.maximumSizePerDelivery;
                if (subscriptionRequest.getDatasetId() != null) {
                    deliverySize = Integer.MAX_VALUE;
                }


                List<Siri> splitSiri = siriHelper.splitDeliveries(filteredPayload, deliverySize);

                logger.info("Object split into {} deliveries.", splitSiri.size());

                SiriPushRouteBuilder siriPushRouteBuilder = new SiriPushRouteBuilder(consumerAddress, subscriptionRequest);
                Route route = addSiriPushRoute(siriPushRouteBuilder);

                for (Siri siri : splitSiri) {
                    executeSiriPushRoute(siri, route.getId());
                }
            } catch (Exception e) {
                if (e.getCause() instanceof SocketException) {
                    logger.info("Recipient is unreachable - ignoring");
                } else {
                    String msg = e.getMessage();
                    if (e.getCause() != null) {
                        msg = e.getCause().getMessage();
                    }
                    logger.warn("Exception caught when pushing SIRI-data: {}", msg);
                }
                subscriptionManager.pushFailedForSubscription(subscriptionRequest.getSubscriptionId());
            } finally {
                executorService.shutdown();
            }

        });
    }

    private Route addSiriPushRoute(SiriPushRouteBuilder route) throws Exception {
        Route existingRoute = camelContext.getRoute(route.getRouteName());
        if (existingRoute == null) {
            camelContext.addRoutes(route);
            logger.trace("Route added - CamelContext now has {} routes", camelContext.getRoutes().size());
            existingRoute = camelContext.getRoute(route.getRouteName());
        }
        return existingRoute;
    }

    private void executeSiriPushRoute(Siri payload, String routeName) {
        if (!serviceDeliveryContainsData(payload)) {
            return;
        }
        ProducerTemplate template = camelContext.createProducerTemplate();
        template.sendBody(routeName, payload);
    }

    private boolean serviceDeliveryContainsData(Siri payload) {
        if (payload.getServiceDelivery() != null) {
            ServiceDelivery serviceDelivery = payload.getServiceDelivery();

            if (SiriHelper.containsValues(serviceDelivery.getSituationExchangeDeliveries())) {
                SituationExchangeDeliveryStructure deliveryStructure = serviceDelivery.getSituationExchangeDeliveries().get(0);
                boolean containsSXdata = deliveryStructure.getSituations() != null &&
                        SiriHelper.containsValues(deliveryStructure.getSituations().getPtSituationElements());
                logger.info("Contains SX-data: [{}]", containsSXdata);
                return containsSXdata;
            }

            if (SiriHelper.containsValues(serviceDelivery.getVehicleMonitoringDeliveries())) {
                VehicleMonitoringDeliveryStructure deliveryStructure = serviceDelivery.getVehicleMonitoringDeliveries().get(0);
                boolean containsVMdata = deliveryStructure.getVehicleActivities() != null &&
                        SiriHelper.containsValues(deliveryStructure.getVehicleActivities());
                logger.info("Contains VM-data: [{}]", containsVMdata);
                return containsVMdata;
            }

            if (SiriHelper.containsValues(serviceDelivery.getEstimatedTimetableDeliveries())) {
                EstimatedTimetableDeliveryStructure deliveryStructure = serviceDelivery.getEstimatedTimetableDeliveries().get(0);
                boolean containsETdata = (deliveryStructure.getEstimatedJourneyVersionFrames() != null &&
                        SiriHelper.containsValues(deliveryStructure.getEstimatedJourneyVersionFrames()) &&
                        SiriHelper.containsValues(deliveryStructure.getEstimatedJourneyVersionFrames().get(0).getEstimatedVehicleJourneies()));
                logger.info("Contains ET-data: [{}]", containsETdata);
                return containsETdata;
            }
        }
        return true;
    }

    private class SiriPushRouteBuilder extends RouteBuilder {

        private final OutboundSubscriptionSetup subscriptionRequest;
        private String remoteEndPoint;
        private RouteDefinition definition;
        private final String routeName;

        public SiriPushRouteBuilder(String remoteEndPoint, OutboundSubscriptionSetup subscriptionRequest) {
            this.remoteEndPoint=remoteEndPoint;
            this.subscriptionRequest = subscriptionRequest;
            routeName = String.format("direct:%s", subscriptionRequest.createRouteId());
        }

        @Override
        public void configure() throws Exception {

            boolean isActiveMQ = false;

            if (remoteEndPoint.startsWith("http://")) {
                //Translating URL to camel-format
                remoteEndPoint = "http4://" + remoteEndPoint.substring("http://".length());
            } else if (remoteEndPoint.startsWith("https://")) {
                //Translating URL to camel-format
                remoteEndPoint = "https4://" + remoteEndPoint.substring("https://".length());
            } else if (remoteEndPoint.startsWith("activemq:")) {
                isActiveMQ = true;
            }

            String options;
            if (isActiveMQ) {
                int timeout = subscriptionRequest.getTimeToLive();
                options = "?asyncConsumer=true&timeToLive=" + timeout;
            } else {
                int timeout = 60000;
                options = "?httpClient.socketTimeout=" + timeout + "&httpClient.connectTimeout=" + timeout;
                onException(ConnectException.class)
                        .maximumRedeliveries(0)
                        .log("Failed to connect to recipient");

                errorHandler(noErrorHandler());
            }

            if (isActiveMQ) {
                definition = from(routeName)
                        .routeId(routeName)
                        .marshal(SiriDataFormatHelper.getSiriJaxbDataformat())
                        .to(ExchangePattern.InOnly, remoteEndPoint + options)
                        .log(LoggingLevel.INFO, "Pushed data to ActiveMQ-topic: [" + remoteEndPoint + "]");
            } else {
                definition = from(routeName)
                        .routeId(routeName)
                        .log(LoggingLevel.INFO, "POST data to " + remoteEndPoint + " [" + subscriptionRequest.getSubscriptionId() + "]")
                        .setHeader("SubscriptionId", constant(subscriptionRequest.getSubscriptionId()))
                        .setHeader("CamelHttpMethod", constant("POST"))
                        .setHeader(Exchange.CONTENT_TYPE, constant("application/xml"))
                        .marshal(SiriDataFormatHelper.getSiriJaxbDataformat())
                        .to("log:push:" + getClass().getSimpleName() + "?showAll=true&multiline=true")
                        .to(remoteEndPoint + options)
                        .bean(subscriptionManager, "clearFailTracker(${header.SubscriptionId})")
                        .to("log:push-resp:" + getClass().getSimpleName() + "?showAll=true&multiline=true")
                        .log(LoggingLevel.INFO, "POST complete [" + subscriptionRequest.getSubscriptionId() + "]");
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
