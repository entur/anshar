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

import com.hazelcast.core.IMap;
import io.micrometer.core.instrument.ImmutableTag;
import io.micrometer.core.instrument.Tag;
import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import no.rutebanken.anshar.subscription.SiriDataType;
import no.rutebanken.anshar.subscription.SubscriptionManager;
import no.rutebanken.anshar.subscription.SubscriptionSetup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.IOException;
import java.util.*;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;

public class PrometheusMetricsServiceImpl extends PrometheusMeterRegistry {
    private static final Logger logger = LoggerFactory.getLogger(PrometheusMetricsServiceImpl.class);

    private final String METRICS_PREFIX = "app.anshar.";
    protected SubscriptionManager manager;

    public PrometheusMetricsServiceImpl(PrometheusConfig config) {
        super(config);
    }

    public void setSubscriptionManager(SubscriptionManager manager) {
        this.manager = manager;
    }

    @PreDestroy
    public void shutdown() throws IOException {
        this.close();
    }

    final static Map<String, Integer> gaugeValues = new HashMap<>();

    public void registerIncomingData(SiriDataType subscriptionType, String agencyId, Function<String, Integer> function) {
        String counterName = METRICS_PREFIX + "data.type";

        List<Tag> counterTags = new ArrayList<>();
        counterTags.add(new ImmutableTag("dataType", subscriptionType.name()));
        counterTags.add(new ImmutableTag("agency", agencyId));

        gaugeValues.put(counterName, function.apply(agencyId));

        gauge(counterName, counterTags, gaugeValues, (name) -> gaugeValues.get(counterName));
    }

    public void update() {
        long t1 = System.currentTimeMillis();
        IMap<String, SubscriptionSetup> subscriptions = manager.subscriptions;
        for (SubscriptionSetup subscription : subscriptions.values()) {

            String vendor = subscription.getVendor();
            String datasetId = subscription.getDatasetId();
            SiriDataType subscriptionType = subscription.getSubscriptionType();

            //e.g.: subscription.ET.RUT.ruterEt.failing
            String gauge_baseName = METRICS_PREFIX + "subscription";//." + subscriptionType + "." + datasetId + "." + vendor;

            String gauge_failing = gauge_baseName + ".failing";
            String gauge_data_failing = gauge_baseName + ".data_failing" ;


            List<Tag> counterTags = new ArrayList<>();
            counterTags.add(new ImmutableTag("dataType", subscriptionType.name()));
            counterTags.add(new ImmutableTag("agency", subscription.getDatasetId()));


            //Flag as failing when ACTIVE, and NOT HEALTHY
            gauge(gauge_failing, counterTags, (manager.isActiveSubscription(subscription.getSubscriptionId()) &&
                    !manager.isSubscriptionHealthy(subscription.getSubscriptionId())) ? 1 : 0);

            //Set flag as data failing when ACTIVE, and NOT receiving data

            gauge(gauge_data_failing + ".5min", getTagsWithTimeLimit(counterTags, "5min"), subscription.getSubscriptionId(), value ->
                    isSubscriptionFailing(manager, subscription, 5*60));

            gauge(gauge_data_failing + ".15min", getTagsWithTimeLimit(counterTags, "15min"), subscription.getSubscriptionId(), value ->
                    isSubscriptionFailing(manager, subscription, 15*60));

            gauge(gauge_data_failing + ".30min", getTagsWithTimeLimit(counterTags, "30min"), subscription.getSubscriptionId(), value ->
                    isSubscriptionFailing(manager, subscription, 30*60));
        }
        logger.info("Calculating metrics took {} ms", (System.currentTimeMillis()-t1));
    }

    private List<Tag> getTagsWithTimeLimit(List<Tag> counterTags, String timeLimit) {
        List<Tag> counterTagsClone = new ArrayList<>(counterTags);
        counterTagsClone.add(new ImmutableTag("timelimit", timeLimit));
        return counterTagsClone;
    }

    private double isSubscriptionFailing(SubscriptionManager manager, SubscriptionSetup subscription, int allowedSeconds) {
        if (manager.isActiveSubscription(subscription.getSubscriptionId()) &&
                !manager.isSubscriptionReceivingData(subscription.getSubscriptionId(), allowedSeconds)) {
            return 1;
        }
        return 0;
    }
}
