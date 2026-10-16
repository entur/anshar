package no.rutebanken.anshar.metrics;

import io.micrometer.core.instrument.ImmutableTag;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Route;
import org.apache.camel.spi.InflightRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Publishes inflight Camel exchanges per route as metrics, and logs exchanges that have been inflight for too long.
 * Makes leaked/stuck exchanges visible without having to wait for the shutdown timeout.
 */
@Component
public class CamelInflightMonitor {

    private static final Logger logger = LoggerFactory.getLogger(CamelInflightMonitor.class);

    private static final String INFLIGHT_GAUGE_NAME = "app.anshar.camel.inflight";
    private static final String OLDEST_INFLIGHT_GAUGE_NAME = "app.anshar.camel.inflight.oldest.seconds";
    private static final String ROUTE_ID_TAG = "routeId";
    private static final String ROUTE_ENDPOINT_TAG = "endpoint";

    private static final int MAX_LOGGED_EXCHANGES = 10;

    private final CamelContext camelContext;
    private final MeterRegistry meterRegistry;
    private final long stuckThresholdMillis;

    private final Map<String, Integer> inflightPerRoute = new ConcurrentHashMap<>();
    private volatile long oldestInflightSeconds;

    @Value("${anshar.camel.inflight.monitor.interval.seconds:300}")
    private long intervalSeconds;

    private ScheduledExecutorService executor;

    @Autowired
    public CamelInflightMonitor(CamelContext camelContext, MeterRegistry meterRegistry,
                                @Value("${anshar.camel.inflight.monitor.stuck.threshold.seconds:300}") long stuckThresholdSeconds) {
        this.camelContext = camelContext;
        this.meterRegistry = meterRegistry;
        this.stuckThresholdMillis = TimeUnit.SECONDS.toMillis(stuckThresholdSeconds);
        meterRegistry.gauge(OLDEST_INFLIGHT_GAUGE_NAME, List.of(), this, monitor -> monitor.oldestInflightSeconds);
    }

    @PostConstruct
    void start() {
        executor = Executors.newSingleThreadScheduledExecutor();
        executor.scheduleAtFixedRate(() -> {
            try {
                update();
            } catch (Exception e) {
                logger.warn("Failed to check inflight exchanges", e);
            }
        }, intervalSeconds, intervalSeconds, TimeUnit.SECONDS);
    }

    @PreDestroy
    void stop() {
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    /**
     * Updates the inflight metrics, and logs exchanges that have been inflight longer than the threshold.
     *
     * @return the stuck exchanges that were logged
     */
    List<InflightRepository.InflightExchange> update() {
        InflightRepository inflightRepository = camelContext.getInflightRepository();

        for (Route route : camelContext.getRoutes()) {
            String routeId = route.getRouteId();
            int inflight = inflightRepository.size(routeId);
            // Only routes that have had inflight exchanges get a gauge, to avoid one time series per route
            if (inflight > 0 && inflightPerRoute.put(routeId, inflight) == null) {
                List<Tag> tags = List.of(new ImmutableTag(ROUTE_ID_TAG, routeId),
                        new ImmutableTag(ROUTE_ENDPOINT_TAG, route.getEndpoint().getEndpointBaseUri()));
                meterRegistry.gauge(INFLIGHT_GAUGE_NAME, tags, inflightPerRoute, map -> map.getOrDefault(routeId, 0));
            } else if (inflightPerRoute.containsKey(routeId)) {
                inflightPerRoute.put(routeId, inflight);
            }
        }

        Collection<InflightRepository.InflightExchange> oldest = inflightRepository.browse(MAX_LOGGED_EXCHANGES, true);
        oldestInflightSeconds = oldest.stream()
                .mapToLong(InflightRepository.InflightExchange::getDuration)
                .max()
                .orElse(0) / 1000;

        List<InflightRepository.InflightExchange> stuck = oldest.stream()
                .filter(exchange -> exchange.getDuration() >= stuckThresholdMillis)
                .toList();

        if (!stuck.isEmpty()) {
            logger.warn("{} exchanges inflight, {} of the oldest have been inflight for more than {}s. Inflights per route: {}",
                    inflightRepository.size(), stuck.size(), stuckThresholdMillis / 1000, inflightPerRoute);
            for (InflightRepository.InflightExchange inflightExchange : stuck) {
                logStuckExchange(inflightExchange);
            }
        }
        return stuck;
    }

    private void logStuckExchange(InflightRepository.InflightExchange inflightExchange) {
        Exchange exchange = inflightExchange.getExchange();
        Object body = exchange.getMessage().getBody();
        logger.warn("Stuck exchange {}: inflight for {}s, {}s at node {} in route {} ({}), started in route {} ({}), body: {}, failed: {}",
                exchange.getExchangeId(),
                inflightExchange.getDuration() / 1000,
                inflightExchange.getElapsed() / 1000,
                inflightExchange.getNodeId(),
                inflightExchange.getAtRouteId(), endpointOf(inflightExchange.getAtRouteId()),
                inflightExchange.getFromRouteId(), endpointOf(inflightExchange.getFromRouteId()),
                body != null ? body.getClass().getSimpleName() : null,
                exchange.getException() != null ? exchange.getException().toString() : exchange.isFailed());
    }

    private String endpointOf(String routeId) {
        if (routeId == null) {
            return null;
        }
        Route route = camelContext.getRoute(routeId);
        return route != null ? route.getEndpoint().getEndpointBaseUri() : null;
    }
}
