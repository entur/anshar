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

package no.rutebanken.anshar.routes.policy;

import no.rutebanken.anshar.data.collections.ExtendedHazelcastService;
import org.apache.camel.CamelContext;
import org.apache.camel.NamedNode;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.spi.RoutePolicy;
import org.apache.camel.spi.RoutePolicyFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import static no.rutebanken.anshar.routes.CamelRouteNames.SINGLETON_ROUTE_DEFINITION_GROUP_NAME;
/**
 * Create policies for enforcing that routes are started as singleton, using Hazelcast for cluster  sync.
 */
@Service
public class SingletonRoutePolicyFactory implements RoutePolicyFactory {

    private static final Logger log = LoggerFactory.getLogger(SingletonRoutePolicyFactory.class);
    public static final String DEFAULT_LOCK_VALUE = "lockValue";

    private boolean ignorePolicy;

    private String lockValue;

    private ExtendedHazelcastService hazelcastService;

    public SingletonRoutePolicyFactory(
        @Autowired
            ExtendedHazelcastService hazelcastService,
        @Value("${anshar.route.singleton.policy.ignore:false}")
            boolean ignorePolicy,
        @Value("${anshar.route.singleton.policy.lockValue:}")
            String lockValue
    ) {
        this.hazelcastService = hazelcastService;
        this.ignorePolicy = ignorePolicy;
        if (lockValue != null && !lockValue.isEmpty()) {
            log.info("using lockValue {}", lockValue);
            this.lockValue = lockValue;
        } else {
            log.info("using default lockValue {}", lockValue);
            this.lockValue = DEFAULT_LOCK_VALUE;
        }
    }

    /**
     * Create policy ensuring only one route with 'key' is started in cluster.
     */
    private RoutePolicy build(String key) {
        InterruptibleHazelcastRoutePolicy hazelcastRoutePolicy = new InterruptibleHazelcastRoutePolicy(hazelcastService.getHazelcastInstance());
        hazelcastRoutePolicy.setLockMapName("ansharRouteLockMap");
        hazelcastRoutePolicy.setLockKey(key);
        hazelcastRoutePolicy.setLockValue(lockValue);
        hazelcastRoutePolicy.setShouldStopConsumer(false);

        log.info("RoutePolicy: Created HazelcastPolicy for key {}", key);
        return hazelcastRoutePolicy;
    }

    @Override
    public RoutePolicy createRoutePolicy(CamelContext camelContext, String routeId, NamedNode routeDefinition) {
        try {
            if (!ignorePolicy && SINGLETON_ROUTE_DEFINITION_GROUP_NAME.equals(((RouteDefinition)routeDefinition).getGroup())) {
                return build(routeId);
            }
        } catch (Exception e) {
            log.warn("Failed to create singleton route policy", e);
        }
        return null;
    }


}
