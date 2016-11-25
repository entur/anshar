package no.rutebanken.anshar.subscription;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.junit.Test;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;

import static org.junit.Assert.*;

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
    public void activeSubscriptionNoHeartbeat() throws InterruptedException {
        long subscriptionDurationSec = 180;
        SubscriptionSetup activeSubscription = createSubscription(subscriptionDurationSec, Duration.ofMillis(100));
        SubscriptionManager.addSubscription("1234", activeSubscription);

        assertTrue(SubscriptionManager.isSubscriptionHealthy("1234"));

        Thread.sleep(activeSubscription.getHeartbeatInterval().toMillis()*3+10);

        assertFalse(SubscriptionManager.isSubscriptionHealthy("1234"));
    }

    @Test
    public void pendingSubscriptionIsHealthy() throws InterruptedException {
        long subscriptionDurationSec = 1;
        SubscriptionSetup pendingSubscription = createSubscription(subscriptionDurationSec, Duration.ofMillis(100));
        SubscriptionManager.addPendingSubscription("1234", pendingSubscription);

        assertTrue(SubscriptionManager.isSubscriptionHealthy("1234"));

        Thread.sleep(pendingSubscription.getHeartbeatInterval().toMillis()*3+10);

        assertFalse(SubscriptionManager.isSubscriptionHealthy("1234"));
    }

    @Test
    public void testCheckStatusResponseOK() throws InterruptedException {
        long subscriptionDurationSec = 180;
        SubscriptionSetup subscription = createSubscription(subscriptionDurationSec);
        SubscriptionManager.addSubscription(subscription.getSubscriptionId(), subscription);

        ZonedDateTime serviceStartedTime = ZonedDateTime.now().minusMinutes(1);
        boolean touched = SubscriptionManager.touchSubscription(subscription.getSubscriptionId(), serviceStartedTime);
        assertTrue(touched);
        assertTrue(SubscriptionManager.isSubscriptionHealthy(subscription.getSubscriptionId()));

        serviceStartedTime = ZonedDateTime.now().plusMinutes(1);
        touched = SubscriptionManager.touchSubscription(subscription.getSubscriptionId(), serviceStartedTime);
        assertFalse(touched);
        assertFalse(SubscriptionManager.isSubscriptionHealthy(subscription.getSubscriptionId()));
    }

    @Test
    public void testAddSubscription() {
        SubscriptionSetup subscription = createSubscription(1);
        assertFalse("Subscription already marked as registered", SubscriptionManager.isSubscriptionRegistered(subscription.getSubscriptionId()));
        SubscriptionManager.addSubscription(subscription.getSubscriptionId(), subscription);

        assertTrue("Subscription not marked as registered", SubscriptionManager.isSubscriptionRegistered(subscription.getSubscriptionId()));
        assertTrue("Subscription not marked as active", SubscriptionManager.isActiveSubscription(subscription.getSubscriptionId()));
        assertFalse("Subscription marked as pending", SubscriptionManager.isPendingSubscription(subscription.getSubscriptionId()));


        assertNotNull("Subscription not found", SubscriptionManager.get(subscription.getSubscriptionId()));
    }

    @Test
    public void testAddAndActivatePendingSubscription() {
        SubscriptionSetup subscription = createSubscription(1);
        assertFalse("Unknown subscription has been found",SubscriptionManager.isSubscriptionRegistered(subscription.getSubscriptionId()));

        SubscriptionManager.addPendingSubscription(subscription.getSubscriptionId(), subscription);

        assertNotNull("Pending subscription not found", SubscriptionManager.get(subscription.getSubscriptionId()));

        assertTrue("Subscription not marked as registered", SubscriptionManager.isSubscriptionRegistered(subscription.getSubscriptionId()));
        assertTrue("Subscription not marked as pending", SubscriptionManager.isPendingSubscription(subscription.getSubscriptionId()));
        assertFalse("Subscription marked as active", SubscriptionManager.isActiveSubscription(subscription.getSubscriptionId()));
        assertTrue("Subscription not healthy", SubscriptionManager.isSubscriptionHealthy(subscription.getSubscriptionId()));

        assertTrue("Activating pending subscription not returning successfully", SubscriptionManager.activatePendingSubscription(subscription.getSubscriptionId()));

        //Activating already activated subscription should be ignored
        assertTrue("Activating already activated subscription not returning successfully", SubscriptionManager.activatePendingSubscription(subscription.getSubscriptionId()));

        assertTrue("Subscription not marked as registered", SubscriptionManager.isSubscriptionRegistered(subscription.getSubscriptionId()));
        assertTrue("Subscription not marked as active", SubscriptionManager.isActiveSubscription(subscription.getSubscriptionId()));
        assertFalse("Subscription marked as pending", SubscriptionManager.isPendingSubscription(subscription.getSubscriptionId()));
    }

    @Test
    public void testAddAndTouchPendingSubscription() {
        SubscriptionSetup subscription = createSubscription(1);
        assertFalse(SubscriptionManager.isSubscriptionRegistered(subscription.getSubscriptionId()));

        SubscriptionManager.addPendingSubscription(subscription.getSubscriptionId(), subscription);

        assertTrue("Subscription not marked as registered", SubscriptionManager.isSubscriptionRegistered(subscription.getSubscriptionId()));
        assertTrue("Subscription not marked as pending", SubscriptionManager.isPendingSubscription(subscription.getSubscriptionId()));
        assertFalse("Subscription marked as active", SubscriptionManager.isActiveSubscription(subscription.getSubscriptionId()));

        assertTrue("Subscription not healthy", SubscriptionManager.isSubscriptionHealthy(subscription.getSubscriptionId()));


        SubscriptionManager.touchSubscription(subscription.getSubscriptionId());

        assertTrue("Subscription not marked as registered", SubscriptionManager.isSubscriptionRegistered(subscription.getSubscriptionId()));
        assertTrue("Subscription not marked as active", SubscriptionManager.isActiveSubscription(subscription.getSubscriptionId()));
        assertFalse("Subscription marked as pending", SubscriptionManager.isPendingSubscription(subscription.getSubscriptionId()));
    }

    @Test
    public void testRemoveSubscription() {
        SubscriptionSetup subscription = createSubscription(1);
        assertFalse(SubscriptionManager.isSubscriptionRegistered(subscription.getSubscriptionId()));

        SubscriptionManager.addSubscription(subscription.getSubscriptionId(), subscription);

        assertTrue("Subscription not registered", SubscriptionManager.isSubscriptionRegistered(subscription.getSubscriptionId()));
        assertFalse("Subscription marked as pending", SubscriptionManager.isPendingSubscription(subscription.getSubscriptionId()));
        assertTrue("Subscription not marked as active", SubscriptionManager.isActiveSubscription(subscription.getSubscriptionId()));

        SubscriptionManager.removeSubscription(subscription.getSubscriptionId());
        assertFalse("Removed subscription marked as active", SubscriptionManager.isActiveSubscription(subscription.getSubscriptionId()));
    }

    @Test
    public void testStatObjectCounterHugeNumber() {
        SubscriptionSetup subscription = createSubscription(1);
        assertFalse(SubscriptionManager.isSubscriptionRegistered(subscription.getSubscriptionId()));

        SubscriptionManager.addSubscription(subscription.getSubscriptionId(), subscription);

        for (int i = 0; i < 10; i++) {
            SubscriptionManager.incrementObjectCounter(subscription, Integer.MAX_VALUE);
        }

        JSONObject jsonObject = SubscriptionManager.buildStats();
        assertNotNull(jsonObject.get("subscriptions"));
        assertTrue(jsonObject.get("subscriptions") instanceof JSONArray);

        JSONArray subscriptions = (JSONArray) jsonObject.get("subscriptions");
        assertTrue(subscriptions.size() > 0);

        JSONObject jsonStats = (JSONObject) subscriptions.get(0);

        assertNotNull(jsonStats.get("objectcount"));
    }

    @Test
    public void testStatObjectCounter() {
        SubscriptionSetup subscription = createSubscription(1);
        assertFalse(SubscriptionManager.isSubscriptionRegistered(subscription.getSubscriptionId()));

        SubscriptionManager.addSubscription(subscription.getSubscriptionId(), subscription);

        int sum = 0;
        int increment = 999;
        for (int i = 1; i < 10;i++) {
            sum += increment;
            SubscriptionManager.incrementObjectCounter(subscription, increment);
        }

        JSONObject jsonObject = SubscriptionManager.buildStats();
        assertNotNull(jsonObject.get("subscriptions"));
        assertTrue(jsonObject.get("subscriptions") instanceof JSONArray);

        JSONArray subscriptions = (JSONArray) jsonObject.get("subscriptions");
        assertTrue(subscriptions.size() > 0);

        JSONObject jsonStats = (JSONObject) subscriptions.get(0);

        assertEquals("" + sum, "" + jsonStats.get("objectcount"));
    }

    @Test
    public void testIsSubscriptionRegistered() {

        assertFalse("Unknown subscription has been activated", SubscriptionManager.activatePendingSubscription("RandomSubscriptionId"));
        assertFalse("Unknown subscription reported as registered", SubscriptionManager.isSubscriptionRegistered("RandomSubscriptionId"));
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
                new ArrayList<>(),
                new HashMap<>(),
                new ArrayList<>(),
                UUID.randomUUID().toString(),
                "RutebankenDEV",
                Duration.ofSeconds(initialDuration),
                true
        );
        return sub;
    }

    private SubscriptionSetup createSubscription(long initialDuration, Duration heartbeatInterval) {
        SubscriptionSetup sub = new SubscriptionSetup(
                SubscriptionSetup.SubscriptionType.SITUATION_EXCHANGE,
                SubscriptionSetup.SubscriptionMode.SUBSCRIBE,
                "http://localhost",
                heartbeatInterval,
                "http://www.kolumbus.no/siri",
                new HashMap<>(),
                "1.4",
                "SwarcoMizar",
                "tst",
                SubscriptionSetup.ServiceType.SOAP,
                new ArrayList<>(),
                new HashMap<>(),
                new ArrayList<>(),
                UUID.randomUUID().toString(),
                "RutebankenDEV",
                Duration.ofSeconds(initialDuration),
                true
        );
        return sub;
    }
}
