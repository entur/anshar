package no.rutebanken.anshar.subscription;


import no.rutebanken.anshar.messages.DistributedCollection;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;

public class SubscriptionManager extends DistributedCollection {

    private static Logger logger = LoggerFactory.getLogger(SubscriptionManager.class);

    private static Map<String, SubscriptionSetup> activeSubscriptions = getActiveSubscriptionsMap();

    private static Map<String, SubscriptionSetup> pendingSubscriptions = getPendingSubscriptionsMap();

    private static Map<String, java.time.Instant> lastActivity = getLastActivityMap();
    private static Map<String, java.time.Instant> activatedTimestamp = getActivatedTimestampMap();
    private static Map<String, Integer> hitcount = getHitcountMap();

    public static void addSubscription(String subscriptionId, SubscriptionSetup setup) {
        if (pendingSubscriptions.containsKey(subscriptionId)) {
            activatePendingSubscription(subscriptionId);
        }
        activeSubscriptions.put(subscriptionId, setup);
        logger.trace("Added subscription {}", setup);
        lastActivity.put(subscriptionId, Instant.now());
        activatedTimestamp.put(subscriptionId, Instant.now());
        logStats();
    }

    public static boolean removeSubscription(String subscriptionId) {
        SubscriptionSetup setup = activeSubscriptions.remove(subscriptionId);

        boolean success = (setup != null);

        lastActivity.remove(subscriptionId);
        activatedTimestamp.remove(subscriptionId);
        pendingSubscriptions.remove(subscriptionId);
        logStats();

        logger.info("Removing subscription {}, success: {}", setup, success);
        return success;
    }

    public static boolean touchSubscription(String subscriptionId) {
        SubscriptionSetup setup = activeSubscriptions.get(subscriptionId);
        hit(subscriptionId);

        boolean success = (setup != null);

        if (!success) {
            // Handling race conditions caused by async responses
            success = activatePendingSubscription(subscriptionId);
        }

        logger.trace("Touched subscription {}, success:{}", setup, success);
        if (success) {
            lastActivity.put(subscriptionId, Instant.now());
        }

        logStats();
        return success;
    }

    /**
     * Touches subscription if reported serviceStartedTime is BEFORE last activity.
     * If not, subscription is removed to trigger reestablishing subscription
     * @param subscriptionId
     * @param serviceStartedTime
     * @return
     */
    public static boolean touchSubscription(String subscriptionId, ZonedDateTime serviceStartedTime) {
        SubscriptionSetup setup = activeSubscriptions.get(subscriptionId);
        if (setup != null && serviceStartedTime != null) {
            if (lastActivity.get(subscriptionId).isAfter(serviceStartedTime.toInstant())) {
                return touchSubscription(subscriptionId);
            } else {
                logger.info("Remote service has been restarted, reestablishing subscription [{}]", subscriptionId);
                removeSubscription(subscriptionId);
            }
        }
        return false;
    }

    private static void logStats() {
        String stats = "Active subscriptions: " + activeSubscriptions.size() + ", Pending subscriptions: " + pendingSubscriptions.size();
        logger.debug(stats);
    }

    public static SubscriptionSetup get(String subscriptionId) {
        SubscriptionSetup subscriptionSetup = activeSubscriptions.get(subscriptionId);

        if (subscriptionSetup == null) {
            //Pending subscriptions are also "valid"
            subscriptionSetup = pendingSubscriptions.get(subscriptionId);
        }
        return subscriptionSetup;
    }

    private static void hit(String subscriptionId) {
        int counter = 1;
        if (hitcount.containsKey(subscriptionId)) {
           counter = hitcount.get(subscriptionId)+1;
        }
        hitcount.put(subscriptionId, counter);
    }

    public static void addPendingSubscription(String subscriptionId, SubscriptionSetup subscriptionSetup) {
        activatedTimestamp.remove(subscriptionId);
        activeSubscriptions.remove(subscriptionId);
        pendingSubscriptions.put(subscriptionId, subscriptionSetup);
        lastActivity.put(subscriptionId, Instant.now());

        logger.trace("Added pending subscription {}", subscriptionSetup.toString());
    }

    public static boolean isPendingSubscription(String subscriptionId) {
        return pendingSubscriptions.containsKey(subscriptionId);
    }
    public static boolean isActiveSubscription(String subscriptionId) {
        return activeSubscriptions.containsKey(subscriptionId);
    }

    public static boolean activatePendingSubscription(String subscriptionId) {
        if (isPendingSubscription(subscriptionId)) {
            SubscriptionSetup setup = pendingSubscriptions.remove(subscriptionId);
            addSubscription(subscriptionId, setup);
            logger.trace("Pending subscription {} activated", setup.toString());
            return true;
        }
        if (isActiveSubscription(subscriptionId)) {
            logger.trace("Pending subscription {} already activated", activeSubscriptions.get(subscriptionId));
            return true;
        }
        logger.debug("Pending subscriptionId [{}] NOT found", subscriptionId);
        return false;
    }

    public static Boolean isSubscriptionHealthy(String subscriptionId) {
        Instant instant = lastActivity.get(subscriptionId);
        if (instant == null) {
            return false;
        }

        logger.trace("SubscriptionId [{}], last activity {}.", subscriptionId, instant);

        SubscriptionSetup activeSubscription = activeSubscriptions.get(subscriptionId);
        if (activeSubscription != null) {
            long tripleInterval = activeSubscription.getHeartbeatInterval().toMillis() * 3;
            if (instant.isBefore(Instant.now().minusMillis(tripleInterval))) {
                //Subscription exists, but heartbeat has not been received recently
                return false;
            }

            //If active subscription has existed longer than "initial subscription duration" - restart
            if (activatedTimestamp.get(subscriptionId)
                    .plusSeconds(
                            activeSubscription.getDurationOfSubscription().getSeconds()
                    ).isBefore(Instant.now())) {
                logger.info("Subscription  [{}] has lasted longer than initial subscription duration - triggering restart", activeSubscription.toString());
                return false;
            }

        }

        SubscriptionSetup pendingSubscription = pendingSubscriptions.get(subscriptionId);
        if (pendingSubscription != null) {
            long tripleInterval = pendingSubscription.getHeartbeatInterval().toMillis() * 3;
            if (instant.isBefore(Instant.now().minusMillis(tripleInterval))) {
                logger.info("Subscription {} never activated.", pendingSubscription.toString());
                //Subscription created, but async response never received - reestablish subscription
                return false;
            }
        }

        return true;
    }

    public static boolean isSubscriptionRegistered(String subscriptionId) {

        if (activeSubscriptions.containsKey(subscriptionId) |
                pendingSubscriptions.containsKey(subscriptionId)) {
            return true;
        }
        //Subscription not registered - trigger start
        return false;
    }

    public static String buildStats() {


        JSONArray stats = new JSONArray();


        for (String key : activeSubscriptions.keySet()) {

            SubscriptionSetup setup = activeSubscriptions.get(key);

            JSONObject obj = setup.toJSON();
            obj.put("activated",""+activatedTimestamp.get(setup.getSubscriptionId()).atZone(ZoneId.systemDefault()));
            obj.put("lastActivity",""+lastActivity.get(setup.getSubscriptionId()).atZone(ZoneId.systemDefault()));
            obj.put("status","active");
            obj.put("healthy",isSubscriptionHealthy(setup.getSubscriptionId()));
            obj.put("hitcount",hitcount.get(setup.getSubscriptionId()));

            stats.add(obj);
        }
        for (String key : pendingSubscriptions.keySet()) {
            SubscriptionSetup setup = pendingSubscriptions.get(key);

            JSONObject obj = setup.toJSON();
            obj.put("activated",null);
            obj.put("lastActivity",""+lastActivity.get(setup.getSubscriptionId()).atZone(ZoneId.systemDefault()));
            obj.put("status","pending");
            obj.put("healthy",isSubscriptionHealthy(setup.getSubscriptionId()));
            obj.put("hitcount",hitcount.get(setup.getSubscriptionId()));

            stats.add(obj);
        }

        return stats.toJSONString();
    }
}
