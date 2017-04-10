package no.rutebanken.anshar.routes.policy;

import no.rutebanken.anshar.messages.collections.AnsharConfiguration;
import org.apache.camel.CamelContext;
import org.apache.camel.component.hazelcast.policy.HazelcastRoutePolicy;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.spi.RoutePolicy;
import org.apache.camel.spi.RoutePolicyFactory;
import org.rutebanken.hazelcasthelper.service.HazelCastService;
import org.rutebanken.hazelcasthelper.service.KubernetesService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import static no.rutebanken.anshar.routes.Constants.SINGLETON_ROUTE_DEFINITION_GROUP_NAME;
/**
 * Create policies for enforcing that routes are started as singleton, using Hazelcast for cluster  sync.
 */
@Service
public class SingletonRoutePolicyFactory extends HazelCastService implements RoutePolicyFactory {

    private static final Logger log = LoggerFactory.getLogger(SingletonRoutePolicyFactory.class);

    @Value("${anshar.route.singleton.policy.ignore:false}")
    private boolean ignorePolicy;

    public SingletonRoutePolicyFactory(@Autowired KubernetesService kubernetesService, @Autowired AnsharConfiguration cfg) {
        super(kubernetesService, cfg.getHazelcastManagementUrl());
    }

    /**
     * Create policy ensuring only one route with 'key' is started in cluster.
     */
    private RoutePolicy build(String key) {
        HazelcastRoutePolicy hazelcastRoutePolicy = new HazelcastRoutePolicy(this.hazelcast);
        hazelcastRoutePolicy.setLockMapName("lockMap");
        hazelcastRoutePolicy.setLockKey(key);
        hazelcastRoutePolicy.setLockValue("lockValue");
        hazelcastRoutePolicy.setShouldStopConsumer(true);

        return hazelcastRoutePolicy;
    }

    @Override
    public RoutePolicy createRoutePolicy(CamelContext camelContext, String routeId, RouteDefinition routeDefinition) {
        try {
            if (!ignorePolicy && SINGLETON_ROUTE_DEFINITION_GROUP_NAME.equals(routeDefinition.getGroup())) {
                return build(routeId);
            }
        } catch (Exception e) {
            log.warn("Failed to create singleton route policy", e);
        }
        return null;
    }


}
