package no.rutebanken.anshar.subscription;

import org.junit.Test;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.UUID;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SubscriptionManagerTest {

    @Test
    public void activeSubscriptionIsHealthy() throws InterruptedException {
        long subscriptionDurationSec = 1;
        SubscriptionSetup subscriptionSoonToExpire = createSubscription(subscriptionDurationSec);
        SubscriptionManager.addSubscription("1234", subscriptionSoonToExpire);

        assertTrue(SubscriptionManager.isSubscriptionHealthy("1234"));

        Thread.sleep(1100);

        assertFalse(SubscriptionManager.isSubscriptionHealthy("1234"));
    }

    @Test
    public void testCheckStatusResponseOK() throws InterruptedException {
        long subscriptionDurationSec = 180;
        SubscriptionSetup subscription = createSubscription(subscriptionDurationSec);
        SubscriptionManager.addSubscription(subscription.getSubscriptionId(), subscription);

        boolean touched = SubscriptionManager.touchSubscription(subscription.getSubscriptionId(), ZonedDateTime.now().minusMinutes(1));
        assertTrue(touched);
        assertTrue(SubscriptionManager.isSubscriptionHealthy(subscription.getSubscriptionId()));

        touched = SubscriptionManager.touchSubscription(subscription.getSubscriptionId(), ZonedDateTime.now().plusMinutes(1));
        assertFalse(touched);
        assertFalse(SubscriptionManager.isSubscriptionHealthy(subscription.getSubscriptionId()));
    }


    private SubscriptionSetup createSubscription(long initialDuration) {
        SubscriptionSetup sub = new SubscriptionSetup(
                SubscriptionSetup.SubscriptionType.SITUATION_EXCHANGE,
                SubscriptionSetup.SubscriptionMode.SUBSCRIBE,
                "http://localhost",
                Duration.ofMinutes(1),
                "http://www.kolumbus.no/siri",
                new HashMap<>(),
                "1.4",
                "SwarcoMizar",
                "tst",
                SubscriptionSetup.ServiceType.SOAP,
                UUID.randomUUID().toString(),
                "RutebankenDEV", Duration.ofSeconds(initialDuration),
                true
        );
        return sub;
    }
}
