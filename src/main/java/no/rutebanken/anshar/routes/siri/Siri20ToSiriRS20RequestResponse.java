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
import no.rutebanken.anshar.routes.siri.helpers.SiriRequestFactory;
import no.rutebanken.anshar.subscription.SubscriptionManager;
import no.rutebanken.anshar.subscription.SubscriptionSetup;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.MessageHistory;
import org.apache.camel.component.http.HttpMethods;

import java.util.List;

import static no.rutebanken.anshar.routes.HttpParameter.INTERNAL_SIRI_DATA_TYPE;
import static no.rutebanken.anshar.routes.HttpParameter.PARAM_SUBSCRIPTION_ID;

public class Siri20ToSiriRS20RequestResponse extends SiriSubscriptionRouteBuilder {

    public Siri20ToSiriRS20RequestResponse(AnsharConfiguration config, SubscriptionSetup subscriptionSetup, SubscriptionManager subscriptionManager) {
        super(config, subscriptionManager);
        this.subscriptionSetup = subscriptionSetup;
    }

    @Override
    public void configure() throws Exception {

        long heartbeatIntervalMillis = subscriptionSetup.getHeartbeatInterval().toMillis();

        SiriRequestFactory helper = new SiriRequestFactory(subscriptionSetup);

        String httpOptions = getTimeout();

        String monitoringRouteId = "monitor.rs.20." + subscriptionSetup.getSubscriptionType() + "." + subscriptionSetup.getVendor();
        boolean releaseLeadershipOnError;
        if (subscriptionSetup.getSubscriptionMode() == SubscriptionSetup.SubscriptionMode.REQUEST_RESPONSE |
                subscriptionSetup.getSubscriptionMode() == SubscriptionSetup.SubscriptionMode.POLLING_FETCHED_DELIVERY) {
            releaseLeadershipOnError = true;
            singletonFrom("quartz://anshar/monitor_" + subscriptionSetup.getRequestResponseRouteName() + "?trigger.repeatInterval=" + heartbeatIntervalMillis,
                    monitoringRouteId)
                    .choice()
                    .when(p -> requestData(subscriptionSetup.getSubscriptionId(), p.getFromRouteId()))
                    .to("direct:" + subscriptionSetup.getServiceRequestRouteName())
                    .endChoice()
            ;
        } else {
            releaseLeadershipOnError = false;
        }

        String routeId = "request.rs.20." + subscriptionSetup.getSubscriptionType() + "." + subscriptionSetup.getVendor();
        from("direct:" + subscriptionSetup.getServiceRequestRouteName())
            .messageHistory()
            .process(p -> requestStarted())
            .log("Retrieving data " + subscriptionSetup.toString())
            .bean(helper, "createSiriDataRequest")
            .marshal(SiriDataFormatHelper.getSiriJaxbDataformat())
            .setExchangePattern(ExchangePattern.InOut) // Make sure we wait for a response
            .removeHeaders("CamelHttp*") // Remove any incoming HTTP headers as they interfere with the outgoing definition
            .setHeader(Exchange.CONTENT_TYPE, constant(subscriptionSetup.getContentType())) // Necessary when talking to Microsoft web services
            .setHeader(Exchange.HTTP_METHOD, constant(HttpMethods.POST))
            .process(addCustomHeaders())
            .to("log:request:" + getClass().getSimpleName() + "?showAll=true&multiline=true")
            .doTry()
                .to(getRequestUrl(subscriptionSetup, httpOptions))
                .setHeader("CamelHttpPath", constant("/appContext" + subscriptionSetup.buildUrl(false)))
                .log("Got response " + subscriptionSetup.toString())
                .setHeader(PARAM_SUBSCRIPTION_ID, simple(subscriptionSetup.getSubscriptionId()))
                .setHeader(INTERNAL_SIRI_DATA_TYPE, simple(subscriptionSetup.getSubscriptionType().name()))
                .to("direct:enqueue.message")
            .doCatch(Exception.class)
                .log("Caught exception -" + (releaseLeadershipOnError ? "":" NOT") + " releasing leadership: " + subscriptionSetup.toString())
                .process(p -> {
                    if (releaseLeadershipOnError) {
                        releaseLeadership(monitoringRouteId);
                    }
                })
            .doFinally()
                .process(p -> {
                    requestFinished();
                    List<MessageHistory> list = p.getProperty(Exchange.MESSAGE_HISTORY, List.class);
                    long elapsed = 0;
                    for (MessageHistory history : list) {
                        if (history.getRouteId().equals(routeId)) {
                            elapsed += history.getElapsed();
                        }
                    }
                    log.info("Processing data took {} ms.", elapsed);
                    if (elapsed > heartbeatIntervalMillis) {
                        log.info("Processing took longer than {} ms - releasing leadership", heartbeatIntervalMillis);
                        if (releaseLeadershipOnError) {
                            releaseLeadership(monitoringRouteId);
                        }
                    }
                })
            .endDoTry()
            .routeId(routeId)
        ;
    }

}
