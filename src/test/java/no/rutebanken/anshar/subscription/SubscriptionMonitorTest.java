package no.rutebanken.anshar.subscription;


import no.rutebanken.anshar.App;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.util.Collection;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNotNull;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringBootTest(webEnvironment= SpringBootTest.WebEnvironment.MOCK, classes = App.class)
public class SubscriptionMonitorTest {

    @Autowired
    SubscriptionMonitor monitor;

    @Autowired
    Config config;

    @Autowired
    private SubscriptionManager subscriptionManager;

    @Test
    public void testUpdatedSubscriptions() {
        Collection<SubscriptionSetup> values = subscriptionManager.getPendingSubscriptions().values();
        assertEquals(1, values.size());
        SubscriptionSetup originalSetup = values.iterator().next();
        assertNotNull(originalSetup.getDatasetId());

        String updtedDatasetId = originalSetup.getDatasetId() + "1234";
        config.getSubscriptions().get(0).setDatasetId(updtedDatasetId);

        monitor.createSubscriptions();

        Collection<SubscriptionSetup> updatedValues = subscriptionManager.getPendingSubscriptions().values();
        assertEquals(1, updatedValues.size());
        SubscriptionSetup setup = updatedValues.iterator().next();
        assertEquals(updtedDatasetId, setup.getDatasetId());
    }
}
