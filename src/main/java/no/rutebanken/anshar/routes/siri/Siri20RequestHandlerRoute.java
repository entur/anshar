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
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.model.rest.RestParamType;
import org.entur.siri21.util.SiriXml;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Service;
import uk.org.siri.siri21.Siri;

import javax.xml.stream.XMLStreamException;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import static no.rutebanken.anshar.routes.HttpParameter.PARAM_DATASET_ID;
import static no.rutebanken.anshar.routes.HttpParameter.PARAM_EXCLUDED_DATASET_ID;
import static no.rutebanken.anshar.routes.HttpParameter.PARAM_MAX_SIZE;
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

        rest("anshar")
                .consumes(MediaType.APPLICATION_XML).produces(MediaType.APPLICATION_XML)

                .post("/anshar/services").to("direct:process.service.anshar.rest.request")
                        .param().required(false).name(PARAM_EXCLUDED_DATASET_ID).type(RestParamType.query).description("Comma-separated list of dataset-IDs to be excluded from response (SIRI ET and VM)").dataType("string").endParam()
                        .description("Backwards compatible endpoint used for SIRI ServiceRequest.")

                .post("/anshar/services/{" + PARAM_DATASET_ID + "}").to("direct:process.service.anshar.rest.request.codespace")
                        .description("Backwards compatible endpoint used for SIRI ServiceRequest limited to single dataprovider.")
                        .param().required(false).name(PARAM_DATASET_ID).type(RestParamType.path).description("The id of the Codespace to limit data to").dataType("string").endParam()

                .post("/anshar/subscribe").to("direct:process.subscription.anshar.rest.request")
                        .description("Backwards compatible endpoint used for SIRI SubscriptionRequest.")

                .post("/anshar/subscribe/{" + PARAM_DATASET_ID + "}").to("direct:process.subscription.anshar.rest.request.codespace")
                    .description("Backwards compatible endpoint used for SIRI SubscriptionRequest limited to single dataprovider.")
                    .param().required(false).name(PARAM_DATASET_ID).type(RestParamType.path).description("The id of the Codespace to limit data to").dataType("string").endParam()

                .post("/services").to("direct:process.service.rest.request")
                    .description("Endpoint used for SIRI ServiceRequest.")

                .post("/services/{" + PARAM_DATASET_ID + "}").to("direct:process.service.rest.request.codespace")
                    .description("Endpoint used for SIRI ServiceRequest limited to single dataprovider.")
                    .param().required(false).name(PARAM_DATASET_ID).type(RestParamType.path).description("The id of the Codespace to limit data to").dataType("string").endParam()

                // Endpoints that returned cached data
                .post("/services-cache").to("direct:process.service.rest.request.cache")
                .post("/services-cache/{" + PARAM_DATASET_ID + "}").to("direct:process.service.rest.request.cache.codespace")


                .post("/subscribe").to("direct:process.subscription.rest.request")
                    .description("Endpoint used for SIRI SubscriptionRequest.")
                .post("/subscribe/{" + PARAM_DATASET_ID + "}").to("direct:process.subscription.rest.request.codespace")
                    .description("Endpoint used for SIRI SubscriptionRequest limited to single dataprovider.")
                    .param().required(false).name(PARAM_DATASET_ID).type(RestParamType.path).description("The id of the Codespace to limit data to").dataType("string").endParam()
        ;

        // Handle optional path-parameters
        from("direct:process.service.anshar.rest.request").to("direct:process.service.request");
        from("direct:process.service.anshar.rest.request.codespace").to("direct:process.service.request");
        from("direct:process.subscription.anshar.rest.request").to("direct:process.subscription.request");
        from("direct:process.subscription.anshar.rest.request.codespace").to("direct:process.subscription.request");

        from("direct:process.service.rest.request").to("direct:process.service.request");
        from("direct:process.service.rest.request.codespace").to("direct:process.service.request");

        from("direct:process.service.rest.request.cache").to("direct:process.service.request.cache");
        from("direct:process.service.rest.request.cache.codespace").to("direct:process.service.request.cache");

        from("direct:process.subscription.rest.request").to("direct:process.subscription.request");
        from("direct:process.subscription.rest.request.codespace").to("direct:process.subscription.request");


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
}
