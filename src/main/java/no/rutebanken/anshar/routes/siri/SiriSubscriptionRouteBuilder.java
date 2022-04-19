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

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import com.sun.xml.bind.marshaller.NamespacePrefixMapper;
import no.rutebanken.anshar.config.AnsharConfiguration;
import no.rutebanken.anshar.data.EstimatedTimetables;
import no.rutebanken.anshar.routes.BaseRouteBuilder;
import no.rutebanken.anshar.routes.siri.transformer.ApplicationContextHolder;
import no.rutebanken.anshar.subscription.SubscriptionManager;
import no.rutebanken.anshar.subscription.SubscriptionSetup;
import no.rutebanken.anshar.subscription.helpers.DataNotReceivedAction;
import no.rutebanken.anshar.subscription.helpers.RequestType;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.http.HttpMethods;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.ws.rs.core.MediaType;
import java.time.Duration;
import java.time.Instant;

@Component
public abstract class SiriSubscriptionRouteBuilder extends BaseRouteBuilder {

    private Logger logger = LoggerFactory.getLogger(this.getClass());
    
    NamespacePrefixMapper customNamespacePrefixMapper;

    SubscriptionSetup subscriptionSetup;

    private Instant restartTriggered = Instant.MIN;

    @Autowired
    EstimatedTimetables estimatedTimetables;

    boolean hasBeenStarted;

    private Instant lastCheckStatus = Instant.now();

    public SiriSubscriptionRouteBuilder(AnsharConfiguration config, SubscriptionManager subscriptionManager) {
        super(config, subscriptionManager);
        estimatedTimetables = ApplicationContextHolder.getContext().getBean(EstimatedTimetables.class);
    }

    String getTimeout() {
        int timeout;
        Duration heartbeatInterval = subscriptionSetup.getHeartbeatInterval();
        if (heartbeatInterval != null) {
            long heartbeatIntervalMillis = heartbeatInterval.toMillis();
            timeout = (int) heartbeatIntervalMillis / 2;
        } else {
            timeout = 30000;
        }

        return "?httpClient.socketTimeout=" + timeout + "&httpClient.connectTimeout=" + timeout;
    }

    protected Processor addCustomHeaders() {
        return exchange -> {
            if (subscriptionSetup.getCustomHeaders() != null && !subscriptionSetup.getCustomHeaders().isEmpty()) {
                exchange.getOut().setHeaders(exchange.getIn().getHeaders());
                exchange.getOut().setBody(exchange.getIn().getBody());
                exchange.getOut().setMessageId(exchange.getIn().getMessageId());
                exchange.getOut().getHeaders().putAll(subscriptionSetup.getCustomHeaders());
            }
        };
    }

    void initTriggerRoutes() {
//        if (!subscriptionManager.isNewSubscription(subscriptionSetup.getSubscriptionId())) {
//            logger.info("Subscription is NOT new - flagging as already started if active {}", subscriptionSetup);
//            hasBeenStarted = subscriptionManager.isActiveSubscription(subscriptionSetup.getSubscriptionId());
//        }
        // Assuming ALL subscriptions are hunky-dory on start-up
        if (subscriptionManager.get(subscriptionSetup.getSubscriptionId()) != null) {
            // Subscription is already initialized on another pod - keep existing status
            hasBeenStarted = subscriptionManager.isActiveSubscription(subscriptionSetup.getSubscriptionId());
        } else {
            // Unknown subscription or first pod to start
            hasBeenStarted = subscriptionSetup.isActive();
        }

        singletonFrom("quartz://anshar/monitor_" + subscriptionSetup.getSubscriptionId() + "?trigger.repeatInterval=" + 15000,
                "monitor.subscription." + subscriptionSetup.getVendor())
                .choice()
                .when(p -> shouldPerformDataNotReceivedAction(p.getFromRouteId()))
                    .log("Performing DataNotReceivedAction: " + subscriptionSetup)
                    .setBody(simple(subscriptionSetup.getDataNotReceivedAction() != null ? subscriptionSetup.getDataNotReceivedAction().getJsonPostContent():""))
                    .setHeader(Exchange.CONTENT_TYPE, constant(MediaType.APPLICATION_JSON))
                    .setHeader(Exchange.HTTP_METHOD, constant(HttpMethods.POST))
                    .to("log:datanotreceived:" + getClass().getSimpleName() + "?showAll=true&multiline=true")
                    .toD(subscriptionSetup.getDataNotReceivedAction() != null ? subscriptionSetup.getDataNotReceivedAction().getEndpoint():"empty", true)
                .when(p -> shouldBeStarted(p.getFromRouteId()))
                    .log("Triggering start subscription: " + subscriptionSetup)
                    .process(p -> hasBeenStarted = true)
                    .to("direct:" + subscriptionSetup.getStartSubscriptionRouteName()) // Start subscription
                .when(p -> shouldBeCancelled(p.getFromRouteId()))
                    .log("Triggering cancel subscription: " + subscriptionSetup)
                    .process(p -> hasBeenStarted = false)
                    .to("direct:" + subscriptionSetup.getCancelSubscriptionRouteName())// Cancel subscription
                .when(p -> shouldCheckStatus(p.getFromRouteId()))
                    .log("Check status: " + subscriptionSetup)
                    .process(p -> lastCheckStatus = Instant.now())
                    .to("direct:" + subscriptionSetup.getCheckStatusRouteName()) // Check status
                .end()
        ;

        from("direct:oauth2.authorize")

                .choice()
                    .when(header("oauth-server").isNotNull())
                        .setHeader("CamelHttpMethod").simple("POST")
                        .setHeader("Content-Type").simple(MediaType.APPLICATION_JSON)
                        .setHeader("Accept") .simple("application/json")
                        .setBody()
                            .simple("{\"grant_type\": \"${header.oauth-grant-type}\", \"client_id\": \"${header.oauth-client-id}\", \"client_secret\": \"${header.oauth-client-secret}\", \"audience\": \"${header.oauth-audience}\"}")
                .toD("${header.oauth-server}")
                .convertBodyTo(String.class)
                .unmarshal().json(JsonLibrary.Jackson, ResponseToken.class)
                .choice()
                    .when().simple("${header.CamelHttpResponseCode} == 200")
                        .setHeader("Authorization").simple("Bearer ${body.access_token}")
                        .log("Authenticated!!!")
                    .otherwise()
                        .log("Not Authenticated!!!")
                    .end()
                .end()
                .routeId("anshar.oauth2.authorize")
        ;


    }

    private boolean shouldPerformDataNotReceivedAction(String routeId) {
        if (!isLeader(routeId)) {
            return false;
        }
        String subscriptionId = subscriptionSetup.getSubscriptionId();
        if (subscriptionManager.isActiveSubscription(subscriptionId)) {
            DataNotReceivedAction dataNotReceivedAction = subscriptionSetup.getDataNotReceivedAction();
            if (dataNotReceivedAction != null) {
                boolean enabled = dataNotReceivedAction.isEnabled();

                boolean isReceiving = subscriptionManager.isSubscriptionReceivingData(subscriptionId,
                        dataNotReceivedAction.getInactivityMinutes() * 60);

                if (!isReceiving) {
                    Instant lastDataReceived = subscriptionManager.getLastDataReceived(subscriptionId);

                    if (lastDataReceived != null && lastDataReceived.isBefore(restartTriggered)) {
                        return false;
                    }

                    if (estimatedTimetables.getDatasetSize(subscriptionSetup.getDatasetId()) == 0) {
                        return false;
                    }

                    if (enabled) {
                        //Clear data
                        estimatedTimetables.clearAllByDatasetId(subscriptionSetup.getDatasetId());

                        restartTriggered = Instant.now();

                        logger.warn("Triggering DataNotReceivedAction: POST {} to {}", dataNotReceivedAction.getJsonPostContent(), dataNotReceivedAction.getEndpoint());
                    } else {
                        logger.info("Should have triggered DataNotReceivedAction, but it has been disabled");

                    }

                    return enabled & true;
                }
            }
        }
        return false;
    }

    private boolean shouldCheckStatus(String routeId) {
        if (!isLeader(routeId)) {
            return false;
        }
        boolean isActive = subscriptionManager.isActiveSubscription(subscriptionSetup.getSubscriptionId());
        boolean requiresCheckStatusRequest = subscriptionSetup.getUrlMap().get(RequestType.CHECK_STATUS) != null;
        boolean isTimeToCheckStatus = lastCheckStatus.isBefore(Instant.now().minus(subscriptionSetup.getHeartbeatInterval()));

        return isActive & requiresCheckStatusRequest & isTimeToCheckStatus;
    }

    private boolean shouldBeStarted(String routeId) {
        if (!isLeader(routeId)) {
            return false;
        }
        boolean isActive = subscriptionManager.isActiveSubscription(subscriptionSetup.getSubscriptionId());

        return (isActive & !hasBeenStarted);
    }

    private boolean shouldBeCancelled(String routeId) {
        if (!isLeader(routeId)) {
            return false;
        }

        if (subscriptionManager.isForceRestart(subscriptionSetup.getSubscriptionId())) {
            // If restart is triggered - ignore all other checks
            return true;
        }

        boolean isActive = subscriptionManager.isActiveSubscription(subscriptionSetup.getSubscriptionId());
        boolean isHealthy = subscriptionManager.isSubscriptionHealthy(subscriptionSetup.getSubscriptionId());

        return (hasBeenStarted & !isActive) || (hasBeenStarted & isActive & !isHealthy);
    }
}


@JacksonXmlRootElement
class ResponseToken {
    private String access_token;
    private String token_type;
    private long expires_in;

    public String getAccess_token() {
        return access_token;
    }

    public void setAccess_token(String access_token) {
        this.access_token = access_token;
    }

    public String getToken_type() {
        return token_type;
    }

    public void setToken_type(String token_type) {
        this.token_type = token_type;
    }

    public long getExpires_in() {
        return expires_in;
    }

    public void setExpires_in(long expires_in) {
        this.expires_in = expires_in;
    }
}
