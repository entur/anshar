/*
 * Licensed under the EUPL, Version 1.2 or – as soon they will be approved by
 * the European Commission - subsequent versions of the EUPL (the "Licence");
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 *
 *   https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and
 * limitations under the Licence.
 */

package no.rutebanken.anshar.routes.outbound;

import no.rutebanken.anshar.metrics.PrometheusMetricsService;
import no.rutebanken.anshar.routes.dataformat.SiriDataFormatHelper;
import no.rutebanken.anshar.subscription.SiriDataType;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.LoggingLevel;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.Route;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.RouteDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import uk.org.siri.siri20.EstimatedTimetableDeliveryStructure;
import uk.org.siri.siri20.ServiceDelivery;
import uk.org.siri.siri20.Siri;
import uk.org.siri.siri20.SituationExchangeDeliveryStructure;
import uk.org.siri.siri20.VehicleMonitoringDeliveryStructure;

import javax.ws.rs.core.MediaType;
import java.net.ConnectException;
import java.net.SocketException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static no.rutebanken.anshar.routes.siri.transformer.SiriOutputTransformerRoute.OUTPUT_ADAPTERS_HEADER_NAME;

@Service
public class CamelRouteManager implements CamelContextAware {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private CamelContext camelContext;
    
    @Autowired
    private SiriHelper siriHelper;

    @Value("${anshar.default.max.elements.per.delivery:1000}")
    private int maximumSizePerDelivery;

    private final Map<OutboundSubscriptionSetup, SiriPushRouteBuilder> outboundRoutes = new HashMap<>();

    @Autowired
    private ServerSubscriptionManager subscriptionManager;

    @Autowired
    private PrometheusMetricsService metrics;

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

        // TODO: Use ProducerTemplate and headers instead of creating new routes for each subscription


        ExecutorService executorService = Executors.newSingleThreadExecutor();
        executorService.submit(() -> {
            try {

                Siri filteredPayload = SiriHelper.filterSiriPayload(payload, subscriptionRequest.getFilterMap());

                int deliverySize = this.maximumSizePerDelivery;
                if (subscriptionRequest.getDatasetId() != null) {
                    deliverySize = Integer.MAX_VALUE;
                }

                List<Siri> splitSiri = siriHelper.splitDeliveries(filteredPayload, deliverySize);

                if (splitSiri.size() > 1) {
                    logger.info("Object split into {} deliveries for subscription.", splitSiri.size(), subscriptionRequest);
                }

                SiriPushRouteBuilder siriPushRouteBuilder = new SiriPushRouteBuilder(consumerAddress, subscriptionRequest);
                Route route = addSiriPushRoute(siriPushRouteBuilder);

                for (Siri siri : splitSiri) {
                    executeSiriPushRoute(siri, route.getId());
                }
            } catch (Exception e) {
                logger.info("Failed to push data for subscription {}: {}", subscriptionRequest, e);

                if (e.getCause() instanceof SocketException) {
                    logger.info("Recipient is unreachable - ignoring");
                } else {
                    String msg = e.getMessage();
                    if (e.getCause() != null) {
                        msg = e.getCause().getMessage();
                    }
                    logger.info("Exception caught when pushing SIRI-data: {}", msg);
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
                return containsSXdata;
            }

            if (SiriHelper.containsValues(serviceDelivery.getVehicleMonitoringDeliveries())) {
                VehicleMonitoringDeliveryStructure deliveryStructure = serviceDelivery.getVehicleMonitoringDeliveries().get(0);
                boolean containsVMdata = deliveryStructure.getVehicleActivities() != null &&
                        SiriHelper.containsValues(deliveryStructure.getVehicleActivities());
                return containsVMdata;
            }

            if (SiriHelper.containsValues(serviceDelivery.getEstimatedTimetableDeliveries())) {
                EstimatedTimetableDeliveryStructure deliveryStructure = serviceDelivery.getEstimatedTimetableDeliveries().get(0);
                boolean containsETdata = (deliveryStructure.getEstimatedJourneyVersionFrames() != null &&
                        SiriHelper.containsValues(deliveryStructure.getEstimatedJourneyVersionFrames()) &&
                        SiriHelper.containsValues(deliveryStructure.getEstimatedJourneyVersionFrames().get(0).getEstimatedVehicleJourneies()));
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
                if (subscriptionRequest.getSubscriptionType() == SiriDataType.ESTIMATED_TIMETABLE){
                    definition = from(routeName)
                            .routeId(routeName)
                            .to("direct:siri.transform.output")
                            .marshal(SiriDataFormatHelper.getSiriJaxbDataformat())
                            .setHeader("asyncConsumer", simple("true"))
                            .to(ExchangePattern.InOnly, "direct:send.to.pubsub.topic.estimated_timetable")
                    ;
                }
            } else {
                definition = from(routeName)
                        .routeId(routeName)
                        .log(LoggingLevel.INFO, "POST data to " + subscriptionRequest)
                        .setHeader("SubscriptionId", constant(subscriptionRequest.getSubscriptionId()))
                        .setHeader("CamelHttpMethod", constant("POST"))
                        .setHeader(Exchange.CONTENT_TYPE, constant(MediaType.APPLICATION_XML))
                        .bean(metrics, "countOutgoingData(${body}, SUBSCRIBE)")
                        .setHeader(OUTPUT_ADAPTERS_HEADER_NAME, constant(subscriptionRequest.getValueAdapters()))
                        .to("direct:siri.transform.output")
                        .marshal(SiriDataFormatHelper.getSiriJaxbDataformat())
                        .to("log:push:" + getClass().getSimpleName() + "?showAll=true&multiline=true")
                        .to(remoteEndPoint + options)
                        .bean(subscriptionManager, "clearFailTracker(${header.SubscriptionId})")
                        .to("log:push-resp:" + getClass().getSimpleName() + "?showAll=true&multiline=true")
                        .log(LoggingLevel.INFO, "POST complete " + subscriptionRequest);
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
