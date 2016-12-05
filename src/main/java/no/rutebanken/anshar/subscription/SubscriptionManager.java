package no.rutebanken.anshar.subscription;


import com.google.common.base.Preconditions;
import com.hazelcast.core.IMap;
import no.rutebanken.anshar.routes.siri.SiriObjectFactory;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.math.BigInteger;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class SubscriptionManager {

    private final int HEALTHCHECK_INTERVAL_FACTOR = 3;
    private Logger logger = LoggerFactory.getLogger(SubscriptionManager.class);

    @Autowired
    @Qualifier("getActiveSubscriptionsMap")
    private IMap<String, SubscriptionSetup> activeSubscriptions;

    @Autowired
    @Qualifier("getPendingSubscriptionsMap")
    private IMap<String, SubscriptionSetup> pendingSubscriptions;

    @Autowired
    @Qualifier("getLastActivityMap")
    private IMap<String, java.time.Instant> lastActivity;

    @Autowired
    @Qualifier("getActivatedTimestampMap")
    private IMap<String, java.time.Instant> activatedTimestamp;

    @Autowired
    private IMap<String, Integer> hitcount;

    @Autowired
    private IMap<String, BigInteger> objectCounter;

    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    public void addSubscription(String subscriptionId, SubscriptionSetup setup) {
        Preconditions.checkState(!pendingSubscriptions.containsKey(subscriptionId), "Subscription already exists (pending)");
        Preconditions.checkState(!activeSubscriptions.containsKey(subscriptionId), "Subscription already exists (active)");

        activeSubscriptions.put(subscriptionId, setup);
        logger.trace("Added subscription {}", setup);
        activatedTimestamp.put(subscriptionId, Instant.now());
        logStats();
    }

    public boolean removeSubscription(String subscriptionId) {
        SubscriptionSetup setup = activeSubscriptions.remove(subscriptionId);

        boolean found = (setup != null);
        addPendingSubscription(subscriptionId, setup);
        logStats();

        logger.info("Removed subscription {}, found: {}", subscriptionId, found);
        return found;
    }

    public boolean touchSubscription(String subscriptionId) {
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
    public boolean touchSubscription(String subscriptionId, ZonedDateTime serviceStartedTime) {
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

    private void logStats() {
        String stats = "Active subscriptions: " + activeSubscriptions.size() + ", Pending subscriptions: " + pendingSubscriptions.size();
        logger.debug(stats);
    }

    public SubscriptionSetup get(String subscriptionId) {
        SubscriptionSetup subscriptionSetup = activeSubscriptions.get(subscriptionId);

        if (subscriptionSetup == null) {
            //Pending subscriptions are also "valid"
            subscriptionSetup = pendingSubscriptions.get(subscriptionId);
        }
        return subscriptionSetup;
    }

    private void hit(String subscriptionId) {
        int counter = (hitcount.get(subscriptionId) != null ? hitcount.get(subscriptionId):0);
        hitcount.put(subscriptionId, counter+1);
    }

    public void incrementObjectCounter(SubscriptionSetup subscriptionSetup, int size) {

        String subscriptionId = subscriptionSetup.getSubscriptionId();
        if (subscriptionId != null) {
            BigInteger counter = (objectCounter.get(subscriptionId) != null ? objectCounter.get(subscriptionId) : new BigInteger("0"));
            objectCounter.put(subscriptionId, counter.add(BigInteger.valueOf(size)));
        }
    }

    public void addPendingSubscription(String subscriptionId, SubscriptionSetup subscriptionSetup) {
        activatedTimestamp.remove(subscriptionId);
        activeSubscriptions.remove(subscriptionId);
        pendingSubscriptions.put(subscriptionId, subscriptionSetup);
        lastActivity.put(subscriptionId, Instant.now());

        logger.info("Added pending subscription {}", subscriptionSetup.toString());
    }

    public boolean isPendingSubscription(String subscriptionId) {
        return pendingSubscriptions.containsKey(subscriptionId);
    }
    public boolean isActiveSubscription(String subscriptionId) {
        return activeSubscriptions.containsKey(subscriptionId);
    }

    public boolean activatePendingSubscription(String subscriptionId) {
        if (isPendingSubscription(subscriptionId)) {
            SubscriptionSetup setup = pendingSubscriptions.remove(subscriptionId);
            addSubscription(subscriptionId, setup);
            lastActivity.put(subscriptionId, Instant.now());
            logger.info("Pending subscription {} activated", setup.toString());
            return true;
        }
        if (isActiveSubscription(subscriptionId)) {
            lastActivity.put(subscriptionId, Instant.now());
            logger.info("Pending subscription {} already activated", activeSubscriptions.get(subscriptionId));
            return true;
        }

        logger.warn("Pending subscriptionId [{}] NOT found", subscriptionId);
        return false;
    }

    public Boolean isSubscriptionHealthy(String subscriptionId) {
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
                logger.debug("Subscription {} never activated.", pendingSubscription.toString());
                //Subscription created, but never received - reestablish subscription
                return false;
            }
        }

        return true;
    }

    public boolean isSubscriptionRegistered(String subscriptionId) {

        if (activeSubscriptions.containsKey(subscriptionId) |
                pendingSubscriptions.containsKey(subscriptionId)) {
            return true;
        }
        //Subscription not registered - trigger start
        return false;
    }

    public JSONObject buildStats() {
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

    private JSONObject getJsonObject(SubscriptionSetup setup, String status) {
        JSONObject obj = setup.toJSON();
        obj.put("activated",formatTimestamp(activatedTimestamp.get(setup.getSubscriptionId())));
        obj.put("lastActivity",""+formatTimestamp(lastActivity.get(setup.getSubscriptionId())));
        if (!setup.isActive()) {
            obj.put("status", "deactivated");
            obj.put("healthy",null);
        } else {
            obj.put("status", status);
            obj.put("healthy",isSubscriptionHealthy(setup.getSubscriptionId()));
        }
        obj.put("hitcount",hitcount.get(setup.getSubscriptionId()));
        obj.put("objectcount",objectCounter.get(setup.getSubscriptionId()));

        JSONObject urllist = new JSONObject();
        for (RequestType s : setup.getUrlMap().keySet()) {
            urllist.put(s.name(), setup.getUrlMap().get(s));
        }
        obj.put("urllist", urllist);

        return obj;
    }

    private String formatTimestamp(Instant instant) {
        if (instant != null) {
            return formatter.format(instant);
        }
        return "";
    }

    public Map<String, SubscriptionSetup> getActiveSubscriptions() {
        return activeSubscriptions;
    }

    public Map<String, SubscriptionSetup> getPendingSubscriptions() {
        return pendingSubscriptions;
    }

    public SubscriptionSetup getSubscriptionById(long internalId) {
        for (SubscriptionSetup setup : activeSubscriptions.values()) {
            if (setup.getInternalId() == internalId) {
                return setup;
            }
        }
        for (SubscriptionSetup setup : pendingSubscriptions.values()) {
            if (setup.getInternalId() == internalId) {
                return setup;
            }
        }
        return null;
    }
}
