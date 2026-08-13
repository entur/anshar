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

import no.rutebanken.anshar.integration.SpringBootBaseTest;
import no.rutebanken.anshar.routes.siri.processor.CleanupInfoLinksPostProcessor;
import no.rutebanken.anshar.routes.siri.processor.CodespaceProcessor;
import no.rutebanken.anshar.routes.siri.processor.ExtraJourneyPostProcessor;
import no.rutebanken.anshar.routes.siri.processor.RemovePersonalInformationProcessor;
import no.rutebanken.anshar.routes.siri.transformer.ValueAdapter;
import no.rutebanken.anshar.routes.siri.transformer.impl.LeftPaddingAdapter;
import no.rutebanken.anshar.routes.siri.transformer.impl.PrefixAdapter;
import no.rutebanken.anshar.subscription.SiriDataType;
import no.rutebanken.anshar.subscription.SubscriptionSetup;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MappingAdapterRegistryTest extends SpringBootBaseTest {

    private static final String DATASET_ID = "TST";

    @Autowired
    private MappingAdapterRegistry registry;

    @Test
    public void testSxProcessorsAreResolved() {
        List<ValueAdapter> adapters = registry.getAdapters(createSubscriptionSetup("sx", SiriDataType.SITUATION_EXCHANGE));

        assertTrue(containsType(adapters, RemovePersonalInformationProcessor.class));
        assertTrue(containsType(adapters, CleanupInfoLinksPostProcessor.class));
        assertTrue(containsType(adapters, CodespaceProcessor.class));
        assertFalse(containsType(adapters, ExtraJourneyPostProcessor.class), "ET-processor added to SX-subscription");
    }

    @Test
    public void testEtProcessorsAreResolved() {
        List<ValueAdapter> adapters = registry.getAdapters(createSubscriptionSetup("et", SiriDataType.ESTIMATED_TIMETABLE));

        assertTrue(containsType(adapters, ExtraJourneyPostProcessor.class));
        assertFalse(containsType(adapters, CleanupInfoLinksPostProcessor.class), "SX-processor added to ET-subscription");
    }

    /**
     * The adapters kept on the SubscriptionSetup may have been registered by a pod running a
     * previous version of the code - they must never end up being applied.
     */
    @Test
    public void testAdaptersAlreadyOnSubscriptionSetupAreIgnored() {
        SubscriptionSetup subscriptionSetup = createSubscriptionSetup("stale", SiriDataType.SITUATION_EXCHANGE);
        subscriptionSetup.getMappingAdapters().add(new AdapterFromPreviousVersion());

        List<ValueAdapter> adapters = registry.getAdapters(subscriptionSetup);

        assertFalse(containsType(adapters, AdapterFromPreviousVersion.class));
        assertTrue(containsType(adapters, CleanupInfoLinksPostProcessor.class));
    }

    @Test
    public void testAdaptersAreCachedPerSubscription() {
        SubscriptionSetup subscriptionSetup = createSubscriptionSetup("cached", SiriDataType.SITUATION_EXCHANGE);

        assertSame(registry.getAdapters(subscriptionSetup), registry.getAdapters(subscriptionSetup));
    }

    @Test
    public void testRepeatedResolvingDoesNotDuplicateAdapters() {
        SubscriptionSetup subscriptionSetup = createSubscriptionSetup("repeated", SiriDataType.ESTIMATED_TIMETABLE);
        subscriptionSetup.setMappingAdapterId("nsb");

        int adapterCount = registry.resolveAdapters(subscriptionSetup).size();

        assertEquals(adapterCount, registry.resolveAdapters(subscriptionSetup).size());
    }

    /**
     * Vendor-specific adapters add the id-prefix-adapters directly to the SubscriptionSetup, and
     * these are to be applied before the ones returned from getValueAdapters().
     */
    @Test
    public void testVendorPrefixAdaptersAreAppliedFirst() {
        SubscriptionSetup subscriptionSetup = createSubscriptionSetup("vendor", SiriDataType.ESTIMATED_TIMETABLE);
        subscriptionSetup.setMappingAdapterId("nsb");

        List<ValueAdapter> adapters = registry.getAdapters(subscriptionSetup);

        int firstPrefixAdapter = indexOfType(adapters, PrefixAdapter.class);
        int firstVendorAdapter = indexOfType(adapters, LeftPaddingAdapter.class);

        assertTrue(firstPrefixAdapter >= 0, "No PrefixAdapter resolved for vendor-subscription");
        assertTrue(firstVendorAdapter >= 0, "No vendor-specific adapter resolved for vendor-subscription");
        assertTrue(firstPrefixAdapter < firstVendorAdapter,
                "PrefixAdapters must be applied before the vendor-specific adapters");
    }

    private boolean containsType(List<ValueAdapter> adapters, Class<? extends ValueAdapter> type) {
        return indexOfType(adapters, type) >= 0;
    }

    private int indexOfType(List<ValueAdapter> adapters, Class<? extends ValueAdapter> type) {
        for (int i = 0; i < adapters.size(); i++) {
            if (type.isInstance(adapters.get(i))) {
                return i;
            }
        }
        return -1;
    }

    private SubscriptionSetup createSubscriptionSetup(String subscriptionId, SiriDataType dataType) {
        SubscriptionSetup subscriptionSetup = new SubscriptionSetup();
        subscriptionSetup.setSubscriptionId(getClass().getSimpleName() + ":" + subscriptionId);
        subscriptionSetup.setSubscriptionType(dataType);
        subscriptionSetup.setDatasetId(DATASET_ID);
        subscriptionSetup.setIdMappingPrefixes(List.of());
        return subscriptionSetup;
    }

    private static class AdapterFromPreviousVersion extends ValueAdapter {
        @Override
        protected String apply(String value) {
            return value;
        }
    }
}
