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

package no.rutebanken.anshar.routes.siri;

import no.rutebanken.anshar.config.AnsharConfiguration;
import no.rutebanken.anshar.routes.CamelRouteNames;
import no.rutebanken.anshar.routes.dataformat.SiriDataFormatHelper;
import no.rutebanken.anshar.routes.siri.handlers.SiriHandler;
import no.rutebanken.anshar.subscription.SubscriptionManager;
import no.rutebanken.anshar.subscription.SubscriptionSetup;
import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.builder.xml.Namespaces;
import org.rutebanken.validator.SiriValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Service;
import uk.org.siri.siri20.Siri;

import javax.xml.bind.UnmarshalException;
import java.io.InputStream;
import java.net.ConnectException;
import java.util.HashMap;
import java.util.Map;

@Service
@Configuration
public class Siri20RequestHandlerRoute extends RouteBuilder {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private SubscriptionManager subscriptionManager;

    @Autowired
    private SiriHandler handler;

    @Autowired
    private AnsharConfiguration configuration;

    public static final String TRANSFORM_VERSION = "TRANSFORM_VERSION";
    public static final String TRANSFORM_SOAP = "TRANSFORM_SOAP";

    @Override
    public void configure() throws Exception {

        onException(ConnectException.class)
                .maximumRedeliveries(10)
                .redeliveryDelay(10000)
                .useExponentialBackOff();

        onException(UnmarshalException.class, InvalidPayloadException.class)
                .handled(true)
                .setHeader(Exchange.HTTP_RESPONSE_CODE, constant("400"))
                .setBody(simple("Invalid XML"))
        ;


        errorHandler(loggingErrorHandler()
                        .log(logger)
                        .level(LoggingLevel.INFO)
        );

        Namespaces ns = new Namespaces("siri", "http://www.siri.org.uk/siri")
                .add("xsd", "http://www.w3.org/2001/XMLSchema");

        String activeMQParameters = "?disableReplyTo=true&timeToLive="+ configuration.getTimeToLive();
        String activeMqConsumerParameters = "?asyncConsumer=true&concurrentConsumers="+ configuration.getConcurrentConsumers();

        //Incoming notifications/deliveries
        from("jetty:http://0.0.0.0:" + configuration.getInboundPort() + "?matchOnUriPrefix=true&httpMethodRestrict=POST")
                .to("log:received:" + getClass().getSimpleName() + "?showAll=true&multiline=true")
                .choice()
                    .when(header("CamelHttpPath").contains("/services")) //Handle synchronous response
                        .process(p -> {
                            p.getOut().setHeaders(p.getIn().getHeaders());

                            String path = (String) p.getIn().getHeader("CamelHttpPath");
                            String datasetId = null;

                            String pathPattern = "/services/";
                            if (path.contains(pathPattern)) {
                                            //e.g. "/anshar/services/akt" resolves "akt"
                                datasetId = path.substring(path.indexOf(pathPattern) + pathPattern.length());
                            }

                            int maxSize = -1;
                            if (p.getIn().getHeaders().containsKey("maxSize")) {
                                maxSize = Integer.parseInt((String) p.getIn().getHeader("maxSize"));
                            }

                            Siri response = handler.handleIncomingSiri(null, p.getIn().getBody(InputStream.class), datasetId, SiriHandler.getIdMappingPolicy((String) p.getIn().getHeader("useOriginalId")), maxSize);
                            if (response != null) {
                                logger.info("Found ServiceRequest-response, streaming response");
                                p.getOut().setBody(response);
                            }
                        })
                        .marshal(SiriDataFormatHelper.getSiriJaxbDataformat())
                    .endChoice()
                    .when(header("CamelHttpPath").contains("/subscribe")) //Handle synchronous SubscriptionResponse
                        .process(p -> {
                            String path = p.getIn().getHeader("CamelHttpPath", String.class);

                            String subscriptionId = getSubscriptionIdFromPath(path);
                            String datasetId = null;

                            //e.g. "/anshar/subscribe/akt" resolves "akt"
                            String pathPattern = "/subscribe/";
                            if (path.contains(pathPattern)) {
                                datasetId = path.substring(path.indexOf(pathPattern) + pathPattern.length());
                            }

                            InputStream xml = p.getIn().getBody(InputStream.class);

                            Siri response = handler.handleIncomingSiri(subscriptionId, xml, datasetId, SiriHandler.getIdMappingPolicy((String) p.getIn().getHeader("useOriginalId")), -1);
                            if (response != null) {
                                logger.info("Returning SubscriptionResponse");

                                p.getOut().setBody(response);
                            }

                        })
                        .marshal(SiriDataFormatHelper.getSiriJaxbDataformat())
                .endChoice()
                    .otherwise()  //Handle asynchronous response
                        .choice()
                            .when(p -> {
                                    String subscriptionId = getSubscriptionIdFromPath(p.getIn().getHeader("CamelHttpPath", String.class));
                                    if (subscriptionId == null || subscriptionId.isEmpty()) {
                                        return false;
                                    }
                                    SubscriptionSetup subscriptionSetup = subscriptionManager.get(subscriptionId);

                                    if (subscriptionSetup == null) {
                                        return false;
                                    }

                                    boolean existsAndIsActive = (subscriptionManager.isSubscriptionRegistered(subscriptionId) &&
                                                subscriptionSetup.isActive());

                                    p.getOut().setHeaders(p.getIn().getHeaders());

                                    if (! "2.0".equals(subscriptionSetup.getVersion())) {
                                        p.getOut().setHeader(TRANSFORM_VERSION, TRANSFORM_VERSION);
                                    }

                                    if (subscriptionSetup.getServiceType() == SubscriptionSetup.ServiceType.SOAP) {
                                        p.getOut().setHeader(TRANSFORM_SOAP, TRANSFORM_SOAP);
                                    }

                                    return existsAndIsActive;
                                })
                                    //Valid subscription
                                .to("activemq:queue:" + CamelRouteNames.TRANSFORM_QUEUE + activeMQParameters)
                                .setHeader(Exchange.HTTP_RESPONSE_CODE, constant("200"))
                                .setBody(constant(null))
                            .endChoice()
                        .otherwise()
                                // Invalid subscription
                            .log("Ignoring incoming delivery for invalid subscription")
                            .setHeader(Exchange.HTTP_RESPONSE_CODE, constant("403")) //403 Forbidden
                            .setBody(constant("Subscription is not valid"))
                        .endChoice()
                .end()
                .routeId("incoming.receive")
        ;

        from("activemq:queue:" + CamelRouteNames.TRANSFORM_QUEUE + activeMqConsumerParameters)
               // .to("log:raw:" + getClass().getSimpleName() + "?showAll=true&multiline=true")
                .choice()
                    .when(header(TRANSFORM_SOAP).isEqualTo(simple(TRANSFORM_SOAP)))
                        .to("xslt:xsl/siri_soap_raw.xsl?saxon=true&allowStAX=false&resultHandlerFactory=#streamResultHandlerFactory") // Extract SOAP version and convert to raw SIRI
                    .endChoice()
                .end()
                .choice()
                    .when(header(TRANSFORM_VERSION).isEqualTo(simple(TRANSFORM_VERSION)))
                        .to("xslt:xsl/siri_14_20.xsl?saxon=true&allowStAX=false&resultHandlerFactory=#streamResultHandlerFactory") // Convert from v1.4 to 2.0
                    .endChoice()
                .end()
                .to("seda:" + CamelRouteNames.ROUTER_QUEUE)
                .routeId("incoming.transform")
        ;

        from("seda:" + CamelRouteNames.ROUTER_QUEUE)
                .choice()
                .when().xpath("/siri:Siri/siri:HeartbeatNotification", ns)
                    .to("activemq:queue:" + CamelRouteNames.HEARTBEAT_QUEUE + activeMQParameters)
                .endChoice()
                .when().xpath("/siri:Siri/siri:CheckStatusResponse", ns)
                    .to("activemq:queue:" + CamelRouteNames.HEARTBEAT_QUEUE + activeMQParameters)
                .endChoice()
                .when().xpath("/siri:Siri/siri:ServiceDelivery/siri:SituationExchangeDelivery", ns)
                    .to("activemq:queue:" + CamelRouteNames.SITUATION_EXCHANGE_QUEUE + activeMQParameters)
                .endChoice()
                .when().xpath("/siri:Siri/siri:ServiceDelivery/siri:VehicleMonitoringDelivery", ns)
                    .to("activemq:queue:" + CamelRouteNames.VEHICLE_MONITORING_QUEUE + activeMQParameters)
                .endChoice()
                .when().xpath("/siri:Siri/siri:ServiceDelivery/siri:EstimatedTimetableDelivery", ns)
                    .to("activemq:queue:" + CamelRouteNames.ESTIMATED_TIMETABLE_QUEUE + activeMQParameters)
                .endChoice()
                .when().xpath("/siri:Siri/siri:ServiceDelivery/siri:ProductionTimetableDelivery", ns)
                    .to("activemq:queue:" + CamelRouteNames.PRODUCTION_TIMETABLE_QUEUE + activeMQParameters)
                .endChoice()
                .when().xpath("/siri:Siri/siri:DataReadyNotification", ns)
                    .to("activemq:queue:" + CamelRouteNames.FETCHED_DELIVERY_QUEUE + activeMQParameters)
                .endChoice()
                .otherwise()
                    .to("activemq:queue:" + CamelRouteNames.DEFAULT_PROCESSOR_QUEUE + activeMQParameters)
                .end()
                .routeId("incoming.redirect")
        ;


        from("activemq:queue:" + CamelRouteNames.DEFAULT_PROCESSOR_QUEUE + activeMqConsumerParameters)
                .log("Processing request in default-queue [" + CamelRouteNames.DEFAULT_PROCESSOR_QUEUE + "].")
                .process(p -> {
                    String path = p.getIn().getHeader("CamelHttpPath", String.class);

                    String subscriptionId = getSubscriptionIdFromPath(path);
                    String datasetId = null;

                    InputStream xml = p.getIn().getBody(InputStream.class);
                    handler.handleIncomingSiri(subscriptionId, xml, datasetId, SiriHandler.getIdMappingPolicy((String) p.getIn().getHeader("useOriginalId")), -1);

                })
                .routeId("incoming.processor.default")
        ;

        from("activemq:queue:" + CamelRouteNames.HEARTBEAT_QUEUE + activeMqConsumerParameters)
                .process(p -> {
                    String subscriptionId = getSubscriptionIdFromPath(p.getIn().getHeader("CamelHttpPath", String.class));

                    InputStream xml = p.getIn().getBody(InputStream.class);
                    handler.handleIncomingSiri(subscriptionId, xml);

                })
                .routeId("incoming.processor.heartbeat")
        ;


        from("activemq:queue:" + CamelRouteNames.FETCHED_DELIVERY_QUEUE + activeMqConsumerParameters)
                .log("Processing fetched delivery")
                .process(p -> {
                    String routeName = null;

                    String subscriptionId = getSubscriptionIdFromPath(p.getIn().getHeader("CamelHttpPath", String.class));

                    SubscriptionSetup subscription = subscriptionManager.get(subscriptionId);
                    if (subscription != null) {
                        routeName = subscription.getServiceRequestRouteName();
                    }

                    p.getOut().setHeader("routename", routeName);

                })
                .choice()
                .when(header("routename").isNotNull())
                    .toD("seda:${header.routename}")
                .endChoice()
                .routeId("incoming.processor.fetched_delivery")
        ;

        from("activemq:queue:" + CamelRouteNames.SITUATION_EXCHANGE_QUEUE + activeMqConsumerParameters)
                .log("Processing SX")
                .process(p -> {
                    String subscriptionId = getSubscriptionIdFromPath(p.getIn().getHeader("CamelHttpPath", String.class));

                    InputStream xml = p.getIn().getBody(InputStream.class);
                    handler.handleIncomingSiri(subscriptionId, xml);

                })
                .routeId("incoming.processor.sx")
        ;

        from("activemq:queue:" + CamelRouteNames.VEHICLE_MONITORING_QUEUE + activeMqConsumerParameters)
                .log("Processing VM")
                .process(p -> {

                    String subscriptionId = getSubscriptionIdFromPath(p.getIn().getHeader("CamelHttpPath", String.class));

                    InputStream xml = p.getIn().getBody(InputStream.class);
                    handler.handleIncomingSiri(subscriptionId, xml);

                })
                .routeId("incoming.processor.vm")
        ;

        from("activemq:queue:" + CamelRouteNames.ESTIMATED_TIMETABLE_QUEUE + activeMqConsumerParameters)
                .log("Processing ET")
                .process(p -> {
                    String subscriptionId = getSubscriptionIdFromPath(p.getIn().getHeader("CamelHttpPath", String.class));

                    InputStream xml = p.getIn().getBody(InputStream.class);
                    handler.handleIncomingSiri(subscriptionId, xml);

                })
                .routeId("incoming.processor.et")
        ;


        from("activemq:queue:" + CamelRouteNames.PRODUCTION_TIMETABLE_QUEUE + activeMqConsumerParameters)
                .log("Processing PT")
                .process(p -> {
                    String subscriptionId = getSubscriptionIdFromPath(p.getIn().getHeader("CamelHttpPath", String.class));

                    InputStream xml = p.getIn().getBody(InputStream.class);

                    handler.handleIncomingSiri(subscriptionId, xml);

                })
                .routeId("incoming.processor.pt")
        ;

    }

    private SiriValidator.Version resolveSiriVersionFromString(String version) {
        if (version != null) {
            switch (version) {
                case "1.0":
                    return SiriValidator.Version.VERSION_1_0;
                case "1.3":
                    return SiriValidator.Version.VERSION_1_3;
                case "1.4":
                    return SiriValidator.Version.VERSION_1_4;
                case "2.0":
                    return SiriValidator.Version.VERSION_2_0;
            }
        }
        return SiriValidator.Version.VERSION_2_0;
    }

    private String getSubscriptionIdFromPath(String path) {
        if (configuration.getIncomingPathPattern().startsWith("/")) {
            if (!path.startsWith("/")) {
                path = "/"+path;
            }
        } else {
            if (path.startsWith("/")) {
                path = path.substring(1);
            }
        }


        Map<String, String> values = calculatePathVariableMap(path);
        logger.trace("Incoming delivery {}", values);

        return values.get("subscriptionId");
    }

    private Map<String, String> calculatePathVariableMap(String path) {
        String[] parameters = path.split("/");
        String[] parameterNames = configuration.getIncomingPathPattern().split("/");

        Map<String, String> values = new HashMap<>();
        for (int i = 0; i < parameterNames.length; i++) {

            String value = (parameters.length > i ? parameters[i] : null);

            if (parameterNames[i].startsWith("{")) {
                parameterNames[i] = parameterNames[i].substring(1);
            }
            if (parameterNames[i].endsWith("}")) {
                parameterNames[i] = parameterNames[i].substring(0, parameterNames[i].lastIndexOf("}"));
            }

            values.put(parameterNames[i], value);
        }

        return values;
    }

}
