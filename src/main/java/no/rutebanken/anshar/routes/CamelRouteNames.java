package no.rutebanken.anshar.routes;

import org.springframework.context.annotation.Configuration;

@Configuration
public class CamelRouteNames {

    public static final String SINGLETON_ROUTE_DEFINITION_GROUP_NAME = "AnsharRutebankenSingletonRoute";


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

}