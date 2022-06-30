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

import com.google.common.net.HttpHeaders;
import no.rutebanken.anshar.config.AnsharConfiguration;
import no.rutebanken.anshar.data.collections.ExtendedHazelcastService;
import no.rutebanken.anshar.routes.RestRouteBuilder;
import no.rutebanken.anshar.routes.admin.auth.BasicAuthService;
import no.rutebanken.anshar.routes.health.HealthManager;
import no.rutebanken.anshar.routes.outbound.ServerSubscriptionManager;
import no.rutebanken.anshar.subscription.SiriDataType;
import no.rutebanken.anshar.subscription.SubscriptionManager;
import no.rutebanken.anshar.subscription.SubscriptionSetup;
import org.apache.camel.Exchange;
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
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static no.rutebanken.anshar.routes.admin.AdminRouteHelper.mergeJsonStats;
import static no.rutebanken.anshar.routes.policy.SingletonRoutePolicyFactory.DEFAULT_LOCK_VALUE;

@SuppressWarnings("unchecked")
@Service
@Configuration
public class AdministrationRoute extends RestRouteBuilder {

    private static final String STATS_ROUTE = "direct:stats";
    private static final String INTERNAL_STATS_ROUTE = "direct:internal.stats";
    private static final String OPERATION_ROUTE = "direct:operation";
    private static final String CLUSTERSTATS_ROUTE = "direct:clusterstats";
    private static final String UNMAPPED_ROUTE = "direct:unmapped";
    private static final String SITUATIONS_ROUTE = "direct:situations";

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

    @Autowired
    private AnsharConfiguration configuration;

    @Value("${anshar.route.singleton.policy.automatic.verification:false}")
    private boolean autoLockVerificationEnabled;

    @Value("${anshar.situations.debug.endpoint.enabled:false}")
    private boolean situationsDebugEndpoint;

    @Autowired
    private BasicAuthService basicAuthProcessor;

    @Override
    public void configure() throws Exception {
        super.configure();


        rest("/").tag("internal.admin.root")
                .get("").produces(MediaType.TEXT_HTML).to(STATS_ROUTE)
                .put("").to(OPERATION_ROUTE)
                .get("/locks").to("direct:locks")
                .get("/prepare-shutdown").to("direct:prepare-shutdown")
                .delete("/unmapped/{datasetId}").to("direct:clear-unmapped")
        ;

        rest("/anshar").tag("internal.admin")
                .get("/stats").produces(MediaType.TEXT_HTML).to(STATS_ROUTE)
                .get("/internalstats").produces(MediaType.APPLICATION_JSON).to(INTERNAL_STATS_ROUTE)
                .get("/clusterstats").produces(MediaType.APPLICATION_JSON).to(CLUSTERSTATS_ROUTE)
                .put("/stats").to(OPERATION_ROUTE)
                .get("/unmapped").produces(MediaType.TEXT_HTML).to(UNMAPPED_ROUTE)
                .get("/unmapped/{datasetId}").produces(MediaType.TEXT_HTML).to(UNMAPPED_ROUTE)
                .get("/situations/{datasetId}").produces(MediaType.TEXT_HTML).to(SITUATIONS_ROUTE)
        ;

        if (autoLockVerificationEnabled) {
            long verificationIntervalMillis = 10 * 60 * 1000;
            // repeatInterval : Use repeat interval to check every 10 minutes after startup - not every 10 minutes on clock
            from("quartz://anshar.verify.locks?trigger.repeatInterval=" + verificationIntervalMillis)
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
                        if (unlock) {
                            log.info("Releasing lock {}", lockEntries.getKey());
                            helper.forceUnlock(lockEntries.getKey());
                        }
                    }
                })
                .log("Verifying locks - done")
                .routeId("anshar.admin.periodic.lock.verification");
        }

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

        if (configuration.processAdmin() && !configuration.processData()) {
            from(STATS_ROUTE)
                    .process(basicAuthProcessor)
                    .setHeader(HttpHeaders.CONTENT_TYPE, simple(MediaType.APPLICATION_JSON))
                    .to(INTERNAL_STATS_ROUTE)
                    .removeHeader(HttpHeaders.CONTENT_TYPE)
                    .setProperty("proxy-stats", body())
                    .toD(vmHandlerBaseUrl + "/anshar/internalstats?bridgeEndpoint=true")
                    .setProperty("vm-stats", body().convertTo(String.class))
                    .toD(etHandlerBaseUrl + "/anshar/internalstats?bridgeEndpoint=true")
                    .setProperty("et-stats", body().convertTo(String.class))
                    .toD(sxHandlerBaseUrl + "/anshar/internalstats?bridgeEndpoint=true")
                    .setProperty("sx-stats", body().convertTo(String.class))
                    .process(p -> {
                        JSONObject body = mergeJsonStats(
                                p.getProperty("proxy-stats", String.class),
                                p.getProperty("vm-stats", String.class),
                                p.getProperty("et-stats", String.class),
                                p.getProperty("sx-stats", String.class)
                        );
                        p.getMessage().setBody(body);
                    })
                    .to("direct:removeHeaders")
                    .setHeader(HttpHeaders.CONTENT_TYPE, simple(MediaType.TEXT_HTML))
                    .to("freemarker:templates/stats.ftl")
                    .routeId("admin.stats")
            ;
        } else {
            //either proxy or data-handler
            from(STATS_ROUTE)
                    .process(basicAuthProcessor)
                    .setHeader(HttpHeaders.CONTENT_TYPE, simple(MediaType.APPLICATION_JSON))
                    .to(INTERNAL_STATS_ROUTE)
                    .to("direct:removeHeaders")
                    .setHeader(HttpHeaders.CONTENT_TYPE, simple(MediaType.TEXT_HTML))
                    .to("freemarker:templates/stats.ftl")
                    .routeId("admin.stats")
            ;
        }
        from("direct:removeHeaders")
                .removeHeaders("*")
                .routeId("admin.remove.headers");

        from (INTERNAL_STATS_ROUTE)
            .process(p -> {
                JSONObject stats = subscriptionManager.buildStats();
                stats.put("outbound", serverSubscriptionManager.getSubscriptionsAsJson());

                if (MediaType.APPLICATION_JSON.equals(p.getIn().getHeader(HttpHeaders.CONTENT_TYPE, String.class))) {
                    p.getMessage().setBody(stats);
                } else {
                    p.getMessage().setBody(stats.toJSONString());
                }
            })
        ;

        final String operationHeaderName = "operation";

        //Stop subscription
        from(OPERATION_ROUTE)
            .process(basicAuthProcessor)
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
                .when(header(operationHeaderName).isEqualTo("delete"))
                    .to("direct:delete.subscription")
                .endChoice()
                .when(header(operationHeaderName).isEqualTo("validateAll"))
                    .to("direct:validate.all.subscriptions")
                .endChoice()
            .end()
        ;

        from("direct:stop")
                .process(basicAuthProcessor)
                .bean(subscriptionManager, "stopSubscription(${header.subscriptionId})")
                .routeId("admin.stop")
        ;

        //Start subscription
        from("direct:start")
                .process(basicAuthProcessor)
                .bean(subscriptionManager, "startSubscription(${header.subscriptionId})")
                .routeId("admin.start")
        ;

        if (!configuration.processData()) {
            //Return subscription status
            from("direct:terminate.outbound.subscription")
                    .process(basicAuthProcessor)
                    .to("direct:redirect.request.et")
                    .to("direct:redirect.request.vm")
                    .to("direct:redirect.request.sx")
                    .routeId("admin.terminate.subscription")
            ;
        } else {
            //Return subscription status
            from("direct:terminate.outbound.subscription")
                    .bean(serverSubscriptionManager, "terminateSubscription(${header.subscriptionId})")
                    .routeId("admin.terminate.subscription")
            ;
        }

        //Return subscription status
        from("direct:terminate.all.subscriptions")
                .process(basicAuthProcessor)
                .bean(subscriptionManager, "terminateAllSubscriptions(${header.type})")
                .routeId("admin.terminate.all.subscriptions")
        ;


        //Return subscription status
        from("direct:restart.all.subscriptions")
                .process(basicAuthProcessor)
                .bean(subscriptionManager, "triggerRestartAllActiveSubscriptions(${header.type})")
                .routeId("admin.start.all.subscriptions")
        ;

        //Return subscription status
        from("direct:flush.data.from.subscription")
                .process(basicAuthProcessor)
                .process(p -> {
                    String subscriptionId = p.getIn().getHeader("subscriptionId", String.class);
                    SubscriptionSetup subscriptionSetup = subscriptionManager.get(subscriptionId);

                    String dataType = subscriptionSetup.getSubscriptionType().toString();

                    p.getMessage().setHeaders(p.getIn().getHeaders());
                    p.getMessage().setHeader("SiriDataType", dataType);
                })
                .to("direct:internal.flush.data.from.subscription")
        ;

        //Return subscription status
        from("direct:validate.all.subscriptions")
                .process(basicAuthProcessor)
                .process(p -> {
                    Set<String> ids = subscriptionManager.subscriptions.keySet();
                    log.info("Enabling validation for {} subscriptions", ids.size());
                    for (String subscriptionId : ids) {
                        SubscriptionSetup subscriptionSetup = subscriptionManager.get(subscriptionId);
                        if (subscriptionSetup != null) {
                            subscriptionSetup.setValidation(true);
                            subscriptionManager.updateSubscription(subscriptionSetup);
                            log.info("Enabling validation for {}", subscriptionSetup);
                        }
                    }
                })
                .routeId("anshar.validate.all.subscriptions")
        ;

        from("direct:internal.flush.data.from.subscription")
                .choice()
                    .when(p -> !configuration.processET() && p.getIn().getHeader("SiriDataType").equals(SiriDataType.ESTIMATED_TIMETABLE.name()))
                        .toD(etHandlerBaseUrl + "/anshar/stats?bridgeEndpoint=true&httpMethod=PUT&subscriptionId=${header.subscriptionId}")
                    .when(p -> !configuration.processVM() && p.getIn().getHeader("SiriDataType").equals(SiriDataType.VEHICLE_MONITORING.name()))
                        .toD(vmHandlerBaseUrl + "/anshar/stats?bridgeEndpoint=true&httpMethod=PUT&subscriptionId=${header.subscriptionId}")
                    .when(p -> !configuration.processSX() && p.getIn().getHeader("SiriDataType").equals(SiriDataType.SITUATION_EXCHANGE.name()))
                        .toD(sxHandlerBaseUrl + "/anshar/stats?bridgeEndpoint=true&httpMethod=PUT&subscriptionId=${header.subscriptionId}")
                    .otherwise()
                        .bean(helper, "flushDataFromSubscription(${header.subscriptionId})")
                .endChoice()
                .routeId("admin.internal.flush.data")
        ;


        //Return subscription status
        from("direct:delete.subscription")
                .process(basicAuthProcessor)
                .bean(helper, "deleteSubscription(${header.subscriptionId})")
                .to("direct:internal.delete.subscription")
        ;

        if (configuration.processAdmin() && !configuration.processData()) {
            from("direct:internal.delete.subscription")
                    .choice()
                    .when(p -> !configuration.processET())
                    .toD(etHandlerBaseUrl + "/anshar/stats?bridgeEndpoint=true&httpMethod=PUT&subscriptionId=${header.subscriptionId}")
                    .end()
                    .choice()
                    .when(p -> !configuration.processVM())
                    .toD(vmHandlerBaseUrl + "/anshar/stats?bridgeEndpoint=true&httpMethod=PUT&subscriptionId=${header.subscriptionId}")
                    .end()
                    .choice()
                    .when(p -> !configuration.processSX())
                    .toD(sxHandlerBaseUrl + "/anshar/stats?bridgeEndpoint=true&httpMethod=PUT&subscriptionId=${header.subscriptionId}")
                    .end()
                    .routeId("admin.internal.delete.subscription")
            ;
        } else {
            from("direct:internal.delete.subscription")
                    .log("Subscription deleted.")
                    .routeId("admin.internal.delete.subscription")
            ;
        }

        //Return subscription status
        from("direct:clear-unmapped")
                .bean(healthManager, "clearUnmappedIds(${header.datasetId})")
                .routeId("admin.clear.unmapped")
        ;


        //Prepare Camel shutdown
        from("direct:prepare-shutdown")
            .log("Triggered to prepare for shutdown")
                .process(p -> {
                    helper.shutdownTriggered = true;

                    final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();

                    executorService.schedule(() -> getContext().shutdown(), 5, TimeUnit.SECONDS);
                    executorService.schedule(() -> extendedHazelcastService.shutdown(), 10, TimeUnit.SECONDS);

                })
                .routeId("admin.prepare.shutdown")
        ;

        //Return unmapped ids
        from(UNMAPPED_ROUTE)
                .filter(header("datasetId").isNotNull())
                .bean(healthManager, "getUnmappedIdsAsJson(${header.datasetId})")
                .to("direct:removeHeaders")
                .to("freemarker:templates/unmapped.ftl")
                .routeId("admin.unmapped")
        ;

        if (situationsDebugEndpoint) {
            if (configuration.processSX()) {
                //Return unmapped ids
                from(SITUATIONS_ROUTE)
                        .filter(header("datasetId").isNotNull())
                        .bean(helper, "getSituationMetadataAsJson(${header.datasetId})")
                        .to("direct:removeHeaders")
                        .to("freemarker:templates/situations.ftl")
                        .routeId("admin.situations")
                ;
            } else {
                from(SITUATIONS_ROUTE)
                        .toD(sxHandlerBaseUrl + "${header.CamelHttpUri}?bridgeEndpoint=true")
                        .routeId("admin.situations")
                ;
            }
        } else {
            from(SITUATIONS_ROUTE)
                    .setHeader(Exchange.HTTP_RESPONSE_CODE, simple("404"))
                    .routeId("admin.situations")
            ;
        }

        from (CLUSTERSTATS_ROUTE)
                .bean(helper, "listClusterStats")
                .routeId("admin.clusterstats")
        ;
    }
}
