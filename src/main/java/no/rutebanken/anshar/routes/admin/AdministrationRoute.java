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
import org.apache.commons.lang3.StringUtils;
import org.json.simple.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Service;

import javax.ws.rs.core.MediaType;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import static no.rutebanken.anshar.routes.policy.SingletonRoutePolicyFactory.DEFAULT_LOCK_VALUE;

@SuppressWarnings("unchecked")
@Service
@Configuration
public class AdministrationRoute extends RestRouteBuilder {

    private static final String STATS_ROUTE = "direct:stats";
    private static final String OPERATION_ROUTE = "direct:operation";
    private static final String CLUSTERSTATS_ROUTE = "direct:clusterstats";
    private static final String UNMAPPED_ROUTE = "direct:unmapped";

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

    @Value("${anshar.route.singleton.policy.automatic.verification:false}")
    private boolean autoLockVerificationEnabled;

    @Override
    public void configure() throws Exception {
        super.configure();


        rest("/").tag("internal.admin.root")
                .get("").produces(MediaType.TEXT_HTML).to(STATS_ROUTE)
                .put("").to(OPERATION_ROUTE)
                .get("/locks").to("direct:locks")
        ;

        rest("/anshar").tag("internal.admin")
                .get("/stats").produces(MediaType.TEXT_HTML).to(STATS_ROUTE)
                .put("/stats").to(OPERATION_ROUTE)
                .get("/unmapped").produces(MediaType.TEXT_HTML).to(UNMAPPED_ROUTE)
                .get("/unmapped/{datasetId}").produces(MediaType.TEXT_HTML).to(UNMAPPED_ROUTE)
        ;

        long verificationIntervalMillis = 10 * 60 * 1000;
        // fireNow=false  : allow all instances to start completely before checking during redeploy
        // repeatInterval : Use repeat interval to check every 10 minutes after startup - not every 10 minutes on clock
        from("quartz://anshar.verify.locks?fireNow=false&trigger.repeatInterval=" + verificationIntervalMillis)
            .log("Verifying locks - start")
            .process(p -> {
                final Map<String, String> locksMap = helper.getAllLocks();
                for (Map.Entry<String, String> lockEntries : locksMap.entrySet()) {
                    final String hostName = lockEntries.getValue();
                    boolean unlock = false;
                    if (!hostName.equals(DEFAULT_LOCK_VALUE)) {
                        try {
                            final InetAddress host = InetAddress.getByName(hostName);
                            if (!host.isReachable(5000)) {
                                unlock = true;
                                log.info("Host [{}] unreachable.", hostName);
                            }
                        } catch (UnknownHostException e) {
                            unlock = true;
                            log.info("Unknown host [{}]", hostName);
                        }
                    }
                    if (autoLockVerificationEnabled && unlock) {
                        log.info("Releasing lock {}", lockEntries.getKey());
                        helper.forceUnlock(lockEntries.getKey());
                    }
                }
            })
            .log("Verifying locks - done")
            .routeId("anshar.admin.periodic.lock.verification");

        from("direct:locks")
            .choice()
            .when().header("unlock")
            .process(p -> helper.forceUnlock((String) p.getIn().getHeader("unlock")))
            .end()
            .process(p -> {
                // Fetch all locks - sorted by keys
                final TreeMap<String, String> locksMap = new TreeMap<>(helper.getAllLocks());
                int maxlength = 0;
                // Find max length for prettifying output
                for (String s : locksMap.keySet()) {
                    maxlength = Math.max(maxlength, s.length());
                }

                String body = StringUtils.rightPad("key", maxlength) + " | value\n";

                // Now, sort by values to group hosts
                final List<Map.Entry<String, String>> sortedEntries = locksMap
                    .entrySet()
                    .stream()
                    .sorted(Map.Entry.comparingByValue())
                    .collect(Collectors.toList());

                for (Map.Entry<String, String> e : sortedEntries) {
                    body += StringUtils.rightPad(e.getKey(), maxlength) + " | " + e.getValue() + "\n";
                }
                p.getOut().setBody(body);
            })
            .routeId("admin")
        ;

        //Return subscription status
        from(STATS_ROUTE)
                .process(p -> {
                    long t1 = System.currentTimeMillis();
                    JSONObject stats = subscriptionManager.buildStats();
                    long t2 = System.currentTimeMillis();
                    stats.put("outbound", serverSubscriptionManager.getSubscriptionsAsJson());
                    long t3 = System.currentTimeMillis();

                    log.info("Build stats: {} ms, builds subscriptions: {} ms", (t2-t1), (t3-t2));
                    p.getOut().setBody(stats);
                })
                .to("freemarker:templates/stats.ftl")
                .routeId("admin.stats")
        ;

        final String operationHeaderName = "operation";

        //Stop subscription
        from(OPERATION_ROUTE)
             .choice()
                .when(header(operationHeaderName).isEqualTo("stop"))
                    .to("direct:stop")
                .endChoice()
                .when(header(operationHeaderName).isEqualTo("start"))
                    .to("direct:start")
                .endChoice()
                .when(header(operationHeaderName).isEqualTo("terminate"))
                    .to("direct:terminate.outbound.subscription")
                .endChoice()
                .when(header(operationHeaderName).isEqualTo("terminateAll"))
                    .to("direct:terminate.all.subscriptions")
                .endChoice()
                .when(header(operationHeaderName).isEqualTo("startAll"))
                    .to("direct:restart.all.subscriptions")
                .endChoice()
                .when(header(operationHeaderName).isEqualTo("flush"))
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


        //Return unmapped ids
        from(UNMAPPED_ROUTE)
                .filter(header("datasetId").isNotNull())
                .bean(healthManager, "getUnmappedIdsAsJson(${header.datasetId})")
                .to("freemarker:templates/unmapped.ftl")
                .routeId("admin.unmapped")
        ;

    }
}
