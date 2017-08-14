package no.rutebanken.anshar.routes;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CamelConfiguration {

    private static final String QUEUE_PREFIX = "anshar.siri";
    public static final String TRANSFORM_QUEUE = QUEUE_PREFIX + ".transform";
    public static final String ROUTER_QUEUE = QUEUE_PREFIX + ".router";
    public static final String VALIDATOR_QUEUE = QUEUE_PREFIX + ".validator";
    public static final String DEFAULT_PROCESSOR_QUEUE = QUEUE_PREFIX + ".process";
    public static final String SITUATION_EXCHANGE_QUEUE = DEFAULT_PROCESSOR_QUEUE + ".sx";
    public static final String VEHICLE_MONITORING_QUEUE = DEFAULT_PROCESSOR_QUEUE + ".vm";
    public static final String ESTIMATED_TIMETABLE_QUEUE = DEFAULT_PROCESSOR_QUEUE + ".et";
    public static final String PRODUCTION_TIMETABLE_QUEUE = DEFAULT_PROCESSOR_QUEUE + ".pt";
    public static final String HEARTBEAT_QUEUE = DEFAULT_PROCESSOR_QUEUE + ".heartbeat";
    public static final String FETCHED_DELIVERY_QUEUE = DEFAULT_PROCESSOR_QUEUE + ".fetched.delivery";

    @Value("${anshar.incoming.port}")
    private String inboundPort;

    @Value("${anshar.validation.enabled}")
    private boolean validationEnabled = false;

    @Value("${anshar.incoming.activemq.concurrentConsumers}")
    private long concurrentConsumers;

    @Value("${anshar.incoming.logdirectory}")
    private String incomingLogDirectory = "/tmp";

    @Value("${anshar.incoming.activemq.timetolive}")
    private String timeToLive;

    @Value("${anshar.inbound.pattern}")
    private String incomingPathPattern = "/foo/bar/rest";

    @Value("${anshar.inbound.url}")
    private String inboundUrl = "http://localhost:8080";

    @Value("${anshar.healthcheck.interval.seconds}")
    private int healthCheckInterval = 30;

    @Value("${anshar.environment}")
    private String environment;

    public String getInboundPort() {
        return inboundPort;
    }

    public String getIncomingPathPattern() {
        return incomingPathPattern;
    }

    public String getTimeToLive() {
        return timeToLive;
    }

    public long getConcurrentConsumers() {
        return concurrentConsumers;
    }

    public boolean isValidationEnabled() {
        return validationEnabled;
    }

    public CamelConfiguration() {
    }

    public String getEnvironment() {
        return environment;
    }

    public String getInboundUrl() {
        return inboundUrl;
    }
}