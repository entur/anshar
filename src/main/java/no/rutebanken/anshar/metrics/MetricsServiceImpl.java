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

package no.rutebanken.anshar.metrics;

import com.codahale.metrics.MetricFilter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.graphite.Graphite;
import com.codahale.metrics.graphite.GraphiteReporter;
import com.google.common.base.Strings;
import no.rutebanken.anshar.subscription.SiriDataType;
import no.rutebanken.anshar.subscription.SubscriptionManager;
import no.rutebanken.anshar.subscription.SubscriptionSetup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

public class MetricsServiceImpl implements MetricsService {
    private static final Logger logger = LoggerFactory.getLogger(MetricsServiceImpl.class);

    private final MetricRegistry metrics = new MetricRegistry();

    private final GraphiteReporter reporter;

    private final Graphite graphite;

    private final Object LOCK = new Object();

    public MetricsServiceImpl(String graphiteServerDns, int graphitePort) {

        if (Strings.isNullOrEmpty(graphiteServerDns)) {
            throw new IllegalArgumentException("graphiteServerDns must not be null or empty");
        }

        logger.info("Setting up metrics service with graphite server dns: {}", graphiteServerDns);
        graphite = new Graphite(new InetSocketAddress(graphiteServerDns, graphitePort));

        reporter = GraphiteReporter.forRegistry(metrics)
                .prefixedWith("app.anshar")
                .convertRatesTo(TimeUnit.SECONDS)
                .convertDurationsTo(TimeUnit.MILLISECONDS)
                .filter(MetricFilter.ALL)
                .build(graphite);
    }

    @PostConstruct
    public void postConstruct() {
        logger.info("Starting graphite reporter");
        reporter.start(10, TimeUnit.SECONDS);
    }

    @PreDestroy
    public void shutdown() throws IOException {
        reporter.stop();
        if (graphite.isConnected()) {
            graphite.flush();
            graphite.close();
        }
    }


    @Override
    public void registerIncomingData(SiriDataType subscriptionType, String agencyId, Function<String, Integer> function) {
        String counterName = "data.type." + subscriptionType + ".codespace." + agencyId;
        if (!metrics.getGauges().containsKey(counterName)) {
            metrics.gauge(counterName, () -> () -> function.apply(agencyId));
        }
    }

    @Override
    public void registerSubscription(SubscriptionManager manager, SubscriptionSetup subscription) {
        String vendor = subscription.getVendor();

        String gauge_failing = "subscription." + vendor + ".failing" ;
        String gauge_data_failing = "subscription." + vendor + ".data_failing" ;

        //Flag as failing when ACTIVE, and NOT HEALTHY
        metrics.gauge(gauge_failing, () -> () -> manager.isActiveSubscription(subscription.getSubscriptionId()) &&
                                                 !manager.isSubscriptionHealthy(subscription.getSubscriptionId()));

        //Flag as data failing when ACTIVE, and NOT receiving data
        metrics.gauge(gauge_data_failing, () -> () -> manager.isActiveSubscription(subscription.getSubscriptionId()) &&
                                                 !manager.isSubscriptionReceivingData(subscription.getSubscriptionId(), 60));
    }
}
