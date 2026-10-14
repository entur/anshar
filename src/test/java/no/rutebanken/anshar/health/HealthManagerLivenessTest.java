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

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.LifecycleService;
import com.hazelcast.map.IMap;
import no.rutebanken.anshar.data.collections.ExtendedHazelcastService;
import no.rutebanken.anshar.routes.health.HealthManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Liveness must only depend on the local Hazelcast member - a distributed map operation blocks while the
 * partition owner is leaving the cluster, which makes the liveness probe kill healthy pods during rollouts.
 */
public class HealthManagerLivenessTest {

    private HealthManager healthManager;
    private LifecycleService lifecycleService;
    private IMap<?, ?> healthCheckMap;

    @BeforeEach
    public void setUp() {
        lifecycleService = mock(LifecycleService.class);
        HazelcastInstance hazelcastInstance = mock(HazelcastInstance.class);
        when(hazelcastInstance.getLifecycleService()).thenReturn(lifecycleService);
        ExtendedHazelcastService hazelcastService = mock(ExtendedHazelcastService.class);
        when(hazelcastService.getHazelcastInstance()).thenReturn(hazelcastInstance);
        healthCheckMap = mock(IMap.class);

        healthManager = new HealthManager();
        ReflectionTestUtils.setField(healthManager, "hazelcastService", hazelcastService);
        ReflectionTestUtils.setField(healthManager, "healthCheckMap", healthCheckMap);
    }

    @Test
    public void testAliveWhenLocalMemberIsRunning() {
        when(lifecycleService.isRunning()).thenReturn(true);
        assertTrue(healthManager.isHazelcastAlive());
    }

    @Test
    public void testNotAliveWhenLocalMemberIsShutDown() {
        when(lifecycleService.isRunning()).thenReturn(false);
        assertFalse(healthManager.isHazelcastAlive());
    }

    @Test
    public void testLivenessDoesNotUseDistributedMap() {
        when(lifecycleService.isRunning()).thenReturn(true);
        healthManager.isHazelcastAlive();
        verifyNoInteractions(healthCheckMap);
    }
}
