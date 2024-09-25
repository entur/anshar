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
import jakarta.ws.rs.core.Response;
import no.rutebanken.anshar.config.AnsharConfiguration;
import no.rutebanken.anshar.routes.BaseRouteBuilder;
import no.rutebanken.anshar.subscription.SiriDataType;
import no.rutebanken.anshar.subscription.SubscriptionManager;
import no.rutebanken.anshar.subscription.SubscriptionSetup;
import org.apache.camel.Exchange;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

@Component
public class DeliveryStatusRoute extends BaseRouteBuilder {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    @Value("${anshar.data.handler.baseurl.vm:}")
    protected String vmHandlerBaseUrl;

    @Value("${anshar.data.handler.baseurl.et:}")
    protected String etHandlerBaseUrl;

    @Value("${anshar.data.handler.baseurl.sx:}")
    protected String sxHandlerBaseUrl;

    public DeliveryStatusRoute(AnsharConfiguration config, SubscriptionManager subscriptionManager) {
        super(config, subscriptionManager);
    }


    @Override
    public void configure() throws Exception {
        if (config.processAdmin() && !config.processData()) {
            from("direct:anshar.rest.subscription.status")
                    .process(p -> {
                        p.getOut().setHeaders(p.getIn().getHeaders());

                        String subscriptionId = p.getIn().getHeader("subscriptionId", String.class);
                        SubscriptionSetup subscriptionSetup = subscriptionManager.get(subscriptionId);
                        if (subscriptionSetup != null) {
                            p.getOut().setHeader("type", subscriptionSetup.getSubscriptionType().name());
                        }
                    })
                    .choice()
                    .when(header("type").isEqualTo(SiriDataType.ESTIMATED_TIMETABLE.name()))
                        .toD(etHandlerBaseUrl + "/anshar/rest/status/${header.subscriptionId}?bridgeEndpoint=true&httpMethod=GET")
                    .end()
                    .choice()
                    .when(header("type").isEqualTo(SiriDataType.VEHICLE_MONITORING.name()))
                    .toD(vmHandlerBaseUrl +  "/anshar/rest/status/${header.subscriptionId}?bridgeEndpoint=true&httpMethod=GET")
                    .end()
                    .choice()
                    .when(header("type").isEqualTo(SiriDataType.SITUATION_EXCHANGE.name()))
                    .toD(sxHandlerBaseUrl +  "/anshar/rest/status/${header.subscriptionId}?bridgeEndpoint=true&httpMethod=GET")
                    .end()
                    .routeId("admin.get.subscription.status")
            ;
        } else {
            from("direct:anshar.rest.subscription.status")
                    .process(p -> {
                        String subscriptionId = p.getIn().getHeader("subscriptionId", String.class);
                        Duration alertLimit = p.getIn().getHeader("limit", Duration.of(60, ChronoUnit.MINUTES), Duration.class);
                        if (alertLimit.isNegative()) {
                            alertLimit = alertLimit.abs();
                        }

                        if (alertLimit.toMinutes() < 10) {
                            alertLimit =Duration.of(10, ChronoUnit.MINUTES);
                        }

                        Instant lastDataReceived = subscriptionManager.getLastDataReceived(subscriptionId);

                        Response.Status responseCode;
                        if (lastDataReceived == null) {
                            responseCode = Response.Status.NOT_FOUND;
                        } else {
                            ZonedDateTime zonedDateTime = ZonedDateTime.ofInstant(lastDataReceived, ZonedDateTime.now().getZone());

                            JSONObject json = new JSONObject();
                            json.put("lastDataReceived", zonedDateTime.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
                            p.getOut().setBody(json.toString());

                            if (alertLimit != null && lastDataReceived.plus(alertLimit).isBefore(Instant.now())) {
                                responseCode = Response.Status.GONE;
                            } else {
                                responseCode = Response.Status.OK;
                            }
                            p.getOut().setHeader(Exchange.CONTENT_TYPE, MediaType.APPLICATION_JSON);
                        }

                        p.getOut().setHeader(Exchange.HTTP_RESPONSE_CODE, responseCode.getStatusCode());
                    });
        }

    }

}
