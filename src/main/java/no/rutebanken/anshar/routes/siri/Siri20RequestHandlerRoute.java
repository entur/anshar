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
import no.rutebanken.anshar.routes.RestRouteBuilder;
import no.rutebanken.anshar.routes.dataformat.SiriDataFormatHelper;
import no.rutebanken.anshar.routes.siri.handlers.SiriHandler;
import no.rutebanken.anshar.subscription.SubscriptionManager;
import no.rutebanken.anshar.subscription.SubscriptionSetup;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.model.rest.RestParamType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Service;
import uk.org.siri.siri20.Siri;

import javax.ws.rs.core.MediaType;
import java.io.InputStream;
import java.util.List;

import static no.rutebanken.anshar.routes.HttpParameter.PARAM_DATASET_ID;
import static no.rutebanken.anshar.routes.HttpParameter.PARAM_EXCLUDED_DATASET_ID;
import static no.rutebanken.anshar.routes.HttpParameter.PARAM_MAX_SIZE;
import static no.rutebanken.anshar.routes.HttpParameter.PARAM_SUBSCRIPTION_ID;
import static no.rutebanken.anshar.routes.HttpParameter.PARAM_USE_ORIGINAL_ID;
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
                    .process(p -> {
                        p.getOut().setBody(p.getIn().getBody(String.class));
                        p.getOut().setHeaders(p.getIn().getHeaders());
                    })
                    .to("direct:enqueue.message")
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
}
