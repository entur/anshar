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
import no.rutebanken.anshar.config.AnsharConfiguration;
import no.rutebanken.anshar.routes.RestRouteBuilder;
import no.rutebanken.anshar.routes.siri.handlers.SiriHandler;
import no.rutebanken.anshar.subscription.SubscriptionManager;
import no.rutebanken.anshar.subscription.SubscriptionSetup;
import org.apache.camel.Exchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Service;

import static no.rutebanken.anshar.routes.HttpParameter.INTERNAL_SIRI_DATA_TYPE;
import static no.rutebanken.anshar.routes.HttpParameter.PARAM_SUBSCRIPTION_ID;

@SuppressWarnings("unchecked")
@Service
@Configuration
public class SiriIncomingDataHandlerRoute extends RestRouteBuilder {

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
                // Endpoints for receiving SIRI deliveries
                .post("/{version}/{type}/{vendor}/{" + PARAM_SUBSCRIPTION_ID + "}").to("direct:process.incoming.rest.request")
                .post("/{version}/{type}/{vendor}/{" + PARAM_SUBSCRIPTION_ID + "}/{service}").to("direct:process.incoming.rest.request.service")
                .post("/{version}/{type}/{vendor}/{" + PARAM_SUBSCRIPTION_ID + "}/{service}/{operation}").to("direct:process.incoming.rest.request.service.operation")
        ;

        // Handle optional path-parameters
        from("direct:process.incoming.rest.request").to("direct:process.incoming.request");
        from("direct:process.incoming.rest.request.service").to("direct:process.incoming.request");
        from("direct:process.incoming.rest.request.service.operation").to("direct:process.incoming.request");

        from("direct:set.mdc.subscriptionId")
                .process(p -> MDC.put("subscriptionId", p.getIn().getHeader("subscriptionId", String.class)))
        ;

        from("direct:clear.mdc.subscriptionId")
                .process(p -> MDC.remove("subscriptionId"))
        ;

        from("direct:process.incoming.request")
                .to("direct:set.mdc.subscriptionId")
                .choice()
                    .when(this::subscriptionExistsAndIsActive)
                        //Valid subscription
                        .wireTap("direct:async.process.request")
                        .setHeader(Exchange.HTTP_RESPONSE_CODE, constant("200"))
                        .setBody(constant(null))
                    .endChoice()
                    .otherwise()
                        // Invalid subscription
                        .log("Ignoring incoming delivery for invalid subscription")
                        .removeHeaders("*") // Remove all headers to avoid potentially leaking information
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
            .routeId("async.process.incoming")
        ;
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
            e.getMessage().setHeaders(e.getIn().getHeaders());
            e.getMessage().setBody(e.getIn().getBody());

            if ("1.4".equals(subscriptionSetup.getVersion())) {
                e.getMessage().setHeader(TRANSFORM_VERSION, TRANSFORM_VERSION);
            }

            if (subscriptionSetup.getServiceType() == SubscriptionSetup.ServiceType.SOAP) {
                e.getMessage().setHeader(TRANSFORM_SOAP, TRANSFORM_SOAP);
            }
        }

        return existsAndIsActive;
    }
}
