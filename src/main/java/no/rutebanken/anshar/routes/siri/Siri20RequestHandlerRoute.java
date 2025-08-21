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

import jakarta.ws.rs.core.MediaType;
import jakarta.xml.bind.JAXBException;
import no.rutebanken.anshar.config.AnsharConfiguration;
import no.rutebanken.anshar.routes.RestRouteBuilder;
import no.rutebanken.anshar.routes.siri.handlers.OutboundIdMappingPolicy;
import no.rutebanken.anshar.routes.siri.handlers.SiriHandler;
import no.rutebanken.anshar.subscription.SubscriptionManager;
import no.rutebanken.anshar.subscription.SubscriptionSetup;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.model.rest.RestParamType;
import org.entur.siri21.util.SiriXml;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Service;
import uk.org.siri.siri21.Siri;

import javax.xml.stream.XMLStreamException;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import static no.rutebanken.anshar.routes.HttpParameter.INTERNAL_SIRI_DATA_TYPE;
import static no.rutebanken.anshar.routes.HttpParameter.PARAM_DATASET_ID;
import static no.rutebanken.anshar.routes.HttpParameter.PARAM_EXCLUDED_DATASET_ID;
import static no.rutebanken.anshar.routes.HttpParameter.PARAM_MAX_SIZE;
import static no.rutebanken.anshar.routes.HttpParameter.PARAM_SUBSCRIPTION_ID;
import static no.rutebanken.anshar.routes.HttpParameter.PARAM_USE_ORIGINAL_ID;
import static no.rutebanken.anshar.routes.HttpParameter.SIRI_VERSION_HEADER_NAME;
import static no.rutebanken.anshar.routes.HttpParameter.getParameterValuesAsList;

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

                // Endpoints that returned cached data
                .post("/services-cache").to("direct:process.service.request.cache")
                .post("/services-cache/{" + PARAM_DATASET_ID + "}").to("direct:process.service.request.cache")


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

        from("direct:set.mdc.subscriptionId")
                .process(p -> MDC.put("subscriptionId", p.getIn().getHeader("subscriptionId", String.class)))
        ;

        from("direct:clear.mdc.subscriptionId")
                .process(p -> MDC.remove("subscriptionId"))
        ;

        from("direct:process.incoming.request")
                .to("direct:set.mdc.subscriptionId")
                .removeHeaders("<Siri*") //Since Camel 3, entire body is also included as header
//                .to("log:incoming:" + getClass().getSimpleName() + "?showAll=true&multiline=true&showStreams=true")
                .choice()
                    .when(e -> subscriptionExistsAndIsActive(e))
                        //Valid subscription
                        .wireTap("direct:async.process.request")
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
                .end()
                .to("direct:clear.mdc.subscriptionId")
            .routeId("process.incoming")
                ;

        from("direct:async.process.request")
            .to("direct:set.mdc.subscriptionId")
            .convertBodyTo(String.class)
            .process(p -> {
                p.getMessage().setBody(p.getIn().getBody());
                p.getMessage().setHeaders(p.getIn().getHeaders());
                p.getMessage().setHeader(INTERNAL_SIRI_DATA_TYPE, getSubscriptionDataType(p));
            })
            .to("direct:enqueue.message")
            .to("direct:clear.mdc.subscriptionId")
            .routeId("async.process.incoming")
        ;

        from("direct:process.subscription.request")
                .to("log:subRequest:" + getClass().getSimpleName() + "?showAll=true&multiline=true&showStreams=true")
                .choice()
                .when(e -> isTrackingHeaderBlocked(e))
                    .to("direct:anshar.blocked.tracking.header.response")
                .endChoice()
                .when(e -> isTrackingHeaderAcceptable(e))
                    .choice()
                        .when().xpath("/siri:Siri/siri:SubscriptionRequest/siri:VehicleMonitoringSubscriptionRequest", ns)
                        .to("direct:process.vm.subscription.request")
                        .when().xpath("/siri:Siri/siri:SubscriptionRequest/siri:SituationExchangeSubscriptionRequest", ns)
                        .to("direct:process.sx.subscription.request")
                        .when().xpath("/siri:Siri/siri:SubscriptionRequest/siri:EstimatedTimetableSubscriptionRequest", ns)
                        .to("direct:process.et.subscription.request")
                        .when().xpath("/siri:Siri/siri:TerminateSubscriptionRequest", ns)
                            // Forwarding TerminateRequest to all data-instances
                            .wireTap("direct:process.et.subscription.request")
                            .wireTap("direct:process.vm.subscription.request")
                            .wireTap("direct:process.sx.subscription.request")
                            .to("direct:internal.handle.subscription") //Build response
                    .endChoice()
                .endChoice()
                .otherwise()
                    .to("direct:anshar.invalid.tracking.header.response")
                .routeId("process.subscription")
        ;

        from("direct:internal.handle.subscription")
                .process(p -> {
                    String datasetId = p.getIn().getHeader(PARAM_DATASET_ID, String.class);
                    String clientTrackingName = p.getIn().getHeader(configuration.getTrackingHeaderName(), String.class);

                    InputStream xml = p.getIn().getBody(InputStream.class);
                    boolean siri21Version = isSiri21Version(xml);

                    OutboundIdMappingPolicy idMappingPolicy = SiriHandler.getIdMappingPolicy((String) p.getIn().getHeader(PARAM_USE_ORIGINAL_ID));

                    if (siri21Version) {
                        idMappingPolicy = OutboundIdMappingPolicy.SIRI_2_1;
                    }

                    Siri response = handler.handleIncomingSiri(null, xml, datasetId, idMappingPolicy, -1, clientTrackingName);
                    if (response != null) {
                        logger.info("Returning SubscriptionResponse");

                        if (siri21Version) {
                            p.getMessage().setBody(SiriXml.toXml(response));
                        } else {
                            p.getMessage().setBody(org.rutebanken.siri20.util.SiriXml.toXml(
                                    downgradeSiriVersion(response)
                            ));
                        }
                    }

                })
                .setHeader(Exchange.CONTENT_TYPE, simple(MediaType.APPLICATION_XML))
                .to("log:subResponse:" + getClass().getSimpleName() + "?showAll=true&multiline=true")
        ;

        from("direct:process.service.request")
                .choice()
                    .when().xpath("/siri:Siri/siri:ServiceRequest/siri:VehicleMonitoringRequest", ns)
                        .to("direct:process.vm.service.request")
                    .when().xpath("/siri:Siri/siri:ServiceRequest/siri:SituationExchangeRequest", ns)
                        .to("direct:process.sx.service.request")
                    .when().xpath("/siri:Siri/siri:ServiceRequest/siri:EstimatedTimetableRequest", ns)
                        .to("direct:process.et.service.request.cache")
                .endChoice()
                .otherwise()
                    .to("direct:internal.process.service.request")
                .end()
        ;
        from("direct:internal.process.service.request")
                .to("log:serRequest:" + getClass().getSimpleName() + "?showAll=true&multiline=true&showStreams=true")
                .choice()
                .when(e -> isTrackingHeaderAcceptable(e))
                    .process(p -> {
                        Message msg = p.getIn();

                        List<String> excludedIdList = getParameterValuesAsList(msg, PARAM_EXCLUDED_DATASET_ID);
                        String clientTrackingName = p.getIn().getHeader(configuration.getTrackingHeaderName(), String.class);

                        String datasetId = msg.getHeader(PARAM_DATASET_ID, String.class);

                        int maxSize = -1;
                        if (msg.getHeaders().containsKey(PARAM_MAX_SIZE)) {
                            maxSize = Integer.parseInt((String) msg.getHeader(PARAM_MAX_SIZE));
                        }
                        OutboundIdMappingPolicy idMappingPolicy = SiriHandler.getIdMappingPolicy((String) p.getIn().getHeader(PARAM_USE_ORIGINAL_ID));

                        InputStream inputStream = msg.getBody(InputStream.class);
                        boolean siri21Version = isSiri21Version(inputStream);

                        if (siri21Version) {
                            idMappingPolicy = OutboundIdMappingPolicy.SIRI_2_1;
                        }

                        Siri response = handler.handleIncomingSiri(null, inputStream, datasetId, excludedIdList, idMappingPolicy, maxSize, clientTrackingName);
                        if (response != null) {
                            logger.info("Found ServiceRequest-response, streaming response");
                            if (siri21Version) {
                                p.getMessage().setBody(SiriXml.toXml(response));
                            } else {
                                p.getMessage().setBody(org.rutebanken.siri20.util.SiriXml.toXml(
                                        downgradeSiriVersion(response)
                                ));
                            }
                        }
                    })
                    .setHeader(Exchange.CONTENT_TYPE, simple(MediaType.APPLICATION_XML))
                    .to("log:serResponse:" + getClass().getSimpleName() + "?showAll=true&multiline=true&showStreams=true")
                .otherwise()
                    .to("direct:anshar.invalid.tracking.header.response")
                .routeId("process.service")
        ;

        from("direct:process.service.request.cache")
                .choice()
                    .when().xpath("/siri:Siri/siri:ServiceRequest/siri:VehicleMonitoringRequest", ns)
                        .to("direct:process.vm.service.request.cache")
                    .when().xpath("/siri:Siri/siri:ServiceRequest/siri:SituationExchangeRequest", ns)
                        .to("direct:process.sx.service.request.cache")
                    .when().xpath("/siri:Siri/siri:ServiceRequest/siri:EstimatedTimetableRequest", ns)
                        .to("direct:process.et.service.request.cache")
                .endChoice()
        ;

        from("direct:internal.process.service.request.cache")
            .to("log:serRequest:" + getClass().getSimpleName() + "?showAll=true&multiline=true&showStreams=true")
            .process(p -> {
                Message msg = p.getIn();

                String datasetId = msg.getHeader(PARAM_DATASET_ID, String.class);
                String clientTrackingName = p.getIn().getHeader(configuration.getTrackingHeaderName(), String.class);

                Siri request = SiriXml.parseXml(msg.getBody(InputStream.class));

                Siri response = handler.handleSiriCacheRequest(request, datasetId, clientTrackingName);

                if (response != null) {
                    logger.info("Found ServiceRequest-response, streaming response");

                    boolean isSiri21Version = "2.1".equals(request.getVersion());

                    if ("2.1".equals(p.getIn().getHeader(SIRI_VERSION_HEADER_NAME))) {
                        // If the request explicitly asks for SIRI 2.1, we assume it is a SIRI 2.1 request.
                        isSiri21Version = true;
                    }

                    // If the request is for SIRI 2.1, we return the response as is.
                    if (isSiri21Version) {
                        p.getMessage().setBody(SiriXml.toXml(response));
                    } else {
                        p.getMessage().setBody(org.rutebanken.siri20.util.SiriXml.toXml(
                                downgradeSiriVersion(response)
                        ));
                    }
                }
            })
            .to("log:serResponse:" + getClass().getSimpleName() + "?showAll=true&multiline=true&showStreams=true")
            .routeId("process.service.cache")
        ;

    }

    private static boolean isSiri21Version(InputStream inputStream) throws JAXBException, XMLStreamException, IOException {
        Siri incomingRequest = SiriXml.parseXml(inputStream);

        boolean siri21Version = "2.1".equals(incomingRequest.getVersion());
        inputStream.reset();

        return siri21Version;
    }

    private String getSubscriptionDataType(Exchange e) {
        String subscriptionId = e.getIn().getHeader(PARAM_SUBSCRIPTION_ID, String.class);
        if (subscriptionId == null || subscriptionId.isEmpty()) {
            return null;
        }
        SubscriptionSetup subscriptionSetup = subscriptionManager.get(subscriptionId);

        if (subscriptionSetup == null) {
            return null;
        }
        return subscriptionSetup.getSubscriptionType().name();
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
            e.getOut().setBody(e.getIn().getBody());

            if ("1.4".equals(subscriptionSetup.getVersion())) {
                e.getOut().setHeader(TRANSFORM_VERSION, TRANSFORM_VERSION);
            }

            if (subscriptionSetup.getServiceType() == SubscriptionSetup.ServiceType.SOAP) {
                e.getOut().setHeader(TRANSFORM_SOAP, TRANSFORM_SOAP);
            }
        }

        return existsAndIsActive;
    }
}
