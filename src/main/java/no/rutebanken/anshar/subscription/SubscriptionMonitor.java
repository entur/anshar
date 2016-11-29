package no.rutebanken.anshar.subscription;

import com.google.common.base.Preconditions;
import com.hazelcast.core.IMap;
import no.rutebanken.anshar.messages.collections.DistributedCollection;
import no.rutebanken.anshar.routes.siri.*;
import no.rutebanken.anshar.routes.siri.transformer.ValueAdapter;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.RouteDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.SocketException;
import java.time.Instant;
import java.time.ZoneId;
import java.util.*;

@Configuration
public class SubscriptionMonitor implements CamelContextAware {
    private static Logger logger = LoggerFactory.getLogger(SubscriptionMonitor.class);

    private final String ANSHAR_HEALTHCHECK_KEY = "anshar.healthcheck";

    @Value("${anshar.inbound.url}")
    private String inboundUrl = "http://localhost:8080";

    @Value("${anshar.healtcheck.interval.seconds}")
    private int healthCheckInterval = 30;

    private IMap<String, Instant> lockMap;

    @Autowired
    private Config config;

    protected static CamelContext camelContext;

    private Timer healthCheckTimer;

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    public SubscriptionMonitor() {
        DistributedCollection dc = new DistributedCollection();
        lockMap = dc.getLockMap();
    }

    @Bean
    List<RouteBuilder> createSubscriptions() {
        camelContext.setUseMDCLogging(true);

        List<RouteBuilder> builders = new ArrayList<>();
        if (config != null) {
            List<SubscriptionSetup> subscriptionSetups = config.getSubscriptions();
            logger.info("Initializing {} subscriptions", subscriptionSetups.size());
            Set<String> subscriptionIds = new HashSet<>();

            // Validation and consistency-verification
            for (SubscriptionSetup subscriptionSetup : subscriptionSetups) {
                subscriptionSetup.setAddress(inboundUrl);

                if (!isValid(subscriptionSetup)) {
                    throw new ServiceConfigurationError("Configuration is not valid for subscription " + subscriptionSetup);
                }

                if (subscriptionIds.contains(subscriptionSetup.getSubscriptionId())) {
                    //Verify subscriptionId-uniqueness
                    throw new ServiceConfigurationError("SubscriptionIds are NOT unique for ID="+subscriptionSetup.getSubscriptionId());
                }

                //Add NSR StopPlaceIdMapperAdapters
                List<ValueAdapter> nsr = MappingAdapterPresets.createNsrIdMappingAdapters(subscriptionSetup.getIdMappingPrefixes());
                subscriptionSetup.getMappingAdapters().addAll(nsr);

                //Add Chouette route_id, trip_id adapters
                List<ValueAdapter> datasetPrefix = MappingAdapterPresets.createIdPrefixAdapters(subscriptionSetup.getDatasetId());
                subscriptionSetup.getMappingAdapters().addAll(datasetPrefix);

                subscriptionIds.add(subscriptionSetup.getSubscriptionId());

                RouteBuilder routeBuilder = getRouteBuilder(subscriptionSetup);
                try {
                    camelContext.addRoutes(routeBuilder);
                } catch (Exception e) {
                    logger.warn("Could not add subscription", e);
                }
            }


            startPeriodicHealthcheckService(subscriptionSetups);
        } else {
            logger.error("Subscriptions not configured correctly - no subscriptions will be started");
        }

        return builders;
    }

    private RouteBuilder getRouteBuilder(SubscriptionSetup subscriptionSetup) {
        RouteBuilder route;
        if (subscriptionSetup.getVersion().equals("1.4")) {
            if (subscriptionSetup.getSubscriptionMode() == SubscriptionSetup.SubscriptionMode.SUBSCRIBE) {
                if (subscriptionSetup.getServiceType() == SubscriptionSetup.ServiceType.SOAP) {
                    route = new Siri20ToSiriWS14Subscription(subscriptionSetup);
                } else {
                    route = new Siri20ToSiriRS14Subscription(subscriptionSetup);
                }
            } else {
                route = new Siri20ToSiriWS14RequestResponse(subscriptionSetup);
            }
        } else {
            if (subscriptionSetup.getSubscriptionMode() == SubscriptionSetup.SubscriptionMode.SUBSCRIBE) {
                route = new Siri20ToSiriRS20Subscription(subscriptionSetup);
            } else {
                route = new Siri20ToSiriRS20RequestResponse(subscriptionSetup);
            }
        }
        return route;
    }

    private void startPeriodicHealthcheckService(final List<SubscriptionSetup> subscriptionSetups) {

        for (SubscriptionSetup subscriptionSetup : subscriptionSetups) {
            if (!SubscriptionManager.isSubscriptionRegistered(subscriptionSetup.getSubscriptionId())) {
                SubscriptionManager.addPendingSubscription(subscriptionSetup.getSubscriptionId(), subscriptionSetup);
            }
        }

        // Subscription Healthcheck
        TimerTask healthCheck = new TimerTask() {
            @Override
            public void run() {
                // tryLock returns immediately - does not wait for lock to be released
                boolean locked = lockMap.tryLock(ANSHAR_HEALTHCHECK_KEY);

                if (locked) {
                    logger.debug("Healthcheck: Got lock");
                    try {
                        Instant instant = lockMap.get(ANSHAR_HEALTHCHECK_KEY);
                        if (instant == null || instant.isBefore(Instant.now().minusSeconds(healthCheckInterval))) {
                            lockMap.put(ANSHAR_HEALTHCHECK_KEY, Instant.now());
                            logger.info("Healthcheck: Checking health {}, {} routes",
                                    lockMap.get(ANSHAR_HEALTHCHECK_KEY).atZone(ZoneId.systemDefault()),
                                    camelContext.getRoutes().size());

                            Map<String, SubscriptionSetup> pendingSubscriptions = SubscriptionManager.getPendingSubscriptions();

                            int counter = 0;
                            for (SubscriptionSetup subscriptionSetup : pendingSubscriptions.values()) {
                                if (subscriptionSetup.isActive()) {
                                    logger.info("Healthcheck: Trigger start subscription {}", subscriptionSetup);
                                    startSubscriptionAsync(subscriptionSetup, 3000 * counter++);
                                }
                            }

                            Map<String, SubscriptionSetup> activeSubscriptions = SubscriptionManager.getActiveSubscriptions();
                            for (SubscriptionSetup subscriptionSetup : activeSubscriptions.values()) {
                                if (!SubscriptionManager.isSubscriptionHealthy(subscriptionSetup.getSubscriptionId())) {
                                    //start "cancel"-route
                                    logger.info("Healthcheck: Trigger cancel subscription {}", subscriptionSetup);
                                    cancelSubscription(subscriptionSetup);
                                }
                            }

                        } else {
                            logger.debug("Healthcheck: Healthcheck has already been handled recently [{}]", instant);
                        }
                    } catch (Exception e) {
                        logger.error("Healthcheck: Caught exception during healthcheck.", e);
                    } finally {
                        lockMap.unlock(ANSHAR_HEALTHCHECK_KEY);
                        logger.debug("Healthcheck: Lock released");
                    }
                } else {
                    logger.debug("Healthcheck: Already locked - skipping");
                }
            }
        };

        healthCheckTimer =  new Timer();
        healthCheckTimer.scheduleAtFixedRate(healthCheck, 0, 10000);

        // Monitor healthcheck-task
        TimerTask task = new TimerTask() {
            @Override
            public void run() {
                Instant instant = lockMap.get(ANSHAR_HEALTHCHECK_KEY);
                if (instant != null && instant.isBefore(Instant.now().minusSeconds(3 * healthCheckInterval))) {
                    // TODO: Should stopped healthcheck-thread trigger server restart?
                    // LivenessReadinessRoute.triggerRestart = true;
                    logger.error("Healthcheck has stopped - last check [{}]", instant.atZone(ZoneId.systemDefault()));
                }
            }
        };
        Timer timer = new Timer();
        timer.scheduleAtFixedRate(task, 0, healthCheckInterval*1000);
    }

    private static void startSubscriptionAsync(SubscriptionSetup subscriptionSetup, int delay) {
        TimerTask task = new TimerTask() {
            @Override
            public void run() {
                try {
                    startSubscription(subscriptionSetup);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        };
        Timer timer = new Timer();
        timer.schedule(task, delay);
    }


    public static void startSubscription(final SubscriptionSetup subscriptionSetup) throws Exception {
        SubscriptionManager.activatePendingSubscription(subscriptionSetup.getSubscriptionId());
        if (subscriptionSetup.getSubscriptionMode() == SubscriptionSetup.SubscriptionMode.SUBSCRIBE) {
            triggerRoute(subscriptionSetup.getStartSubscriptionRouteName());
        } else {
            // If request/response-routes lose its connection, it will not be reestablished - needs to be restarted
//            Route route = camelContext.getRoute(subscriptionSetup.getRequestResponseRouteName());

            camelContext.startRoute(subscriptionSetup.getRequestResponseRouteName());
//            if (route != null) {
//                logger.info("Starting route for subscription {}", subscriptionSetup);
//                route.getServices().forEach(service -> {
//                    try {
//                        service.start();
//                    } catch (Exception e) {
//                        logger.warn("Restarting route failed", e);
//                    }
//                });
//            }
        }
    }

    public static void cancelSubscription(final SubscriptionSetup subscriptionSetup) throws Exception {

        SubscriptionManager.removeSubscription(subscriptionSetup.getSubscriptionId());

        if (subscriptionSetup.getSubscriptionMode() == SubscriptionSetup.SubscriptionMode.SUBSCRIBE) {
            triggerRoute(subscriptionSetup.getCancelSubscriptionRouteName());
        } else {
            // If request/response-routes lose its connection, it will not be reestablished - needs to be restarted
//            Route route = camelContext.getRoute(subscriptionSetup.getRequestResponseRouteName());
            camelContext.stopRoute(subscriptionSetup.getRequestResponseRouteName());
//            if (route != null) {
//                logger.info("Stopping route for subscription {}", subscriptionSetup);
//                route.getServices().forEach(service -> {
//                    try {
//                        service.stop();
//                    } catch (Exception e) {
//                        logger.warn("Restarting route failed", e);
//                    }
//                });
//            }
        }
    }


    private static void triggerRoute(final String routeName) {
        if (camelContext.getRoutes().isEmpty()) {
            //Not yet started
            return;
        }
        Thread r = new Thread() {
            @Override
            public void run() {
                try {
                    logger.info("Triggering route {}", routeName);
                    TriggerRouteBuilder triggerRouteBuilder = new TriggerRouteBuilder(routeName);
                    String routeId = addRoute(triggerRouteBuilder);
                    executeRoute(triggerRouteBuilder.getRouteName());
                    stopAndRemoveRoute(routeId);
                } catch (Exception e) {
                    if (e.getCause() instanceof SocketException) {
                        logger.info("Recipient is unreachable - ignoring");
                    } else {
                        logger.warn("Exception caught when triggering route ", e);
                    }
                }
            }
        };

        r.start();
    }

    private static String addRoute(TriggerRouteBuilder route) throws Exception {
        camelContext.addRoutes(route);
        logger.trace("Route added - CamelContext now has {} routes", camelContext.getRoutes().size());
        return route.getDefinition().getId();
    }

    private static void executeRoute(String routeName) {
        ProducerTemplate template = camelContext.createProducerTemplate();
        template.sendBody(routeName, "");
    }

    private static boolean stopAndRemoveRoute(String routeId) throws Exception {
        RouteDefinition routeDefinition = camelContext.getRouteDefinition(routeId);
        camelContext.removeRouteDefinition(routeDefinition);
        logger.trace("Route removed - CamelContext now has {} routes", camelContext.getRoutes().size());
        return true;
    }

    private boolean isValid(SubscriptionSetup s) {
        Preconditions.checkNotNull(s.getVendor(), "Vendor is not set");
        Preconditions.checkNotNull(s.getDatasetId(), "DatasetId is not set");
        Preconditions.checkNotNull(s.getServiceType(), "ServiceType is not set");
        Preconditions.checkNotNull(s.getSubscriptionType(), "SubscriptionType is not set");
        Preconditions.checkNotNull(s.getVersion(), "Version is not set");
        Preconditions.checkNotNull(s.getSubscriptionId(), "SubscriptionId is not set");
        Preconditions.checkNotNull(s.getRequestorRef(), "RequestorRef is not set");
        Preconditions.checkNotNull(s.getSubscriptionMode(), "SubscriptionMode is not set");

        Preconditions.checkNotNull(s.getHeartbeatInterval(), "HeartbeatInterval is not set");
        Preconditions.checkState(s.getHeartbeatInterval().toMillis() > 0, "HeartbeatInterval must be > 0");

        Preconditions.checkNotNull(s.getDurationOfSubscription(), "Duration is not set");
        Preconditions.checkState(s.getDurationOfSubscription().toMillis() > 0, "Duration must be > 0");

        Preconditions.checkNotNull(s.getUrlMap(), "UrlMap is not set");
        Map<RequestType, String> urlMap = s.getUrlMap();
        if (s.getSubscriptionMode() == SubscriptionSetup.SubscriptionMode.REQUEST_RESPONSE) {
            if (SubscriptionSetup.SubscriptionType.SITUATION_EXCHANGE.equals(s.getSubscriptionType())) {
                Preconditions.checkNotNull(urlMap.get(RequestType.GET_SITUATION_EXCHANGE), "GET_SITUATION_EXCHANGE-url is missing. " + s);
            } else if (SubscriptionSetup.SubscriptionType.VEHICLE_MONITORING.equals(s.getSubscriptionType())) {
                Preconditions.checkNotNull(urlMap.get(RequestType.GET_VEHICLE_MONITORING), "GET_VEHICLE_MONITORING-url is missing. " + s);
            } else if (SubscriptionSetup.SubscriptionType.ESTIMATED_TIMETABLE.equals(s.getSubscriptionType())) {
                Preconditions.checkNotNull(urlMap.get(RequestType.GET_ESTIMATED_TIMETABLE), "GET_ESTIMATED_TIMETABLE-url is missing. " + s);
            } else {
                Preconditions.checkArgument(false, "URLs not configured correctly");
            }
        } else if (s.getSubscriptionMode() == SubscriptionSetup.SubscriptionMode.SUBSCRIBE) {
            Preconditions.checkNotNull(urlMap.get(RequestType.SUBSCRIBE), "SUBSCRIBE-url is missing. " + s);
            Preconditions.checkNotNull(urlMap.get(RequestType.DELETE_SUBSCRIPTION), "DELETE_SUBSCRIPTION-url is missing. " + s);
        } else {
            Preconditions.checkArgument(false, "Subscription mode not configured");
        }

        return true;
    }

}

class TriggerRouteBuilder extends RouteBuilder {

    private final String routeToTrigger;
    private String routeName;
    private RouteDefinition definition;

    public TriggerRouteBuilder(String routeToTrigger) {
        this.routeToTrigger = routeToTrigger;
    }

    @Override
    public void configure() throws Exception {
        routeName = String.format("direct:%s", UUID.randomUUID().toString());
        definition = from(routeName)
                .log("Triggering route: " + routeToTrigger)
                .to("direct:" + routeToTrigger);
    }

    public RouteDefinition getDefinition() {
        return definition;
    }

    public String getRouteName() {
        return routeName;
    }
}
