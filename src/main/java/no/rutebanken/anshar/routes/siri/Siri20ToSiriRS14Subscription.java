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
import no.rutebanken.anshar.subscription.SubscriptionManager;
import no.rutebanken.anshar.subscription.SubscriptionSetup;
import no.rutebanken.anshar.subscription.helpers.RequestType;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.component.http4.HttpMethods;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.net.ConnectException;
import java.util.Map;

import static no.rutebanken.anshar.routes.HttpParameter.PARAM_RESPONSE_CODE;
import static no.rutebanken.anshar.routes.siri.helpers.SiriRequestFactory.getCamelUrl;

public class Siri20ToSiriRS14Subscription extends SiriSubscriptionRouteBuilder {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private final SiriHandler handler;

    public Siri20ToSiriRS14Subscription(AnsharConfiguration config, SiriHandler handler, SubscriptionSetup subscriptionSetup, SubscriptionManager subscriptionManager) {
        super(config, subscriptionManager);
        this.handler = handler;
        this.subscriptionSetup = subscriptionSetup;
    }


    @Override
    public void configure() throws Exception {
    	
    	
        Map<RequestType, String> urlMap = subscriptionSetup.getUrlMap();

        SiriRequestFactory helper = new SiriRequestFactory(subscriptionSetup);

        //Start subscription
        from("direct:" + subscriptionSetup.getStartSubscriptionRouteName())
                .log("Starting subscription " + subscriptionSetup.toString())
                .bean(helper, "createSiriSubscriptionRequest", false)
                .marshal(SiriDataFormatHelper.getSiriJaxbDataformat())
                .setExchangePattern(ExchangePattern.InOut) // Make sure we wait for a response
                .setHeader("SOAPAction", constant("Subscribe"))
                .setHeader("operatorNamespace", constant(subscriptionSetup.getOperatorNamespace())) // Need to make SOAP request with endpoint specific element namespace
                .to("xslt:xsl/siri_20_14.xsl") // Convert from SIRI 2.0 to SIRI 1.4
                .removeHeaders("CamelHttp*") // Remove any incoming HTTP headers as they interfere with the outgoing definition
                .setHeader(Exchange.CONTENT_TYPE, constant(subscriptionSetup.getContentType())) // Necessary when talking to Microsoft web services
                .setHeader(Exchange.HTTP_METHOD, constant(HttpMethods.POST))
                .process(addCustomHeaders())
                .to("log:sent:" + getClass().getSimpleName() + "?showAll=true&multiline=true")
                .doTry()
                    .to(getCamelUrl(urlMap.get(RequestType.SUBSCRIBE), getTimeout()))
                    .to("log:received response:" + getClass().getSimpleName() + "?showAll=true&multiline=true")
                .doCatch(ConnectException.class)
                    .log("Caught ConnectException - subscription not started - will try again: "+ subscriptionSetup.toString())
                    .process(p -> p.getOut().setBody(null))
                .endDoTry()
                .process(p -> {

                    String responseCode = p.getIn().getHeader(PARAM_RESPONSE_CODE, String.class);
                    if ("200".equals(responseCode)) {
                        logger.info("SubscriptionResponse OK {}", subscriptionSetup);
                    }

                    InputStream body = p.getIn().getBody(InputStream.class);

                    if (body != null && body.available() > 0) {
                        handler.handleIncomingSiri(subscriptionSetup.getSubscriptionId(), body);
                    }
                })
                .routeId("start.rs.14.subscription."+subscriptionSetup.getVendor())
        ;

        //Cancel subscription
        from("direct:" + subscriptionSetup.getCancelSubscriptionRouteName())
                .log("Cancelling subscription " + subscriptionSetup.toString())
                .bean(helper, "createSiriTerminateSubscriptionRequest", false)
                .marshal(SiriDataFormatHelper.getSiriJaxbDataformat())
                .setExchangePattern(ExchangePattern.InOut) // Make sure we wait for a response
                .setProperty(Exchange.LOG_DEBUG_BODY_STREAMS, constant("true"))
                .setHeader("SOAPAction", constant("DeleteSubscription")) // set SOAPAction Header (Microsoft requirement)
                .setHeader("operatorNamespace", constant(subscriptionSetup.getOperatorNamespace())) // Need to make SOAP request with endpoint specific element namespace
                .to("xslt:xsl/siri_20_14.xsl") // Convert from SIRI 2.0 to SIRI 1.4
                .to("log:sent:" + getClass().getSimpleName() + "?showAll=true&multiline=true")
                .removeHeaders("CamelHttp*") // Remove any incoming HTTP headers as they interfere with the outgoing definition
                .setHeader(Exchange.CONTENT_TYPE, constant(subscriptionSetup.getContentType())) // Necessary when talking to Microsoft web services
                .setHeader(Exchange.HTTP_METHOD, constant(org.apache.camel.component.http4.HttpMethods.POST))
                .process(addCustomHeaders())
                .to(getCamelUrl(urlMap.get(RequestType.DELETE_SUBSCRIPTION), getTimeout()))
                .to("log:received:" + getClass().getSimpleName() + "?showAll=true&multiline=true")
                .process(p -> {
                    InputStream body = p.getIn().getBody(InputStream.class);
                    logger.info("Response body [{}]", body);
                    if (body != null && body.available() > 0) {
                        handler.handleIncomingSiri(subscriptionSetup.getSubscriptionId(), body);
                    }
                })
                .routeId("cancel.rs.14.subscription."+subscriptionSetup.getVendor())
        ;
        initTriggerRoutes();
    }

}
