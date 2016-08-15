package no.rutebanken.anshar.subscription;

import org.junit.Test;

import java.time.Duration;
import java.util.HashMap;
import java.util.UUID;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SubscriptionManagerTest {

    @Test
    public void activeSubscriptionIsHealthy() throws InterruptedException {
        SubscriptionSetup subscriptionSoonToExpire = createSubscription(1);
        SubscriptionManager.addSubscription("1234", subscriptionSoonToExpire);

        assertTrue(SubscriptionManager.isSubscriptionHealthy("1234"));

        Thread.sleep(1100);

        assertFalse(SubscriptionManager.isSubscriptionHealthy("1234"));
    }

    private SubscriptionSetup createSubscription(long initialDuration) {
        SubscriptionSetup sub = new SubscriptionSetup(
                SubscriptionSetup.SubscriptionType.SITUATION_EXCHANGE,
                "http://localhost",
                Duration.ofMinutes(1),
                "http://www.kolumbus.no/siri",
                new HashMap<>(),
                "1.4",
                "SwarcoMizar",
                SubscriptionSetup.ServiceType.SOAP,
                UUID.randomUUID().toString(),
                "RutebankenDEV", Duration.ofSeconds(initialDuration),
                true
        );
        return sub;
    }
}
