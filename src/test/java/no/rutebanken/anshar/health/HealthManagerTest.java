package no.rutebanken.anshar.health;

import no.rutebanken.anshar.App;
import no.rutebanken.anshar.data.collections.ExtendedHazelcastService;
import no.rutebanken.anshar.routes.health.HealthManager;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertTrue;


@RunWith(SpringJUnit4ClassRunner.class)
@SpringBootTest(webEnvironment= SpringBootTest.WebEnvironment.MOCK, classes = App.class)
public class HealthManagerTest {

    @Autowired
    private HealthManager healthManager;

    @Autowired
    private ExtendedHazelcastService extendedHazelcastService;

    /*
     * Test is ignored as it shuts down entire hazelcast-instance causing ultiple tests to fail
     */
    @Test
    @Ignore
    public void testShutDownDiscovered() {
        assertTrue(healthManager.isHazelcastAlive());
        extendedHazelcastService.shutdown();
        assertFalse(healthManager.isHazelcastAlive());
    }
}
