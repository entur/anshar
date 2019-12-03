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

package no.rutebanken.anshar.routes.admin;

import no.rutebanken.anshar.data.collections.ExtendedHazelcastService;
import no.rutebanken.anshar.routes.RestRouteBuilder;
import no.rutebanken.anshar.routes.health.HealthManager;
import no.rutebanken.anshar.routes.outbound.ServerSubscriptionManager;
import no.rutebanken.anshar.subscription.SubscriptionManager;
import org.json.simple.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Service;

import javax.ws.rs.core.MediaType;

@SuppressWarnings("unchecked")
@Service
@Configuration
public class AdministrationRoute extends RestRouteBuilder {
    private Logger logger = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private ExtendedHazelcastService extendedHazelcastService;

    @Autowired
    private SubscriptionManager subscriptionManager;

    @Autowired
    private ServerSubscriptionManager serverSubscriptionManager;

    @Autowired
    private HealthManager healthManager;

    @Autowired
    private AdminRouteHelper helper;

    @Override
    public void configure() throws Exception {
        super.configure();

        rest("/anshar").tag("internal.admin")
                .get("/stats").produces(MediaType.TEXT_HTML).to("direct:stats")
                .put("/stats").to("direct:operation")
                .get("/clusterstats").produces(MediaType.APPLICATION_JSON).to("direct:clusterstats")
                .get("/unmapped").produces(MediaType.TEXT_HTML).to("direct:unmapped")
                .get("/unmapped/{datasetId}").produces(MediaType.TEXT_HTML).to("direct:unmapped")
        ;

        //Return subscription status
        from("direct:stats")
                .process(p -> {
                    JSONObject stats = subscriptionManager.buildStats();

                    stats.put("outbound", serverSubscriptionManager.getSubscriptionsAsJson());
                    p.getOut().setBody(stats);
                })
                .to("freemarker:templates/stats.ftl")
                .routeId("admin.stats")
        ;

        //Stop subscription
        from("direct:operation")
             .choice()
                .when(header("operation").isEqualTo("stop"))
                    .to("direct:stop")
                .endChoice()
                .when(header("operation").isEqualTo("start"))
                    .to("direct:start")
                .endChoice()
                .when(header("operation").isEqualTo("terminate"))
                    .to("direct:terminate.outbound.subscription")
                .endChoice()
                .when(header("operation").isEqualTo("terminateAll"))
                    .to("direct:terminate.all.subscriptions")
                .endChoice()
                .when(header("operation").isEqualTo("startAll"))
                    .to("direct:restart.all.subscriptions")
                .endChoice()
                .when(header("operation").isEqualTo("flush"))
                    .to("direct:flush.data.from.subscription")
                .endChoice()
            .end()
        ;

        from("direct:stop")
                .bean(subscriptionManager, "stopSubscription(${header.subscriptionId})")
                .routeId("admin.stop")
        ;

        //Start subscription
        from("direct:start")
                .bean(subscriptionManager, "startSubscription(${header.subscriptionId})")
                .routeId("admin.start")
        ;

        //Return subscription status
        from("direct:terminate.outbound.subscription")
                .bean(serverSubscriptionManager, "terminateSubscription(${header.subscriptionId})")
                .routeId("admin.terminate.subscription")
        ;

        //Return subscription status
        from("direct:terminate.all.subscriptions")
                .bean(subscriptionManager, "terminateAllSubscriptions()")
                .routeId("admin.terminate.all.subscriptions")
        ;


        //Return subscription status
        from("direct:restart.all.subscriptions")
                .bean(subscriptionManager, "triggerRestartAllActiveSubscriptions()")
                .routeId("admin.start.all.subscriptions")
        ;

        //Return subscription status
        from("direct:flush.data.from.subscription")
                .bean(helper, "flushDataFromSubscription(${header.subscriptionId})")
                .routeId("admin.flush.data")
        ;

        //Return cluster status
        from("direct:clusterstats")
                .bean(extendedHazelcastService, "listNodes(${header.stats})")
                .routeId("admin.clusterstats")
        ;

        //Return unmapped ids
        from("direct:unmapped")
                .filter(header("datasetId").isNotNull())
                .bean(healthManager, "getUnmappedIdsAsJson(${header.datasetId})")
                .to("freemarker:templates/unmapped.ftl")
                .routeId("admin.unmapped")
        ;

    }
}
