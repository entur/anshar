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
import no.rutebanken.anshar.routes.dataformat.SiriDataFormatHelper;
import no.rutebanken.anshar.routes.siri.handlers.SiriHandler;
import no.rutebanken.anshar.routes.siri.helpers.SiriRequestFactory;
import no.rutebanken.anshar.subscription.OAuthConfigElement;
import no.rutebanken.anshar.subscription.SubscriptionManager;
import no.rutebanken.anshar.subscription.SubscriptionSetup;
import no.rutebanken.anshar.subscription.helpers.RequestType;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Processor;
import org.apache.camel.http.common.HttpMethods;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.net.ConnectException;
import java.util.HashMap;
import java.util.Map;

import static no.rutebanken.anshar.routes.HttpParameter.PARAM_RESPONSE_CODE;
import static no.rutebanken.anshar.routes.siri.helpers.SiriRequestFactory.getCamelUrl;
import static no.rutebanken.anshar.subscription.SubscriptionSetup.SubscriptionMode.FETCHED_DELIVERY;

public class Siri20ToSiriRS20Subscription extends SiriSubscriptionRouteBuilder {

    private final Logger logger = LoggerFactory.getLogger(Siri20ToSiriRS20Subscription.class);

    private final SiriHandler handler;

    public Siri20ToSiriRS20Subscription(AnsharConfiguration config, SiriHandler handler, SubscriptionSetup subscriptionSetup, SubscriptionManager subscriptionManager) {
        super(config, subscriptionManager);
        this.handler = handler;
        this.subscriptionSetup = subscriptionSetup;
    }

    @Override
    public void configure() throws Exception {

        Map<RequestType, String> urlMap = subscriptionSetup.getUrlMap();
        SiriRequestFactory helper = new SiriRequestFactory(subscriptionSetup);

        Map<OAuthConfigElement, String> oauthHeaders = new HashMap<>();
        if (subscriptionSetup.getOauth2Config() != null) {
            oauthHeaders.put(OAuthConfigElement.CLIENT_ID, subscriptionSetup.getOauth2Config().get(OAuthConfigElement.CLIENT_ID));
            oauthHeaders.put(OAuthConfigElement.CLIENT_SECRET, subscriptionSetup.getOauth2Config().get(OAuthConfigElement.CLIENT_SECRET));
            oauthHeaders.put(OAuthConfigElement.GRANT_TYPE, subscriptionSetup.getOauth2Config().get(OAuthConfigElement.GRANT_TYPE));
            oauthHeaders.put(OAuthConfigElement.SERVER, subscriptionSetup.getOauth2Config().get(OAuthConfigElement.SERVER));
            oauthHeaders.put(OAuthConfigElement.AUDIENCE, subscriptionSetup.getOauth2Config().get(OAuthConfigElement.AUDIENCE));
        }

        //Start subscription
        Processor oauthHeadersProcess = exchange -> {
            if (!oauthHeaders.isEmpty()) {
                logger.info("Configuring OAuth for subscription {}", subscriptionSetup);
                exchange.getMessage().setHeader("oauth-client-id", oauthHeaders.get(OAuthConfigElement.CLIENT_ID));
                exchange.getMessage().setHeader("oauth-client-secret", oauthHeaders.get(OAuthConfigElement.CLIENT_SECRET));
                exchange.getMessage().setHeader("oauth-grant-type", oauthHeaders.get(OAuthConfigElement.GRANT_TYPE));
                exchange.getMessage().setHeader("oauth-server", oauthHeaders.get(OAuthConfigElement.SERVER));
                exchange.getMessage().setHeader("oauth-audience", oauthHeaders.get(OAuthConfigElement.AUDIENCE));
            }
        };

        from("direct:" + subscriptionSetup.getStartSubscriptionRouteName())
                .log("Starting subscription " + subscriptionSetup.toString())
                .process(oauthHeadersProcess)
                .to("direct:oauth2.authorize")
                .bean(helper, "createSiriSubscriptionRequest")
                .marshal(SiriDataFormatHelper.getSiriJaxbDataformat())
                .setExchangePattern(ExchangePattern.InOut) // Make sure we wait for a response
                .setHeader("operatorNamespace", constant(subscriptionSetup.getOperatorNamespace())) // Need to make SOAP request with endpoint specific element namespace
                .removeHeaders("CamelHttp*") // Remove any incoming HTTP headers as they interfere with the outgoing definition
                .setHeader(Exchange.CONTENT_TYPE, constant(subscriptionSetup.getContentType())) // Necessary when talking to Microsoft web services
                .setHeader(Exchange.HTTP_METHOD, constant(HttpMethods.POST))
                .process(addCustomHeaders())
                .to("log:sent request:" + getClass().getSimpleName() + "?showAll=true&multiline=true")
                .doTry()
                    .to(getCamelUrl(urlMap.get(RequestType.SUBSCRIBE), getTimeout()))
                    .to("log:received response:" + getClass().getSimpleName() + "?showAll=true&multiline=true")
                    .process(p -> {

                        String responseCode = p.getIn().getHeader(PARAM_RESPONSE_CODE, String.class);
                        InputStream body = p.getIn().getBody(InputStream.class);
                        if (body != null && body.available() > 0) {
                            handler.handleIncomingSiri(subscriptionSetup.getSubscriptionId(), body);
                        } else if ("200".equals(responseCode) | "201".equals(responseCode)) {
                            logger.info("SubscriptionResponse OK - Async response performs actual registration");
                            subscriptionManager.activatePendingSubscription(subscriptionSetup.getSubscriptionId());
                        } else {
                            hasBeenStarted = false;
                        }

                    })
                .doCatch(ConnectException.class)
                    .log("Caught ConnectException - subscription not started - will try again: "+ subscriptionSetup.toString())
                    .process(p -> {
                        p.getOut().setBody(null);
                    })
                .endDoTry()
                .routeId("start.rs.20.subscription."+subscriptionSetup.getVendor())
        ;

        if (urlMap.get(RequestType.CHECK_STATUS) != null) {
            //Check status-request checks the server status - NOT the subscription
            from("direct:" + subscriptionSetup.getCheckStatusRouteName())
                .process(oauthHeadersProcess)
                .to("direct:oauth2.authorize")
                .bean(helper, "createSiriCheckStatusRequest")
                .marshal(SiriDataFormatHelper.getSiriJaxbDataformat())
                .removeHeaders("CamelHttp*") // Remove any incoming HTTP headers as they interfere with the outgoing definition
                .setHeader(Exchange.CONTENT_TYPE, constant(subscriptionSetup.getContentType())) // Necessary when talking to Microsoft web services
                .setHeader(Exchange.HTTP_METHOD, constant(HttpMethods.POST))
                .process(addCustomHeaders())
                .to(getCamelUrl(urlMap.get(RequestType.CHECK_STATUS), getTimeout()))
                .process(p -> {

                    String responseCode = p.getIn().getHeader(PARAM_RESPONSE_CODE, String.class);
                    if ("200".equals(responseCode)) {
                        logger.trace("CheckStatus OK - Remote service is up [{}]",
                            subscriptionSetup.buildUrl()
                        );
                        InputStream body = p.getIn().getBody(InputStream.class);
                        if (body != null && body.available() > 0) {
                            handler.handleIncomingSiri(subscriptionSetup.getSubscriptionId(), body);
                        }
                    }
                    else {
                        logger.info("CheckStatus NOT OK - Remote service is down [{}]",
                            subscriptionSetup.buildUrl()
                        );
                    }

                    if (subscriptionSetup.getSubscriptionMode().equals(FETCHED_DELIVERY) && !subscriptionManager.isSubscriptionReceivingData(subscriptionSetup.getSubscriptionId(),
                        subscriptionSetup.getHeartbeatInterval().toMillis() / 1000
                    )) {
                        logger.info(
                            "No data received since last CheckStatusRequest - triggering DataSupplyRequest.");
                        p.getOut().setHeader("routename", subscriptionSetup.getServiceRequestRouteName());
                    }


                })
                .choice()
                .when(header("routename").isNotNull())
                .toD("direct:${header.routename}")
                .endChoice()
                .routeId("check.status.rs.20.subscription." + subscriptionSetup.getVendor());
        }

        //Cancel subscription
        from("direct:" + subscriptionSetup.getCancelSubscriptionRouteName())
                .log("Cancelling subscription " + subscriptionSetup.toString())
                .process(oauthHeadersProcess)
                .to("direct:oauth2.authorize")
                .bean(helper, "createSiriTerminateSubscriptionRequest")
                .marshal(SiriDataFormatHelper.getSiriJaxbDataformat())
                .setExchangePattern(ExchangePattern.InOut) // Make sure we wait for a response
                .setProperty(Exchange.LOG_DEBUG_BODY_STREAMS, constant("true"))
                .removeHeaders("CamelHttp*") // Remove any incoming HTTP headers as they interfere with the outgoing definition
                .setHeader(Exchange.CONTENT_TYPE, constant(subscriptionSetup.getContentType())) // Necessary when talking to Microsoft web services
                .setHeader(Exchange.HTTP_METHOD, constant(HttpMethods.POST))
                .process(addCustomHeaders())
                .to("log:sent request:" + getClass().getSimpleName() + "?showAll=true&multiline=true")
                .to(getCamelUrl(urlMap.get(RequestType.DELETE_SUBSCRIPTION), getTimeout()))
                .to("log:received response:" + getClass().getSimpleName() + "?showAll=true&multiline=true")
                .process(p -> {
                    InputStream body = p.getIn().getBody(InputStream.class);
                    if (body != null && body.available() >0) {
                        handler.handleIncomingSiri(subscriptionSetup.getSubscriptionId(), body);
                    }
                })
                .routeId("cancel.rs.20.subscription."+subscriptionSetup.getVendor())
        ;

        initTriggerRoutes();
    }

}
