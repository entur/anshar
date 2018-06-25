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

import no.rutebanken.anshar.config.AnsharConfiguration;
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

@Service
@Configuration
public class AdministrationRoute extends RestRouteBuilder {
    private Logger logger = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private AnsharConfiguration configuration;

    @Autowired
    private ExtendedHazelcastService extendedHazelcastService;

    @Autowired
    private SubscriptionManager subscriptionManager;

    @Autowired
    private ServerSubscriptionManager serverSubscriptionManager;

    @Autowired
    private HealthManager healthManager;

    @Override
    public void configure() throws Exception {
        super.configure();

        rest("/anshar").tag("internal.admin")
                .get("/stats").produces("text/html").to("direct:stats")
                .put("/stop").to("direct:stop")
                .put("/start").to("direct:start")
                .put("/terminate").to("direct:terminate.outbound.subscription")
                .get("/subscriptions").produces("text/html").to("direct:subscriptions")
                .get("/clusterstats").produces("application/json").to("direct:clusterstats")
                .get("/unmapped").produces("text/html").to("direct:unmapped")
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
        from("direct:subscriptions")
                .bean(serverSubscriptionManager, "getSubscriptionsAsJson")
                .to("freemarker:templates/subscriptions.ftl")
                .routeId("admin.subscriptions")
        ;

        //Return subscription status
        from("direct:terminate.outbound.subscription")
                .bean(serverSubscriptionManager, "terminateSubscription(${header.subscriptionId})")
                .routeId("admin.terminate.subscription")
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
