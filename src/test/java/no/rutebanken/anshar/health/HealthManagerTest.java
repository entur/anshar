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
}
