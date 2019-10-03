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
import no.rutebanken.anshar.routes.RestRouteBuilder;
import no.rutebanken.anshar.routes.dataformat.SiriDataFormatHelper;
import no.rutebanken.anshar.routes.siri.handlers.SiriHandler;
import no.rutebanken.anshar.subscription.SubscriptionManager;
import no.rutebanken.anshar.subscription.SubscriptionSetup;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.builder.xml.Namespaces;
import org.apache.camel.model.rest.RestParamType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Service;
import uk.org.siri.siri20.Siri;

import javax.ws.rs.core.MediaType;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static no.rutebanken.anshar.routes.HttpParameter.*;

@SuppressWarnings("unchecked")
@Service
@Configuration
public class Siri20RequestHandlerRoute extends RestRouteBuilder {

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

        super.configure();

        Namespaces ns = new Namespaces("siri", "http://www.siri.org.uk/siri")
                .add("xsd", "http://www.w3.org/2001/XMLSchema");

        String activeMQParameters = "?disableReplyTo=true&timeToLive="+ configuration.getTimeToLive();
        String activeMqConsumerParameters = "?asyncConsumer=true&concurrentConsumers="+ configuration.getConcurrentConsumers();

        rest("anshar").tag("siri")
                .consumes(MediaType.APPLICATION_XML).produces(MediaType.APPLICATION_XML)

                .post("/anshar/services").to("direct:process.service.request")
                        .param().required(false).name(PARAM_EXCLUDED_DATASET_ID).type(RestParamType.query).description("Comma-separated list of dataset-IDs to be excluded from response (SIRI ET and VM)").dataType("string").endParam()
                        .description("Backwards compatible endpoint used for SIRI ServiceRequest.")

                .post("/anshar/services/{" + PARAM_DATASET_ID + "}").to("direct:process.service.request")
                        .description("Backwards compatible endpoint used for SIRI ServiceRequest limited to single dataprovider.")
                        .param().required(false).name(PARAM_DATASET_ID).type(RestParamType.path).description("The id of the Codespace to limit data to").dataType("string").endParam()

                .post("/anshar/subscribe").to("direct:process.subscription.request")
                        .description("Backwards compatible endpoint used for SIRI SubscriptionRequest.")

                .post("/anshar/subscribe/{" + PARAM_DATASET_ID + "}").to("direct:process.subscription.request")
                    .description("Backwards compatible endpoint used for SIRI SubscriptionRequest limited to single dataprovider.")
                    .param().required(false).name(PARAM_DATASET_ID).type(RestParamType.path).description("The id of the Codespace to limit data to").dataType("string").endParam()

                .post("/services").to("direct:process.service.request")
                    .param().required(false).name(PARAM_EXCLUDED_DATASET_ID).type(RestParamType.query).description("Comma-separated list of dataset-IDs to be excluded from response (SIRI ET and VM)").dataType("string").endParam()
                    .description("Endpoint used for SIRI ServiceRequest.")

                .post("/services/{" + PARAM_DATASET_ID + "}").to("direct:process.service.request")
                    .description("Endpoint used for SIRI ServiceRequest limited to single dataprovider.")
                    .param().required(false).name(PARAM_DATASET_ID).type(RestParamType.path).description("The id of the Codespace to limit data to").dataType("string").endParam()


                .post("/subscribe").to("direct:process.subscription.request")
                        .description("Endpoint used for SIRI SubscriptionRequest.")

                .post("/subscribe/{" + PARAM_DATASET_ID + "}").to("direct:process.subscription.request")
                        .description("Endpoint used for SIRI SubscriptionRequest limited to single dataprovider.")
                        .param().required(false).name(PARAM_DATASET_ID).type(RestParamType.path).description("The id of the Codespace to limit data to").dataType("string").endParam()

                .post("/{version}/{type}/{vendor}/{" + PARAM_SUBSCRIPTION_ID + "}").to("direct:process.incoming.request")
                        .apiDocs(false)

                .post("/{version}/{type}/{vendor}/{" + PARAM_SUBSCRIPTION_ID + "}/{service}").to("direct:process.incoming.request")
                        .apiDocs(false)

                .post("/{version}/{type}/{vendor}/{" + PARAM_SUBSCRIPTION_ID + "}/{service}/{operation}").to("direct:process.incoming.request")
                        .description("Generated dynamically when creating Subscription. Endpoint for incoming data")
                        .param().required(false).name("service").endParam()
                        .param().required(false).name("operation").endParam()
        ;

        from("direct:process.incoming.request")
                .to("log:incoming:" + getClass().getSimpleName() + "?showAll=true&multiline=true&showStreams=true")
                .choice()
                .when(e -> subscriptionExistsAndIsActive(e))
                    //Valid subscription
                    .to("activemq:queue:" + CamelRouteNames.TRANSFORM_QUEUE + activeMQParameters)
                    .setHeader(Exchange.HTTP_RESPONSE_CODE, constant("200"))
                    .setBody(constant(null))
                .endChoice()
                .otherwise()
                    // Invalid subscription
                    .log("Ignoring incoming delivery for invalid subscription")
                    .removeHeaders("*")
                    .setHeader(Exchange.HTTP_RESPONSE_CODE, constant("403")) //403 Forbidden
                    .setBody(constant("Subscription is not valid"))
                .endChoice()
        .routeId("process.incoming")
                ;

        from("direct:process.subscription.request")
                .to("log:subRequest:" + getClass().getSimpleName() + "?showAll=true&multiline=true&showStreams=true")
                .choice()
                .when(e -> isTrackingHeaderAcceptable(e))
                    .process(p -> {
                        String datasetId = p.getIn().getHeader(PARAM_DATASET_ID, String.class);
                        String clientTrackingName = p.getIn().getHeader(configuration.getTrackingHeaderName(), String.class);

                        InputStream xml = p.getIn().getBody(InputStream.class);

                        Siri response = handler.handleIncomingSiri(null, xml, datasetId, SiriHandler.getIdMappingPolicy((String) p.getIn().getHeader(PARAM_USE_ORIGINAL_ID)), -1, clientTrackingName);
                        if (response != null) {
                            logger.info("Returning SubscriptionResponse");

                            p.getOut().setBody(response);
                        }

                    })
                    .marshal(SiriDataFormatHelper.getSiriJaxbDataformat())
                    .to("log:subResponse:" + getClass().getSimpleName() + "?showAll=true&multiline=true")
                .otherwise()
                    .to("direct:anshar.invalid.tracking.header.response")
                .routeId("process.subscription")
        ;

        from("direct:process.service.request")
                .to("log:serRequest:" + getClass().getSimpleName() + "?showAll=true&multiline=true&showStreams=true")
                .choice()
                .when(e -> isTrackingHeaderAcceptable(e))
                    .process(p -> {
                        Message msg = p.getIn();

                        p.getOut().setHeaders(msg.getHeaders());

                        List<String> excludedIdList = getParameterValuesAsList(msg, PARAM_EXCLUDED_DATASET_ID);
                        String clientTrackingName = p.getIn().getHeader(configuration.getTrackingHeaderName(), String.class);

                        String datasetId = msg.getHeader(PARAM_DATASET_ID, String.class);

                        int maxSize = -1;
                        if (msg.getHeaders().containsKey(PARAM_MAX_SIZE)) {
                            maxSize = Integer.parseInt((String) msg.getHeader(PARAM_MAX_SIZE));
                        }

                        String useOriginalId = msg.getHeader(PARAM_USE_ORIGINAL_ID, String.class);

                        Siri response = handler.handleIncomingSiri(null, msg.getBody(InputStream.class), datasetId, excludedIdList, SiriHandler.getIdMappingPolicy(useOriginalId), maxSize, clientTrackingName);
                        if (response != null) {
                            logger.info("Found ServiceRequest-response, streaming response");
                            p.getOut().setBody(response);
                        }
                    })
                    .marshal(SiriDataFormatHelper.getSiriJaxbDataformat())
                    .to("log:serResponse:" + getClass().getSimpleName() + "?showAll=true&multiline=true&showStreams=true")
                .otherwise()
                    .to("direct:anshar.invalid.tracking.header.response")
                .routeId("process.service")
        ;

        from("activemq:queue:" + CamelRouteNames.TRANSFORM_QUEUE + activeMqConsumerParameters)
                .log("Transformation start")
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
                .log("Transformation done")
                .to("seda:" + CamelRouteNames.ROUTER_QUEUE)
                .routeId("incoming.transform")
        ;

        from("seda:" + CamelRouteNames.ROUTER_QUEUE)
                .log("Rerouting SIRI-XML")
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
                .log("Finished rerouting SIRI-XML")
                .routeId("incoming.redirect")
        ;


        from("activemq:queue:" + CamelRouteNames.DEFAULT_PROCESSOR_QUEUE + activeMqConsumerParameters)
                .log("Processing request in default-queue [" + CamelRouteNames.DEFAULT_PROCESSOR_QUEUE + "].")
                .process(p -> {

                    String subscriptionId = getSubscriptionIdFromPath(p.getIn().getHeader(PARAM_PATH, String.class));
                    String datasetId = null;

                    InputStream xml = p.getIn().getBody(InputStream.class);
                    String useOriginalId = p.getIn().getHeader(PARAM_USE_ORIGINAL_ID, String.class);
                    String clientTrackingName = p.getIn().getHeader(configuration.getTrackingHeaderName(), String.class);

                    handler.handleIncomingSiri(subscriptionId, xml, datasetId, SiriHandler.getIdMappingPolicy(useOriginalId), -1, clientTrackingName);

                })
                .routeId("incoming.processor.default")
        ;

        from("activemq:queue:" + CamelRouteNames.HEARTBEAT_QUEUE + activeMqConsumerParameters)
                .process(p -> {
                    String subscriptionId = getSubscriptionIdFromPath(p.getIn().getHeader(PARAM_PATH, String.class));

                    InputStream xml = p.getIn().getBody(InputStream.class);
                    handler.handleIncomingSiri(subscriptionId, xml);

                })
                .routeId("incoming.processor.heartbeat")
        ;


        from("activemq:queue:" + CamelRouteNames.FETCHED_DELIVERY_QUEUE + activeMqConsumerParameters)
                .log("Processing fetched delivery")
                .process(p -> {
                    String routeName = null;

                    String subscriptionId = getSubscriptionIdFromPath(p.getIn().getHeader(PARAM_PATH, String.class));

                    SubscriptionSetup subscription = subscriptionManager.get(subscriptionId);
                    if (subscription != null) {
                        routeName = subscription.getServiceRequestRouteName();
                    }

                    p.getOut().setHeader("routename", routeName);

                })
                .choice()
                .when(header("routename").isNotNull())
                    .toD("direct:${header.routename}")
                .endChoice()
                .routeId("incoming.processor.fetched_delivery")
        ;

        from("activemq:queue:" + CamelRouteNames.SITUATION_EXCHANGE_QUEUE + activeMqConsumerParameters)
                .log("Processing SX")
                .process(p -> {
                    String subscriptionId = getSubscriptionIdFromPath(p.getIn().getHeader(PARAM_PATH, String.class));

                    InputStream xml = p.getIn().getBody(InputStream.class);
                    handler.handleIncomingSiri(subscriptionId, xml);

                })
                .routeId("incoming.processor.sx")
        ;

        from("activemq:queue:" + CamelRouteNames.VEHICLE_MONITORING_QUEUE + activeMqConsumerParameters)
                .log("Processing VM")
                .process(p -> {

                    String subscriptionId = getSubscriptionIdFromPath(p.getIn().getHeader(PARAM_PATH, String.class));

                    InputStream xml = p.getIn().getBody(InputStream.class);
                    handler.handleIncomingSiri(subscriptionId, xml);

                })
                .routeId("incoming.processor.vm")
        ;

        from("activemq:queue:" + CamelRouteNames.ESTIMATED_TIMETABLE_QUEUE + activeMqConsumerParameters)
                .log("Processing ET")
                .process(p -> {

                    String subscriptionId = getSubscriptionIdFromPath(p.getIn().getHeader(PARAM_PATH, String.class));
                    InputStream xml = p.getIn().getBody(InputStream.class);
                    handler.handleIncomingSiri(subscriptionId, xml);

                })
                .routeId("incoming.processor.et")
        ;
    }

    private boolean subscriptionExistsAndIsActive(Exchange e) {
        String subscriptionId = e.getIn().getHeader(PARAM_SUBSCRIPTION_ID, String.class);
        if (subscriptionId == null || subscriptionId.isEmpty()) {
            return false;
        }
        SubscriptionSetup subscriptionSetup = subscriptionManager.get(subscriptionId);

        if (subscriptionSetup == null) {
            return false;
        }

        boolean existsAndIsActive = (subscriptionManager.isSubscriptionRegistered(subscriptionId) &&
                    subscriptionSetup.isActive());

        if (existsAndIsActive) {
            e.getOut().setHeaders(e.getIn().getHeaders());

            if (!"2.0".equals(subscriptionSetup.getVersion())) {
                e.getOut().setHeader(TRANSFORM_VERSION, TRANSFORM_VERSION);
            }

            if (subscriptionSetup.getServiceType() == SubscriptionSetup.ServiceType.SOAP) {
                e.getOut().setHeader(TRANSFORM_SOAP, TRANSFORM_SOAP);
            }
        }

        return existsAndIsActive;
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
