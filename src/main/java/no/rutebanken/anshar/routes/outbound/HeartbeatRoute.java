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

package no.rutebanken.anshar.routes.outbound;

import com.hazelcast.map.IMap;
import no.rutebanken.anshar.config.AnsharConfiguration;
import no.rutebanken.anshar.routes.BaseRouteBuilder;
import no.rutebanken.anshar.routes.siri.helpers.SiriObjectFactory;
import no.rutebanken.anshar.subscription.SubscriptionManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import uk.org.siri.siri20.Siri;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Service
public class HeartbeatRoute extends BaseRouteBuilder {

    private static final int HEARTBEAT_INTERVAL_MILLIS = 2000;


    @Autowired
    @Qualifier("getHeartbeatTimestampMap")
    private IMap<String, Instant> heartbeatTimestampMap;

    @Autowired
    private ServerSubscriptionManager serverSubscriptionManager;

    @Autowired
    private CamelRouteManager camelRouteManager;

    @Autowired
    private SiriObjectFactory siriObjectFactory;

    protected HeartbeatRoute(@Autowired  AnsharConfiguration config, @Autowired SubscriptionManager subscriptionManager) {
        super(config, subscriptionManager);
    }

    @Override
    public void configure() throws Exception {
        final String routeId = "anshar.outbound.subscription.manager.route";
        singletonFrom("quartz://anshar.outbound.subscription.manager?trigger.repeatInterval=" + HEARTBEAT_INTERVAL_MILLIS,
            routeId
        )
            .choice()
            .when(p -> isLeader(routeId))
                .process(p -> {
                    final Set<String> subscriptionIds = serverSubscriptionManager.subscriptions.keySet();
                    for (String subscriptionId : subscriptionIds) {
                        final OutboundSubscriptionSetup outboundSubscriptionSetup = serverSubscriptionManager.subscriptions.get(subscriptionId);

                        if (outboundSubscriptionSetup != null) {
                            if (LocalDateTime.now().isAfter(outboundSubscriptionSetup.getInitialTerminationTime().toLocalDateTime())) {
                                serverSubscriptionManager.terminateSubscription(outboundSubscriptionSetup.getSubscriptionId(), true);
                            } else if (!heartbeatTimestampMap.containsKey(subscriptionId)) {
                                final long heartbeatInterval = outboundSubscriptionSetup.getHeartbeatInterval();

                                Siri heartbeatNotification = siriObjectFactory.createHeartbeatNotification(outboundSubscriptionSetup.getSubscriptionId());
                                camelRouteManager.pushSiriData(heartbeatNotification, outboundSubscriptionSetup, true);

                                heartbeatTimestampMap.put(subscriptionId, Instant.now(), heartbeatInterval, TimeUnit.MILLISECONDS);
                            }
                        } else {
                            log.info("Outbound subscription {} not found.", subscriptionId);
                        }
                    }
                })
            .endChoice()
        ;
    }
}
