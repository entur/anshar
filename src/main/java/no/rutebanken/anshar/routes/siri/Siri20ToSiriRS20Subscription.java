package no.rutebanken.anshar.routes.siri;

import no.rutebanken.anshar.routes.siri.handlers.SiriHandler;
import no.rutebanken.anshar.subscription.RequestType;
import no.rutebanken.anshar.subscription.SubscriptionManager;
import no.rutebanken.anshar.subscription.SubscriptionSetup;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.builder.xml.Namespaces;
import org.apache.camel.http.common.HttpMethods;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.ZonedDateTime;
import java.util.Map;

import static no.rutebanken.anshar.routes.siri.RouteHelper.getCamelUrl;

public class Siri20ToSiriRS20Subscription extends SiriSubscriptionRouteBuilder {

    private Logger logger = LoggerFactory.getLogger(Siri20ToSiriRS20Subscription.class);

    private SiriHandler handler;

    public Siri20ToSiriRS20Subscription(SiriHandler handler, SubscriptionSetup subscriptionSetup, SubscriptionManager subscriptionManager) {
        super(subscriptionManager);
        this.handler = handler;
        this.subscriptionSetup = subscriptionSetup;
    }

    @Override
    public void configure() throws Exception {

        Map<RequestType, String> urlMap = subscriptionSetup.getUrlMap();

        Namespaces ns = new Namespaces("siri", "http://www.siri.org.uk/siri")
                .add("xsd", "http://www.w3.org/2001/XMLSchema");

        RouteHelper helper = new RouteHelper(subscriptionSetup, customNamespacePrefixMapper);

        //Start subscription
        from("direct:" + subscriptionSetup.getStartSubscriptionRouteName())
                .process(p -> {
                    System.err.println("Starting " + subscriptionSetup.getStartSubscriptionRouteName() + " " + ZonedDateTime.now());
                })
                .bean(helper, "marshalSiriSubscriptionRequest", false)
                .setExchangePattern(ExchangePattern.InOut) // Make sure we wait for a response
                .setHeader("operatorNamespace", constant(subscriptionSetup.getOperatorNamespace())) // Need to make SOAP request with endpoint specific element namespace
                .to("log:sent request:" + getClass().getSimpleName() + "?showAll=true&multiline=true")
                .removeHeaders("CamelHttp*") // Remove any incoming HTTP headers as they interfere with the outgoing definition
                .setHeader(Exchange.CONTENT_TYPE, constant("text/xml;charset=UTF-8")) // Necessary when talking to Microsoft web services
                .setHeader(Exchange.HTTP_METHOD, constant(HttpMethods.POST))
                .to(getCamelUrl(urlMap.get(RequestType.SUBSCRIBE)) + getTimeout())
                .to("log:received response:" + getClass().getSimpleName() + "?showAll=true&multiline=true")
                .process(p -> {

                    String responseCode = p.getIn().getHeader("CamelHttpResponseCode", String.class);
                    if ("200".equals(responseCode)) {
                        logger.info("SubscriptionResponse OK - Async response performs actual registration");
                        subscriptionManager.activatePendingSubscription(subscriptionSetup.getSubscriptionId());
                    }
                })
        ;

        //Check status-request checks the server status - NOT the subscription
        from("direct:" + subscriptionSetup.getCheckStatusRouteName())
                .bean(helper, "marshalSiriCheckStatusRequest", false)
                .removeHeaders("CamelHttp*") // Remove any incoming HTTP headers as they interfere with the outgoing definition
                .setHeader(Exchange.CONTENT_TYPE, constant("text/xml;charset=UTF-8")) // Necessary when talking to Microsoft web services
                .setHeader(Exchange.HTTP_METHOD, constant(HttpMethods.POST))
                .to(getCamelUrl(urlMap.get(RequestType.CHECK_STATUS)) + getTimeout())
                .process(p -> {

                    String responseCode = p.getIn().getHeader("CamelHttpResponseCode", String.class);
                    if ("200" .equals(responseCode)) {
                        logger.trace("CheckStatus OK - Remote service is up [{}]", subscriptionSetup.buildUrl());
                        handler.handleIncomingSiri(subscriptionSetup.getSubscriptionId(), p.getIn().getBody(String.class));
                    } else {
                        logger.info("CheckStatus NOT OK - Remote service is down [{}]", subscriptionSetup.buildUrl());
                    }

                })
        ;

        //Cancel subscription
        from("direct:" + subscriptionSetup.getCancelSubscriptionRouteName())
                .bean(helper, "marshalSiriTerminateSubscriptionRequest", false)
                .process(p -> {
                    System.err.println("Cancelling " + subscriptionSetup.getStartSubscriptionRouteName() + " " + ZonedDateTime.now());
                })
                .setExchangePattern(ExchangePattern.InOut) // Make sure we wait for a response
                .setProperty(Exchange.LOG_DEBUG_BODY_STREAMS, constant("true"))
                .removeHeaders("CamelHttp*") // Remove any incoming HTTP headers as they interfere with the outgoing definition
                .setHeader(Exchange.CONTENT_TYPE, constant("text/xml;charset=UTF-8")) // Necessary when talking to Microsoft web services
                .setHeader(Exchange.HTTP_METHOD, constant(HttpMethods.POST))
                .to(getCamelUrl(urlMap.get(RequestType.DELETE_SUBSCRIPTION)) + getTimeout())
                .to("log:received response:" + getClass().getSimpleName() + "?showAll=true&multiline=true")
                .process(p -> {
                    String body = p.getIn().getBody(String.class);
                    logger.info("Response body [{}]", body);
                    if (body != null && !body.isEmpty()) {
                        handler.handleIncomingSiri(subscriptionSetup.getSubscriptionId(), body);
                        subscriptionManager.removeSubscription(subscriptionSetup.getSubscriptionId());
                    }
                });

        initTriggerRoutes();
    }

}
