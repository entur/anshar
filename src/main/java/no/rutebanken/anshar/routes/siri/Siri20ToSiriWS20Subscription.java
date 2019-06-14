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

import com.sun.xml.bind.marshaller.NamespacePrefixMapper;
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
import java.util.Map;

import static no.rutebanken.anshar.routes.HttpParameter.PARAM_RESPONSE_CODE;
import static no.rutebanken.anshar.routes.siri.helpers.SiriRequestFactory.getCamelUrl;
import static no.rutebanken.anshar.subscription.SubscriptionSetup.SubscriptionMode.FETCHED_DELIVERY;

public class Siri20ToSiriWS20Subscription extends SiriSubscriptionRouteBuilder {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private final SiriHandler handler;

    public Siri20ToSiriWS20Subscription(AnsharConfiguration config, SiriHandler handler, SubscriptionSetup subscriptionSetup, SubscriptionManager subscriptionManager) {
        super(config, subscriptionManager);
        this.handler = handler;
        this.subscriptionSetup = subscriptionSetup;

        this.customNamespacePrefixMapper = new NamespacePrefixMapper() {
            @Override
            public String getPreferredPrefix(String arg0, String arg1, boolean arg2) {
                return "siri";
            }
        };
    }


    @Override
    public void configure() throws Exception {

        Map<RequestType, String> urlMap = subscriptionSetup.getUrlMap();
        SiriRequestFactory helper = new SiriRequestFactory(subscriptionSetup);

        String endpointUrl = urlMap.get(RequestType.SUBSCRIBE);
        if (endpointUrl.startsWith("https4://")) {
            endpointUrl = endpointUrl.replaceFirst("https4", "https");
        } else {
            endpointUrl = "http://" + endpointUrl;
        }

        //Start subscription
        from("direct:" + subscriptionSetup.getStartSubscriptionRouteName())
                .log("Starting subscription " + subscriptionSetup.toString())
                .bean(helper, "createSiriSubscriptionRequest", false)
                .marshal(SiriDataFormatHelper.getSiriJaxbDataformat(customNamespacePrefixMapper))
                .setExchangePattern(ExchangePattern.InOut) // Make sure we wait for a response
                .setHeader("SOAPAction", constant("Subscribe"))
                .setHeader("operatorNamespace", constant(subscriptionSetup.getOperatorNamespace())) // Need to make SOAP request with endpoint specific element namespace
                .setHeader("endpointUrl", constant(endpointUrl)) // Need to make SOAP request with endpoint specific element namespace
                .setHeader("soapEnvelopeNamespace", constant(subscriptionSetup.getSoapenvNamespace())) // Need to make SOAP request with endpoint specific element namespace
                .to("xslt:xsl/siri_raw_soap.xsl") // Convert SIRI raw request to SOAP version
                .to("xslt:xsl/siri_14_20.xsl") // Convert SIRI raw request to SOAP version
                .removeHeaders("CamelHttp*") // Remove any incoming HTTP headers as they interfere with the outgoing definition
                .setHeader(Exchange.CONTENT_TYPE, constant(subscriptionSetup.getContentType())) // Necessary when talking to Microsoft web services
                .setHeader(Exchange.HTTP_METHOD, constant(HttpMethods.POST))
                .process(addCustomHeaders())
                .to("log:sent:" + getClass().getSimpleName() + "?showAll=true&multiline=true")
                .to(getCamelUrl(urlMap.get(RequestType.SUBSCRIBE), getTimeout()))
                .choice().when(simple("${in.body} != null"))
                    .to("log:received:" + getClass().getSimpleName() + "?showAll=true&multiline=true")
                    .to("xslt:xsl/siri_soap_raw.xsl?saxon=true&allowStAX=false&resultHandlerFactory=#streamResultHandlerFactory") // Extract SOAP version and convert to raw SIRI
                .end()
                .to("log:received:" + getClass().getSimpleName() + "?showAll=true&multiline=true")
                .process(p -> {
                    InputStream body = p.getIn().getBody(InputStream.class);
                    handler.handleIncomingSiri(subscriptionSetup.getSubscriptionId(), body);
                })
                .choice()
                .when(p -> subscriptionSetup.isDataSupplyRequestForInitialDelivery())
                    .log("Requesting DataSupplyRequest " + subscriptionSetup)
                    .to("direct:" + subscriptionSetup.getServiceRequestRouteName())
                .end()
                .routeId("start.ws.20.subscription."+subscriptionSetup.getVendor())
        ;

        //Check status-request checks the server status - NOT the subscription
        from("direct:" + subscriptionSetup.getCheckStatusRouteName())
                .bean(helper, "createSiriCheckStatusRequest", false)
                .marshal(SiriDataFormatHelper.getSiriJaxbDataformat(customNamespacePrefixMapper))
                .setExchangePattern(ExchangePattern.InOut) // Make sure we wait for a response
                .setHeader("SOAPAction", constant("CheckStatus"))
                .setHeader("operatorNamespace", constant(subscriptionSetup.getOperatorNamespace())) // Need to make SOAP request with endpoint specific element namespace
                .setHeader("endpointUrl", constant(endpointUrl)) // Need to make SOAP request with endpoint specific element namespace
                .setHeader("soapEnvelopeNamespace", constant(subscriptionSetup.getSoapenvNamespace())) // Need to make SOAP request with endpoint specific element namespace
                .to("xslt:xsl/siri_raw_soap.xsl") // Convert SIRI raw request to SOAP version
                .removeHeaders("CamelHttp*") // Remove any incoming HTTP headers as they interfere with the outgoing definition
                .setHeader(Exchange.CONTENT_TYPE, constant(subscriptionSetup.getContentType())) // Necessary when talking to Microsoft web services
                .setHeader(Exchange.HTTP_METHOD, constant(org.apache.camel.http.common.HttpMethods.POST))
                .process(addCustomHeaders())
                .to("log:cs:" + getClass().getSimpleName() + "?showAll=true&multiline=true")
                .to(getCamelUrl(urlMap.get(RequestType.CHECK_STATUS), getTimeout()))
                .choice().when(simple("${in.body} != null"))
                    .to("xslt:xsl/siri_soap_raw.xsl?saxon=true&allowStAX=false&resultHandlerFactory=#streamResultHandlerFactory") // Extract SOAP version and convert to raw SIRI
                .end()
                .process(p -> {

                    String responseCode = p.getIn().getHeader(PARAM_RESPONSE_CODE, String.class);
                    if ("200" .equals(responseCode)) {
                        InputStream body = p.getIn().getBody(InputStream.class);
                        if (body != null && body.available() > 0) {
                            handler.handleIncomingSiri(subscriptionSetup.getSubscriptionId(), body);
                        }
                    } else {
                        logger.info("CheckStatus NOT OK - Remote service is down [{}]", subscriptionSetup.buildUrl());
                    }

                    if (subscriptionSetup.getSubscriptionMode().equals(FETCHED_DELIVERY) &&
                            !subscriptionManager.isSubscriptionReceivingData(subscriptionSetup.getSubscriptionId(),
                                    subscriptionSetup.getHeartbeatInterval().toMillis()/1000)) {
                        logger.info("No data received since last CheckStatusRequest - triggering DataSupplyRequest.");
                        p.getOut().setHeader("routename", subscriptionSetup.getServiceRequestRouteName());
                    }


                })
                .choice()
                    .when(header("routename").isNotNull())
                        .toD("direct:${header.routename}")
                    .endChoice()
                .routeId("check.status.rs.20.subscription."+subscriptionSetup.getVendor())
        ;

        //Cancel subscription
        from("direct:" + subscriptionSetup.getCancelSubscriptionRouteName())
                .log("Cancelling subscription " + subscriptionSetup.toString())
                .bean(helper, "createSiriTerminateSubscriptionRequest", false)
                .marshal(SiriDataFormatHelper.getSiriJaxbDataformat(customNamespacePrefixMapper))
                .setExchangePattern(ExchangePattern.InOut) // Make sure we wait for a response
                .setProperty(Exchange.LOG_DEBUG_BODY_STREAMS, constant("true"))
                .setHeader("SOAPAction", constant("DeleteSubscription")) // set SOAPAction Header (Microsoft requirement)
                .setHeader("operatorNamespace", constant(subscriptionSetup.getOperatorNamespace())) // Need to make SOAP request with endpoint specific element namespace
                .setHeader("endpointUrl", constant(endpointUrl)) // Need to make SOAP request with endpoint specific element namespace
                .to("xslt:xsl/siri_raw_soap.xsl") // Convert SIRI raw request to SOAP version
                .to("xslt:xsl/siri_14_20.xsl") // Convert SIRI raw request to SOAP version
                .removeHeaders("CamelHttp*") // Remove any incoming HTTP headers as they interfere with the outgoing definition
                .setHeader(Exchange.CONTENT_TYPE, constant(subscriptionSetup.getContentType())) // Necessary when talking to Microsoft web services
                .setHeader(Exchange.HTTP_METHOD, constant(HttpMethods.POST))
                .process(addCustomHeaders())
                .to("log:sent:" + getClass().getSimpleName() + "?showAll=true&multiline=true")
                .to(getCamelUrl(urlMap.get(RequestType.DELETE_SUBSCRIPTION), getTimeout()))
                .choice().when(simple("${in.body} != null"))
                    .to("xslt:xsl/siri_soap_raw.xsl?saxon=true&allowStAX=false&resultHandlerFactory=#streamResultHandlerFactory") // Extract SOAP version and convert to raw SIRI
                .end()
                .to("log:received:" + getClass().getSimpleName() + "?showAll=true&multiline=true")
                .process(p -> {
                    InputStream body = p.getIn().getBody(InputStream.class);
                    if (body != null && body.available() > 0) {
                        handler.handleIncomingSiri(subscriptionSetup.getSubscriptionId(), body);
                    }
                })
                .routeId("cancel.ws.20.subscription."+subscriptionSetup.getVendor())
        ;

        initTriggerRoutes();
    }

}
