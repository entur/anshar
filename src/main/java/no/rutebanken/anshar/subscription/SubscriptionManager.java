package no.rutebanken.anshar.subscription;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public class SubscriptionManager {

    private static Logger logger = LoggerFactory.getLogger(SubscriptionManager.class);

    private static Map<String, SubscriptionSetup> activeSubscriptions = new HashMap<>();
    private static Map<String, SubscriptionSetup> pendingSubscriptions = new HashMap<>();

    private static Map<String, java.time.Instant> lastActivity = new HashMap<>();

    public static void addSubscription(String subscriptionId, SubscriptionSetup setup) {
        if (pendingSubscriptions.containsKey(subscriptionId)) {
            activatePendingSubscription(subscriptionId);
        }
        activeSubscriptions.put(subscriptionId, setup);
        logger.trace("Added subscription [{}]", subscriptionId);
        lastActivity.put(subscriptionId, Instant.now());
        logStats();
    }

    public static boolean removeSubscription(String subscriptionId) {
        boolean success = (activeSubscriptions.remove(subscriptionId) != null);
        logger.trace("Removed subscription [{}], success:{}", subscriptionId, success);
        lastActivity.remove(subscriptionId);

        logStats();
        return success;
    }

    public static boolean touchSubscription(String subscriptionId) {
        boolean success = (activeSubscriptions.get(subscriptionId) != null);
        lastActivity.put(subscriptionId, Instant.now());

        logger.trace("Touched subscription [{}], success:{}", subscriptionId, success);
        logStats();
        return success;
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

    public static void addPendingSubscription(String subscriptionId, SubscriptionSetup subscriptionSetup) {
        pendingSubscriptions.put(subscriptionId, subscriptionSetup);
        lastActivity.put(subscriptionId, Instant.now());

        logger.trace("Added pending subscription [{}]", subscriptionId);
    }

    public static boolean isPendingSubscription(String subscriptionId) {
        return pendingSubscriptions.containsKey(subscriptionId);
    }

    public static boolean activatePendingSubscription(String subscriptionId) {
        if (isPendingSubscription(subscriptionId)) {
            SubscriptionSetup setup = pendingSubscriptions.remove(subscriptionId);
            addSubscription(subscriptionId, setup);
            logger.trace("Pending subscription [{}] activated", subscriptionId);
            return true;
        }
        logger.debug("Pending subscription [{}] NOT activated", subscriptionId);
        return false;
    }

    public static Boolean isSubscriptionHealthy(String subscriptionId) {
        Instant instant = lastActivity.get(subscriptionId);
        if (instant == null) {
            return false;
        }

        logger.trace("Subscription [{}], last activity {}.", subscriptionId, instant);

        SubscriptionSetup activeSubscription = activeSubscriptions.get(subscriptionId);
        if (activeSubscription != null) {
            long tripleInterval = activeSubscription.getHeartbeatInterval().toMillis() * 3;
            if (instant.isBefore(Instant.now().minusMillis(tripleInterval))) {
                //Subscription exists, but heartbeat has not been received recently
                return false;
            }
        }

        SubscriptionSetup pendingSubscription = pendingSubscriptions.get(subscriptionId);
        if (pendingSubscription != null) {
            long tripleInterval = pendingSubscription.getHeartbeatInterval().toMillis() * 3;
            if (instant.isBefore(Instant.now().minusMillis(tripleInterval))) {
                logger.info("Subscription [{}] never activated.", subscriptionId);
                //Subscription created, but async response never received - reestablish subscription
                return false;
            }
        }

        return true;
    }

    public static boolean isSubscriptionRegistered(String subscriptionId) {

        if (!activeSubscriptions.containsKey(subscriptionId) &&
                !pendingSubscriptions.containsKey(subscriptionId)) {
            //Subscription not registered - trigger start
            return false;
        }
        return true;
    }
}
