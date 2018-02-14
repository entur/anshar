package no.rutebanken.anshar.subscription;

import no.rutebanken.anshar.App;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;
import java.util.UUID;

import static org.junit.Assert.*;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringBootTest(webEnvironment= SpringBootTest.WebEnvironment.MOCK, classes = App.class)
public class SubscriptionManagerTest {

    @Autowired
    private SubscriptionManager subscriptionManager;

    @Test
    public void activeSubscriptionIsHealthy() throws InterruptedException {
        long subscriptionDurationSec = 1;
        SubscriptionSetup subscriptionSoonToExpire = createSubscription(subscriptionDurationSec);
        String subscriptionId = UUID.randomUUID().toString();
        subscriptionManager.addSubscription(subscriptionId, subscriptionSoonToExpire);
        subscriptionManager.activatePendingSubscription(subscriptionId);
        subscriptionManager.touchSubscription(subscriptionId);

        assertTrue(subscriptionManager.isSubscriptionHealthy(subscriptionId));

        Thread.sleep(1000*subscriptionDurationSec * subscriptionManager.HEALTHCHECK_INTERVAL_FACTOR + 150);

        assertFalse(subscriptionManager.isSubscriptionHealthy(subscriptionId));
    }

    @Test
    public void activeSubscriptionNoHeartbeat() throws InterruptedException {
        long subscriptionDurationSec = 180;
        SubscriptionSetup activeSubscription = createSubscription(subscriptionDurationSec, Duration.ofMillis(150));
        String subscriptionId = UUID.randomUUID().toString();
        subscriptionManager.addSubscription(subscriptionId, activeSubscription);
        subscriptionManager.activatePendingSubscription(subscriptionId);
        subscriptionManager.touchSubscription(subscriptionId);

        assertTrue(subscriptionManager.isSubscriptionHealthy(subscriptionId));

        Thread.sleep(activeSubscription.getHeartbeatInterval().toMillis()*subscriptionManager.HEALTHCHECK_INTERVAL_FACTOR+150);

        assertFalse(subscriptionManager.isSubscriptionHealthy(subscriptionId));
    }

    @Test
    public void pendingSubscriptionIsHealthy() throws InterruptedException {
        long subscriptionDurationSec = 1;
        SubscriptionSetup pendingSubscription = createSubscription(subscriptionDurationSec, Duration.ofMillis(150));
        pendingSubscription.setActive(false);
        String subscriptionId = UUID.randomUUID().toString();
        subscriptionManager.addSubscription(subscriptionId, pendingSubscription);

        subscriptionManager.activatePendingSubscription(subscriptionId);
        subscriptionManager.touchSubscription(subscriptionId);

        assertTrue(subscriptionManager.isSubscriptionHealthy(subscriptionId));

        Thread.sleep(pendingSubscription.getHeartbeatInterval().toMillis()*subscriptionManager.HEALTHCHECK_INTERVAL_FACTOR+150);

        assertFalse(subscriptionManager.isSubscriptionHealthy(subscriptionId));
    }

    @Test
    public void notStartedSubscriptionIsHealthy() throws InterruptedException {

        long subscriptionDurationSec = 1;
        SubscriptionSetup pendingSubscription = createSubscription(subscriptionDurationSec, Duration.ofMillis(150));
        pendingSubscription.setActive(false);
        String subscriptionId = UUID.randomUUID().toString();
        subscriptionManager.addSubscription(subscriptionId, pendingSubscription);

        assertTrue(subscriptionManager.isSubscriptionHealthy(subscriptionId));

        Thread.sleep(pendingSubscription.getHeartbeatInterval().toMillis()* subscriptionManager.HEALTHCHECK_INTERVAL_FACTOR + 150);

        assertTrue(subscriptionManager.isSubscriptionHealthy(subscriptionId));
    }

    @Test
    public void testCheckStatusResponseOK() throws InterruptedException {
        long subscriptionDurationSec = 180;
        SubscriptionSetup subscription = createSubscription(subscriptionDurationSec);
        subscriptionManager.addSubscription(subscription.getSubscriptionId(), subscription);
        subscriptionManager.activatePendingSubscription(subscription.getSubscriptionId());

        ZonedDateTime serviceStartedTime = ZonedDateTime.now().minusMinutes(1);
        boolean touched = subscriptionManager.touchSubscription(subscription.getSubscriptionId(), serviceStartedTime);
        assertTrue(touched);
        assertTrue(subscriptionManager.isSubscriptionHealthy(subscription.getSubscriptionId()));

    }

    @Test
    public void testAddSubscription() {
        SubscriptionSetup subscription = createSubscription(1);
        assertFalse("Subscription already marked as registered", subscriptionManager.isSubscriptionRegistered(subscription.getSubscriptionId()));
        subscriptionManager.addSubscription(subscription.getSubscriptionId(), subscription);
        subscriptionManager.activatePendingSubscription(subscription.getSubscriptionId());

        assertTrue("Subscription not marked as registered", subscriptionManager.isSubscriptionRegistered(subscription.getSubscriptionId()));
        assertTrue("Subscription not marked as active", subscriptionManager.isActiveSubscription(subscription.getSubscriptionId()));


        assertNotNull("Subscription not found", subscriptionManager.get(subscription.getSubscriptionId()));
    }

    @Test
    public void testAddAndActivatePendingSubscription() {
        SubscriptionSetup subscription = createSubscription(1);
        assertFalse("Unknown subscription has been found",subscriptionManager.isSubscriptionRegistered(subscription.getSubscriptionId()));
        subscription.setActive(false);

        subscriptionManager.addSubscription(subscription.getSubscriptionId(), subscription);

        assertNotNull("Pending subscription not found", subscriptionManager.get(subscription.getSubscriptionId()));

        assertTrue("Subscription not marked as registered", subscriptionManager.isSubscriptionRegistered(subscription.getSubscriptionId()));
        assertFalse("Subscription marked as active", subscriptionManager.isActiveSubscription(subscription.getSubscriptionId()));
        assertTrue("Subscription not healthy", subscriptionManager.isSubscriptionHealthy(subscription.getSubscriptionId()));

        assertTrue("Activating pending subscription not returning successfully", subscriptionManager.activatePendingSubscription(subscription.getSubscriptionId()));

        //Activating already activated subscription should be ignored
        assertTrue("Activating already activated subscription not returning successfully", subscriptionManager.activatePendingSubscription(subscription.getSubscriptionId()));

        assertTrue("Subscription not marked as registered", subscriptionManager.isSubscriptionRegistered(subscription.getSubscriptionId()));
        assertTrue("Subscription not marked as active", subscriptionManager.isActiveSubscription(subscription.getSubscriptionId()));
    }

    @Test
    public void testAddAndTouchPendingSubscription() {
        SubscriptionSetup subscription = createSubscription(1);
        subscription.setActive(false);
        assertFalse(subscriptionManager.isSubscriptionRegistered(subscription.getSubscriptionId()));

        subscriptionManager.addSubscription(subscription.getSubscriptionId(), subscription);

        assertTrue("Subscription not marked as registered", subscriptionManager.isSubscriptionRegistered(subscription.getSubscriptionId()));
        assertFalse("Subscription marked as active", subscriptionManager.isActiveSubscription(subscription.getSubscriptionId()));

        assertTrue("Subscription not healthy", subscriptionManager.isSubscriptionHealthy(subscription.getSubscriptionId()));

        subscriptionManager.touchSubscription(subscription.getSubscriptionId());

        assertTrue("Subscription not marked as registered", subscriptionManager.isSubscriptionRegistered(subscription.getSubscriptionId()));
        assertFalse("Subscription marked as active", subscriptionManager.isActiveSubscription(subscription.getSubscriptionId()));
    }

    @Test
    public void testRemoveSubscription() {
        SubscriptionSetup subscription = createSubscription(1);
        assertFalse(subscriptionManager.isSubscriptionRegistered(subscription.getSubscriptionId()));

        subscriptionManager.addSubscription(subscription.getSubscriptionId(), subscription);
        subscriptionManager.activatePendingSubscription(subscription.getSubscriptionId());

        assertTrue("Subscription not registered", subscriptionManager.isSubscriptionRegistered(subscription.getSubscriptionId()));
        assertTrue("Subscription not marked as active", subscriptionManager.isActiveSubscription(subscription.getSubscriptionId()));

        subscriptionManager.removeSubscription(subscription.getSubscriptionId());
        assertFalse("Removed subscription marked as active", subscriptionManager.isActiveSubscription(subscription.getSubscriptionId()));
    }

    @Test
    public void testForceRemoveSubscription() {
        SubscriptionSetup subscription = createSubscription(1);
        assertFalse(subscriptionManager.isSubscriptionRegistered(subscription.getSubscriptionId()));

        subscriptionManager.addSubscription(subscription.getSubscriptionId(), subscription);
        subscriptionManager.activatePendingSubscription(subscription.getSubscriptionId());

        subscriptionManager.removeSubscription(subscription.getSubscriptionId(), true);
        assertFalse("Removed subscription marked as active", subscriptionManager.isActiveSubscription(subscription.getSubscriptionId()));
    }

    @Test
    public void testStatsObjectCounterHugeNumber() {
        SubscriptionSetup subscription = createSubscription(1);
        assertFalse(subscriptionManager.isSubscriptionRegistered(subscription.getSubscriptionId()));

        subscriptionManager.addSubscription(subscription.getSubscriptionId(), subscription);
        subscriptionManager.activatePendingSubscription(subscription.getSubscriptionId());

        for (int i = 0; i < 10; i++) {
            subscriptionManager.incrementObjectCounter(subscription, Integer.MAX_VALUE);
        }

        JSONObject jsonObject = subscriptionManager.buildStats();
        assertNotNull(jsonObject.get("subscriptions"));
        assertTrue(jsonObject.get("subscriptions") instanceof JSONArray);

        JSONArray subscriptions = (JSONArray) jsonObject.get("subscriptions");
        assertTrue(subscriptions.size() > 0);

        boolean verifiedCounter = false;
        for (Object object : subscriptions) {
            JSONObject jsonStats = (JSONObject) object;
            if (subscription.getSubscriptionId().equals(jsonStats.get("subscriptionId"))) {
                assertNotNull(jsonStats.get("objectcount"));
                assertTrue(jsonStats.get("objectcount").toString().length() > String.valueOf(Integer.MAX_VALUE).length());
                verifiedCounter = true;
            }
        }
        assertTrue("Counter has not been verified", verifiedCounter);
    }

    @Test
    public void testStatByteCounter() {
        SubscriptionSetup subscription = createSubscription(1);
        assertFalse(subscriptionManager.isSubscriptionRegistered(subscription.getSubscriptionId()));

        subscriptionManager.addSubscription(subscription.getSubscriptionId(), subscription);
        subscriptionManager.activatePendingSubscription(subscription.getSubscriptionId());

        int sum = 0;
        int increment = 999;
        for (int i = 1; i < 10;i++) {
            sum += increment;
            subscriptionManager.incrementObjectCounter(subscription, increment);
        }

        JSONObject jsonObject = subscriptionManager.buildStats();
        assertNotNull(jsonObject.get("subscriptions"));
        assertTrue(jsonObject.get("subscriptions") instanceof JSONArray);

        JSONArray subscriptions = (JSONArray) jsonObject.get("subscriptions");
        assertTrue(subscriptions.size() > 0);

        boolean verifiedCounter = false;
        for (Object object : subscriptions) {
            JSONObject jsonStats = (JSONObject) object;
            if (subscription.getSubscriptionId().equals(jsonStats.get("subscriptionId"))) {
                assertEquals("" + sum, "" + jsonStats.get("objectcount"));
                verifiedCounter = true;
            }
        }
        assertTrue("Counter has not been verified", verifiedCounter);
    }

    @Test
    public void testIsSubscriptionRegistered() {

        assertFalse("Unknown subscription has been activated", subscriptionManager.activatePendingSubscription("RandomSubscriptionId"));
        assertFalse("Unknown subscription reported as registered", subscriptionManager.isSubscriptionRegistered("RandomSubscriptionId"));
    }

    @Test
    public void testAddSubscriptionAndReceivingData() {
        SubscriptionSetup subscription = createSubscription(1000);
        subscription.setVendor("VIPVendor");
        subscription.setActive(true);

        String subscriptionId = subscription.getSubscriptionId();
        subscriptionManager.addSubscription(subscriptionId, subscription);
        assertTrue(subscriptionManager.isSubscriptionHealthy(subscriptionId));
        subscriptionManager.dataReceived(subscriptionId);

        Set<String> allUnhealthySubscriptions = subscriptionManager.getAllUnhealthySubscriptions(1);
        assertFalse(allUnhealthySubscriptions.contains(subscription.getVendor()));
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        Set<String> allUnhealthySubscriptions_2 = subscriptionManager.getAllUnhealthySubscriptions(0);
        assertTrue(allUnhealthySubscriptions_2.contains(subscription.getVendor()));
    }

    private SubscriptionSetup createSubscription(long initialDuration) {
        return createSubscription(initialDuration, Duration.ofMinutes(4));
    }

    private SubscriptionSetup createSubscription(long initialDuration, Duration heartbeatInterval) {
        return new SubscriptionSetup(
                SubscriptionSetup.SubscriptionType.SITUATION_EXCHANGE,
                SubscriptionSetup.SubscriptionMode.SUBSCRIBE,
                "http://localhost",
                heartbeatInterval,
                Duration.ofHours(1),
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
    }
}
