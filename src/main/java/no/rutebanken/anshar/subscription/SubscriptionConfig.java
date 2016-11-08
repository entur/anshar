package no.rutebanken.anshar.subscription;

import com.google.common.base.Preconditions;
import com.hazelcast.core.IMap;
import no.rutebanken.anshar.messages.collections.DistributedCollection;
import no.rutebanken.anshar.routes.siri.*;
import org.apache.camel.*;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.RouteDefinition;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Configuration
public class SubscriptionConfig implements CamelContextAware {
    private static Logger logger = LoggerFactory.getLogger(SubscriptionConfig.class);

    private final String ANSHAR_HEALTHCHECK_KEY = "anshar.healthcheck";

    @Value("${anshar.inbound.url}")
    private String inboundUrl = "http://localhost:8080";

    @Value("${anshar.healtcheck.interval.seconds}")
    private int healthCheckInterval = 30;

    private IMap<String, Instant> lockMap;

    @Autowired
    private Config config;

    protected static CamelContext camelContext;
    private ScheduledExecutorService execService;
    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    public SubscriptionConfig() {
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
                subscriptionIds.add(subscriptionSetup.getSubscriptionId());
            }

            startPeriodicHealthcheckService(subscriptionSetups);
        } else {
            logger.error("Subscriptions not configured correctly - no subscriptions will be started");
        }

        return builders;
    }

    @NotNull
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

        execService =   Executors.newScheduledThreadPool(2);

        // Subscription Healthcheck
        execService.scheduleWithFixedDelay(() -> {

            // tryLock returns immediately - does not wait for lock to be released
            boolean locked = lockMap.tryLock(ANSHAR_HEALTHCHECK_KEY);

            if (locked) {
                logger.trace("Healthcheck: Got lock");
                try {
                    Instant instant = lockMap.get(ANSHAR_HEALTHCHECK_KEY);
                    if (instant == null || instant.isBefore(Instant.now().minusSeconds(healthCheckInterval))) {
                        lockMap.put(ANSHAR_HEALTHCHECK_KEY, Instant.now());
                        logger.info("Healthcheck: Checking health {}", lockMap.get(ANSHAR_HEALTHCHECK_KEY));
                        try {
                            Map<String, SubscriptionSetup> pendingSubscriptions = SubscriptionManager.getPendingSubscriptions();
                            for (SubscriptionSetup subscriptionSetup : pendingSubscriptions.values()) {
                                logger.info("Healthcheck: Trigger start subscription {}", subscriptionSetup);
                                startSubscription(subscriptionSetup);
                            }

                            Map<String, SubscriptionSetup> activeSubscriptions = SubscriptionManager.getActiveSubscriptions();
                            for (SubscriptionSetup subscriptionSetup : activeSubscriptions.values()) {
                                if (!SubscriptionManager.isSubscriptionHealthy(subscriptionSetup.getSubscriptionId())) {
                                    SubscriptionManager.removeSubscription(subscriptionSetup.getSubscriptionId());
                                    //start "cancel"-route
                                    logger.info("Healthcheck: Trigger cancel subscription {}", subscriptionSetup);
                                    cancelSubscription(subscriptionSetup);
                                }
                            }
                        } catch (Exception e) {
                            logger.error("Healthcheck: Caught exception during healthcheck.", e);
                        }
                    } else {
                        logger.info("Healthcheck: Healthcheck has already been handled recently [{}]", instant);
                    }
                } finally {
                    lockMap.unlock(ANSHAR_HEALTHCHECK_KEY);
                    logger.trace("Healthcheck: Lock released");
                }
            } else {
                logger.info("Healthcheck: Already locked - skipping");
            }
        }, 0, healthCheckInterval, TimeUnit.SECONDS);

        // Monitor healthcheck-task
        execService.scheduleAtFixedRate(() -> {
            Instant instant = lockMap.get(ANSHAR_HEALTHCHECK_KEY);
            if (instant != null && instant.isBefore(Instant.now().minusSeconds(3*healthCheckInterval))) {
                logger.error("Healthcheck has stopped - last check [{}]", instant);
            }
        }, 0, healthCheckInterval, TimeUnit.SECONDS);
    }


    private void startSubscription(final SubscriptionSetup subscriptionSetup) throws Exception {
        String routeId = "triggerStart" + subscriptionSetup.getSubscriptionId();
        RouteBuilder initializerRoute = new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                if (subscriptionSetup.getSubscriptionMode() == SubscriptionSetup.SubscriptionMode.REQUEST_RESPONSE) {

                    String routeId = subscriptionSetup.getRequestResponseRouteName();

                    RouteDefinition routeDefinition = camelContext.getRouteDefinition(routeId);
                    if (routeDefinition != null) {
                        camelContext.removeRouteDefinition(routeDefinition);
                    }

                    RouteBuilder route = getRouteBuilder(subscriptionSetup);
                    try {
                        camelContext.addRoutes(route);
                    } catch (Exception e) {
                        logger.warn("Could not start subscription {}", subscriptionSetup);
                    }

                } else {
                    from("timer://triggerStart" + subscriptionSetup.getSubscriptionId() + "?repeatCount=1&delay=50")
                            .routeId(routeId)
                            .to("activemq:" + subscriptionSetup.getStartSubscriptionRouteName());
                }
            }
        };

        RouteDefinition routeDefinition = camelContext.getRouteDefinition(routeId);
        if (routeDefinition != null) {
            camelContext.removeRouteDefinition(routeDefinition);
        }
        camelContext.addRoutes(initializerRoute);
    }

    private void cancelSubscription(final SubscriptionSetup subscriptionSetup) throws Exception {
        String routeId = "triggerCancel" + subscriptionSetup.getSubscriptionId();
        RouteBuilder initializerRoute = new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                if (subscriptionSetup.getSubscriptionMode() == SubscriptionSetup.SubscriptionMode.REQUEST_RESPONSE) {
                    String routeId = subscriptionSetup.getRequestResponseRouteName();

                    RouteDefinition routeDefinition = camelContext.getRouteDefinition(routeId);
                    if (routeDefinition != null) {
                        camelContext.removeRouteDefinition(routeDefinition);
                    }
                } else {
                    from("timer://triggerCancel" + subscriptionSetup.getSubscriptionId() + "?repeatCount=1&delay=50")
                            .routeId(routeId)
                            .to("activemq:" + subscriptionSetup.getCancelSubscriptionRouteName());
                }
            }
        };

        RouteDefinition routeDefinition = camelContext.getRouteDefinition(routeId);
        if (routeDefinition != null) {
            camelContext.removeRouteDefinition(routeDefinition);
        }
        camelContext.addRoutes(initializerRoute);
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
