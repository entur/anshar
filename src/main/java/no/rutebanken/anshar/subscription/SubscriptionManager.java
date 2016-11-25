package no.rutebanken.anshar.subscription;


import no.rutebanken.anshar.messages.collections.DistributedCollection;
import no.rutebanken.anshar.routes.siri.SiriObjectFactory;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.stream.Collectors;

public class SubscriptionManager {

    private static final int HEALTHCHECK_INTERVAL_FACTOR = 3;
    private static Logger logger = LoggerFactory.getLogger(SubscriptionManager.class);

    private static Map<String, SubscriptionSetup> activeSubscriptions;

    private static Map<String, SubscriptionSetup> pendingSubscriptions;

    private static Map<String, java.time.Instant> lastActivity;
    private static Map<String, java.time.Instant> activatedTimestamp;
    private static Map<String, Integer> hitcount;
    private static Map<String, BigInteger> objectCounter;

    static {
        DistributedCollection dc = new DistributedCollection();
        activeSubscriptions = dc.getActiveSubscriptionsMap();
        pendingSubscriptions = dc.getPendingSubscriptionsMap();
        lastActivity = dc.getLastActivityMap();
        activatedTimestamp = dc.getActivatedTimestampMap();
        hitcount = dc.getHitcountMap();
        objectCounter = dc.getObjectCounterMap();
    }

    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

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

        boolean found = (setup != null);
        addPendingSubscription(subscriptionId, setup);
        logStats();

        logger.info("Removed subscription {}, found: {}", subscriptionId, found);
        return found;
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
                //Setting 'last activity' to longer ago than healthcheck accepts
                lastActivity.put(subscriptionId, Instant.now().minusSeconds((HEALTHCHECK_INTERVAL_FACTOR+1) * setup.getHeartbeatInterval().getSeconds()));
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
        int counter = (hitcount.get(subscriptionId) != null ? hitcount.get(subscriptionId):0);
        hitcount.put(subscriptionId, counter+1);
    }

    public static void incrementObjectCounter(SubscriptionSetup subscriptionSetup, int size) {

        String subscriptionId = subscriptionSetup.getSubscriptionId();
        BigInteger counter = (objectCounter.get(subscriptionId) != null ? objectCounter.get(subscriptionId):new BigInteger("0"));
        objectCounter.put(subscriptionId, counter.add(BigInteger.valueOf(size)));
    }

    public static void addPendingSubscription(String subscriptionId, SubscriptionSetup subscriptionSetup) {
        activatedTimestamp.remove(subscriptionId);
        activeSubscriptions.remove(subscriptionId);
        pendingSubscriptions.put(subscriptionId, subscriptionSetup);
        lastActivity.put(subscriptionId, Instant.now());

        logger.info("Added pending subscription {}", subscriptionSetup.toString());
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
            logger.info("Pending subscription {} activated", setup.toString());
            return true;
        }
        if (isActiveSubscription(subscriptionId)) {
            logger.info("Pending subscription {} already activated", activeSubscriptions.get(subscriptionId));
            return true;
        }
        logger.warn("Pending subscriptionId [{}] NOT found", subscriptionId);
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
            long tripleInterval = activeSubscription.getHeartbeatInterval().toMillis() * HEALTHCHECK_INTERVAL_FACTOR;
            if (instant.isBefore(Instant.now().minusMillis(tripleInterval))) {
                //Subscription exists, but there has not been any activity recently
                return false;
            }

            if (activeSubscription.getSubscriptionMode().equals(SubscriptionSetup.SubscriptionMode.SUBSCRIBE)) {
                //Only actual subscriptions have an expiration - NOT request/response-"subscriptions"

                //If active subscription has existed longer than "initial subscription duration" - restart
                if (activatedTimestamp.get(subscriptionId)
                        .plusSeconds(
                                activeSubscription.getDurationOfSubscription().getSeconds()
                        ).isBefore(Instant.now())) {
                    logger.info("Subscription  [{}] has lasted longer than initial subscription duration ", activeSubscription.toString());
                    return false;
                }
            }

        }

        SubscriptionSetup pendingSubscription = pendingSubscriptions.get(subscriptionId);
        if (pendingSubscription != null) {
            long tripleInterval = pendingSubscription.getHeartbeatInterval().toMillis() * HEALTHCHECK_INTERVAL_FACTOR;
            if (instant.isBefore(Instant.now().minusMillis(tripleInterval))) {
                logger.info("Subscription {} never activated.", pendingSubscription.toString());
                //Subscription created, but never received - reestablish subscription
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

    public static JSONObject buildStats() {
        JSONObject result = new JSONObject();
        JSONArray stats = activeSubscriptions.keySet().stream()
                .map(key -> getJsonObject(activeSubscriptions.get(key), "active"))
                .collect(Collectors.toCollection(() -> new JSONArray()));

        stats.addAll(pendingSubscriptions.keySet().stream()
                .map(key -> getJsonObject(pendingSubscriptions.get(key), "pending"))
                .collect(Collectors.toList()));

        result.put("subscriptions", stats);

        result.put("serverStarted", formatTimestamp(SiriObjectFactory.serverStartTime.toInstant()));

        return result;
    }

    private static JSONObject getJsonObject(SubscriptionSetup setup, String status) {
        JSONObject obj = setup.toJSON();
        obj.put("activated",formatTimestamp(activatedTimestamp.get(setup.getSubscriptionId())));
        obj.put("lastActivity",""+formatTimestamp(lastActivity.get(setup.getSubscriptionId())));
        obj.put("status", status);
        obj.put("healthy",isSubscriptionHealthy(setup.getSubscriptionId()));
        obj.put("hitcount",hitcount.get(setup.getSubscriptionId()));
        obj.put("objectcount",objectCounter.get(setup.getSubscriptionId()));

        JSONObject urllist = new JSONObject();
        for (RequestType s : setup.getUrlMap().keySet()) {
            urllist.put(s.name(), setup.getUrlMap().get(s));
        }
        obj.put("urllist", urllist);

        return obj;
    }

    private static String formatTimestamp(Instant instant) {
        if (instant != null) {
            return formatter.format(instant);
        }
        return "";
    }

    public static Map<String, SubscriptionSetup> getActiveSubscriptions() {
        return activeSubscriptions;
    }

    public static Map<String, SubscriptionSetup> getPendingSubscriptions() {
        return pendingSubscriptions;
    }
}
