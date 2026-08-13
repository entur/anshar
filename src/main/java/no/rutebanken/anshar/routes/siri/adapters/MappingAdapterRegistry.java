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

package no.rutebanken.anshar.routes.siri.adapters;

import jakarta.annotation.PostConstruct;
import no.rutebanken.anshar.config.AnsharConfiguration;
import no.rutebanken.anshar.routes.siri.processor.AddOrderToAllCallsPostProcessor;
import no.rutebanken.anshar.routes.siri.processor.CleanupInfoLinksPostProcessor;
import no.rutebanken.anshar.routes.siri.processor.CodespaceBlackListProcessor;
import no.rutebanken.anshar.routes.siri.processor.CodespaceProcessor;
import no.rutebanken.anshar.routes.siri.processor.CodespaceWhiteListProcessor;
import no.rutebanken.anshar.routes.siri.processor.EnsureIncreasingTimesForCancelledStopsProcessor;
import no.rutebanken.anshar.routes.siri.processor.EnsureNonNullVehicleModePostProcessor;
import no.rutebanken.anshar.routes.siri.processor.ExtraJourneyDestinationDisplayPostProcessor;
import no.rutebanken.anshar.routes.siri.processor.ExtraJourneyPostProcessor;
import no.rutebanken.anshar.routes.siri.processor.LimitClosedProgressValidityPostProcessor;
import no.rutebanken.anshar.routes.siri.processor.RemovePersonalInformationProcessor;
import no.rutebanken.anshar.routes.siri.processor.ReportTypeProcessor;
import no.rutebanken.anshar.routes.siri.transformer.ValueAdapter;
import no.rutebanken.anshar.subscription.SiriDataType;
import no.rutebanken.anshar.subscription.SubscriptionSetup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceConfigurationError;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves - and caches - the ValueAdapters/PostProcessors to apply for a subscription.
 * <p>
 * The adapters are pure code, derived from the subscription-config. They are deliberately resolved
 * from the locally deployed code and kept in a node-local cache: if they were read from the
 * distributed - and therefore cluster-wide shared - {@code SubscriptionSetup}, a pod running new
 * code would inherit the adapters registered by the pods running the previous version, and
 * added/changed adapters would not take effect until the entire cluster had been stopped.
 * <p>
 * Resolving locally means a rolling upgrade is sufficient: each pod applies the rules it was built
 * with to the data it handles itself.
 */
@Service
public class MappingAdapterRegistry {

    private final Logger logger = LoggerFactory.getLogger(MappingAdapterRegistry.class);

    @Autowired
    private AnsharConfiguration configuration;

    @Autowired
    private ApplicationContext applicationContext;

    /**
     * Node-local - NOT distributed. Adapters must never be shared between pods.
     */
    private final Map<String, List<ValueAdapter>> adaptersBySubscriptionId = new ConcurrentHashMap<>();

    private final Map<String, MappingAdapter> mappingAdaptersById = new HashMap<>();

    @PostConstruct
    void initMappingAdapters() {
        if (configuration.isDisableAllMappingAdapters()) {
            logger.info("All mapping adapters are disabled");
            return;
        }
        final Map<String, Object> mappingBeans = applicationContext.getBeansWithAnnotation(Mapping.class);
        for (final Object mappingBean : mappingBeans.values()) {
            final Mapping annotation = mappingBean.getClass().getAnnotation(Mapping.class);
            mappingAdaptersById.put(annotation.id(), (MappingAdapter) mappingBean);
        }
    }

    /**
     * Returns the adapters to apply for the given subscription, resolving them on first use.
     */
    public List<ValueAdapter> getAdapters(SubscriptionSetup subscriptionSetup) {
        return adaptersBySubscriptionId.computeIfAbsent(
                subscriptionSetup.getSubscriptionId(),
                subscriptionId -> resolve(subscriptionSetup)
        );
    }

    /**
     * Resolves the adapters for the given subscription and replaces any cached value.
     * <p>
     * Called while initializing subscriptions to make invalid config fail on startup instead of
     * when the first message arrives.
     */
    public List<ValueAdapter> resolveAdapters(SubscriptionSetup subscriptionSetup) {
        List<ValueAdapter> adapters = resolve(subscriptionSetup);
        adaptersBySubscriptionId.put(subscriptionSetup.getSubscriptionId(), adapters);
        return adapters;
    }

    private List<ValueAdapter> resolve(SubscriptionSetup subscriptionSetup) {
        if (configuration.isDisableAllMappingAdapters()) {
            return List.of();
        }

        // Vendor-specific adapters append the id-prefix-adapters directly to the subscription's own
        // list, and these are to be applied BEFORE the adapters returned from getValueAdapters().
        // Using that list as accumulator keeps the resulting order intact.
        List<ValueAdapter> accumulator = subscriptionSetup.getMappingAdapters();
        accumulator.clear();

        List<ValueAdapter> valueAdapters = new ArrayList<>();

        MappingAdapter mappingAdapter = mappingAdaptersById.get(subscriptionSetup.getMappingAdapterId());
        if (mappingAdapter != null) {
            try {
                valueAdapters.addAll(mappingAdapter.getValueAdapters(subscriptionSetup));
            } catch (Exception e) {
                throw new ServiceConfigurationError("Invalid mappingAdapterId for subscription " + subscriptionSetup, e);
            }
        }

        //Is added to ALL subscriptions AFTER subscription-specific adapters
        if (!subscriptionSetup.isUseProvidedCodespaceId()) {
            valueAdapters.add(new CodespaceProcessor(subscriptionSetup.getDatasetId()));
        }

        // SX
        if (subscriptionSetup.getSubscriptionType() == SiriDataType.SITUATION_EXCHANGE) {
            valueAdapters.add(new ReportTypeProcessor(subscriptionSetup.getDatasetId()));
            valueAdapters.add(new RemovePersonalInformationProcessor());
            valueAdapters.add(new LimitClosedProgressValidityPostProcessor(subscriptionSetup.getDatasetId()));
            valueAdapters.add(new CleanupInfoLinksPostProcessor(subscriptionSetup.getDatasetId()));
        }

        // ET
        if (subscriptionSetup.getSubscriptionType() == SiriDataType.ESTIMATED_TIMETABLE) {
            valueAdapters.add(new EnsureIncreasingTimesForCancelledStopsProcessor(subscriptionSetup.getDatasetId()));
            valueAdapters.add(new ExtraJourneyDestinationDisplayPostProcessor(subscriptionSetup.getDatasetId()));
            valueAdapters.add(new AddOrderToAllCallsPostProcessor(subscriptionSetup.getDatasetId()));
            valueAdapters.add(new EnsureNonNullVehicleModePostProcessor());
            valueAdapters.add(new ExtraJourneyPostProcessor(subscriptionSetup.getDatasetId()));
        }

        if (!subscriptionSetup.getCodespaceWhiteList().isEmpty()) {
            valueAdapters.add(new CodespaceWhiteListProcessor(subscriptionSetup.getDatasetId(), subscriptionSetup.getCodespaceWhiteList()));
        }
        if (!subscriptionSetup.getCodespaceBlackList().isEmpty()) {
            valueAdapters.add(new CodespaceBlackListProcessor(subscriptionSetup.getDatasetId(), subscriptionSetup.getCodespaceBlackList()));
        }

        accumulator.addAll(valueAdapters);

        logger.debug("Resolved {} adapters for subscription {}", accumulator.size(), subscriptionSetup);

        return List.copyOf(accumulator);
    }
}
