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

package no.rutebanken.anshar.health;

import no.rutebanken.anshar.data.collections.ExtendedHazelcastService;
import no.rutebanken.anshar.integration.SpringBootBaseTest;
import no.rutebanken.anshar.routes.health.HealthManager;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;

import no.rutebanken.anshar.subscription.SiriDataType;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class HealthManagerTest extends SpringBootBaseTest {

    @Autowired
    private HealthManager healthManager;

    @Autowired
    private ExtendedHazelcastService extendedHazelcastService;

    /*
     * Test is ignored as it shuts down entire hazelcast-instance causing multiple tests to fail
     */
    @Test
    @Disabled
    public void testShutDownDiscovered() {
        assertTrue(healthManager.isHazelcastAlive());
        extendedHazelcastService.shutdown();
        assertFalse(healthManager.isHazelcastAlive());
    }

    @Test
    public void testUnmappedIdsAreLimited() {
        final String datasetId = "unmapped-limit-test";
        final Object originalLimit = ReflectionTestUtils.getField(healthManager, "maxUnmappedIds");
        ReflectionTestUtils.setField(healthManager, "maxUnmappedIds", 3);
        try {
            for (int i = 0; i < 10; i++) {
                healthManager.addUnmappedId(SiriDataType.ESTIMATED_TIMETABLE, datasetId, "id-" + i);
            }
            assertEquals(3, healthManager.getUnmappedIds(datasetId).get(SiriDataType.ESTIMATED_TIMETABLE).size());

            // An id that did not fit is added when there is room
            healthManager.removeUnmappedId(SiriDataType.ESTIMATED_TIMETABLE, datasetId, "id-0");
            assertEquals(3, healthManager.getUnmappedIds(datasetId).get(SiriDataType.ESTIMATED_TIMETABLE).size());
            assertFalse(healthManager.getUnmappedIds(datasetId).get(SiriDataType.ESTIMATED_TIMETABLE).contains("id-0"));
        } finally {
            ReflectionTestUtils.setField(healthManager, "maxUnmappedIds", originalLimit);
            healthManager.clearUnmappedIds(datasetId);
        }
    }
}
