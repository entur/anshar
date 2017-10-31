package no.rutebanken.anshar.subscription;


import com.hazelcast.core.IMap;
import no.rutebanken.anshar.messages.EstimatedTimetables;
import no.rutebanken.anshar.messages.ProductionTimetables;
import no.rutebanken.anshar.messages.Situations;
import no.rutebanken.anshar.messages.VehicleActivities;
import no.rutebanken.anshar.routes.health.HealthManager;
import no.rutebanken.anshar.routes.siri.SiriObjectFactory;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigInteger;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class SubscriptionManager {

    final int HEALTHCHECK_INTERVAL_FACTOR = 5;
    private Logger logger = LoggerFactory.getLogger(SubscriptionManager.class);

    @Autowired
    @Qualifier("getSubscriptionsMap")
    private IMap<String, SubscriptionSetup> subscriptions;

    @Autowired
    @Qualifier("getLastActivityMap")
    private IMap<String, java.time.Instant> lastActivity;

    @Autowired
    @Qualifier("getDataReceivedMap")
    private IMap<String, java.time.Instant> dataReceived;

    @Autowired
    @Qualifier("getActivatedTimestampMap")
    private IMap<String, java.time.Instant> activatedTimestamp;

    @Value("${anshar.environment}")
    private String environment;

    @Autowired
    private IMap<String, Integer> hitcount;

    @Autowired
    private IMap<String, BigInteger> objectCounter;

    @Autowired
    private SiriObjectFactory siriObjectFactory;

    @Autowired
    private HealthManager healthManager;

    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    @Autowired
    private Situations sx;
    @Autowired
    private EstimatedTimetables et;
    @Autowired
    private ProductionTimetables pt;
    @Autowired
    private VehicleActivities vm;

    @Autowired
    @Qualifier("getSituationChangesMap")
    private IMap<String, Set<String>> sxChanges;

    @Autowired
    @Qualifier("getEstimatedTimetableChangesMap")
    private IMap<String, Set<String>> etChanges;

    @Autowired
    @Qualifier("getProductionTimetableChangesMap")
    private IMap<String, Set<String>> ptChanges;

    @Autowired
    @Qualifier("getVehicleChangesMap")
    private IMap<String, Set<String>> vmChanges;

    public void addSubscription(String subscriptionId, SubscriptionSetup setup) {

        subscriptions.put(subscriptionId, setup);
        logger.trace("Added subscription {}", setup);
        if (setup.isActive()) {
            activatePendingSubscription(subscriptionId);
        }
        logStats();
    }

    public boolean removeSubscription(String subscriptionId) {
        return removeSubscription(subscriptionId, false);
    }

    public boolean removeSubscription(String subscriptionId, boolean force) {
        SubscriptionSetup setup = subscriptions.remove(subscriptionId);

        boolean found = (setup != null);

        if (force) {
            logger.info("Completely deleting subscription by request.");
            activatedTimestamp.remove(subscriptionId);
            lastActivity.remove(subscriptionId);
            hitcount.remove(subscriptionId);
            objectCounter.remove(subscriptionId);
        } else if (found) {
            setup.setActive(false);
            addSubscription(subscriptionId, setup);
        }

        logStats();

        logger.info("Removed subscription {}, found: {}", (setup !=null ? setup.toString():subscriptionId), found);
        return found;
    }

    public boolean touchSubscription(String subscriptionId) {
        SubscriptionSetup setup = subscriptions.get(subscriptionId);
        hit(subscriptionId);

        boolean success = (setup != null);

        logger.info("Touched subscription {}, success:{}", setup, success);
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
        SubscriptionSetup setup = subscriptions.get(subscriptionId);
        if (setup != null && serviceStartedTime != null) {
            Instant lastActivity = this.lastActivity.get(subscriptionId);
            if (lastActivity == null || serviceStartedTime.toInstant().isBefore(lastActivity)) {
                logger.info("Remote Service startTime ({}) is before lastActivity ({}) for subscription [{}]",serviceStartedTime, lastActivity, setup);
                return touchSubscription(subscriptionId);
            } else {
                logger.info("Remote service has been restarted, reestablishing subscription [{}]", setup);
                //Setting 'last activity' to longer ago than healthcheck accepts
                this.lastActivity.put(subscriptionId, Instant.now().minusSeconds((HEALTHCHECK_INTERVAL_FACTOR+1) * setup.getHeartbeatInterval().getSeconds()));
            }
        }
        return false;
    }

    private void logStats() {
        String stats = "Active subscriptions: " + subscriptions.size();
        logger.debug(stats);
    }

    public SubscriptionSetup get(String subscriptionId) {

        return subscriptions.get(subscriptionId);
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

    public boolean isActiveSubscription(String subscriptionId) {
        SubscriptionSetup subscriptionSetup = subscriptions.get(subscriptionId);
        if (subscriptionSetup != null) {
            return subscriptionSetup.isActive();
        }
        return false;
    }

    public boolean activatePendingSubscription(String subscriptionId) {
        SubscriptionSetup subscriptionSetup = subscriptions.get(subscriptionId);
        if (subscriptionSetup != null) {
            subscriptionSetup.setActive(true);
            // Subscriptions are inserted as immutable - need to replace previous value
            subscriptions.put(subscriptionId, subscriptionSetup);
            lastActivity.put(subscriptionId, Instant.now());
            activatedTimestamp.put(subscriptionId, Instant.now());
            logger.info("Pending subscription {} activated", subscriptions.get(subscriptionId));
            if (!dataReceived.containsKey(subscriptionId)) {
                dataReceived(subscriptionId);
            }
            return true;
        }

        logger.warn("Pending subscriptionId [{}] NOT found", subscriptionId);
        return false;
    }

    public boolean isNewSubscription(String subscriptionId) {
        return lastActivity.get(subscriptionId) == null;
    }

    public Boolean isSubscriptionHealthy(String subscriptionId) {
        return isSubscriptionHealthy(subscriptionId, HEALTHCHECK_INTERVAL_FACTOR);
    }
    public Boolean isSubscriptionHealthy(String subscriptionId, int healthCheckIntervalFactor) {
        Instant instant = lastActivity.get(subscriptionId);

        if (instant == null) {
            //Subscription has not had any activity, and may not have been started yet - flag as healthy
            return true;
        }

        logger.trace("SubscriptionId [{}], last activity {}.", subscriptionId, instant);

        SubscriptionSetup activeSubscription = subscriptions.get(subscriptionId);
        if (activeSubscription != null && activeSubscription.isActive()) {

            Duration heartbeatInterval = activeSubscription.getHeartbeatInterval();
            if (heartbeatInterval == null) {
                heartbeatInterval = Duration.ofMinutes(5);
            }

            long allowedInterval = heartbeatInterval.toMillis() * healthCheckIntervalFactor;

            if (instant.isBefore(Instant.now().minusMillis(allowedInterval))) {
                //Subscription exists, but there has not been any activity recently
                return false;
            }

            if (activeSubscription.getSubscriptionMode().equals(SubscriptionSetup.SubscriptionMode.SUBSCRIBE)) {
                //Only actual subscriptions have an expiration - NOT request/response-"subscriptions"

                //If active subscription has existed longer than "initial subscription duration" - restart
                if (activatedTimestamp.get(subscriptionId) != null && activatedTimestamp.get(subscriptionId)
                        .plusSeconds(
                                activeSubscription.getDurationOfSubscription().getSeconds()
                        ).isBefore(Instant.now())) {
                    logger.info("Subscription  [{}] has lasted longer than initial subscription duration ", activeSubscription.toString());
                    return false;
                }
            }

        }

        return true;
    }

    public boolean isSubscriptionRegistered(String subscriptionId) {

        return subscriptions.containsKey(subscriptionId);
    }

    public JSONObject buildStats() {
        JSONObject result = new JSONObject();
        JSONArray stats = new JSONArray();
        stats.addAll(subscriptions.keySet().stream()
                .map(key -> getJsonObject(subscriptions.get(key)))
                .filter(json -> json != null)
                .collect(Collectors.toList()));

        result.put("subscriptions", stats);

        result.put("environment", environment);
        result.put("serverStarted", formatTimestamp(siriObjectFactory.serverStartTime));
        result.put("secondsSinceDataReceived", healthManager.getSecondsSinceDataReceived());
        JSONObject count = new JSONObject();
        count.put("sx", sx.getSize());
        count.put("et", et.getSize());
        count.put("vm", vm.getSize());
        count.put("pt", pt.getSize());
        count.put("sxChanges", sxChanges.size());
        count.put("etChanges", etChanges.size());
        count.put("vmChanges", vmChanges.size());
        count.put("ptChanges", ptChanges.size());

        result.put("elements", count);

        return result;
    }

    private JSONObject getJsonObject(SubscriptionSetup setup) {
        if (setup == null) {
            return null;
        }
        JSONObject obj = setup.toJSON();
        obj.put("activated",formatTimestamp(activatedTimestamp.get(setup.getSubscriptionId())));
        obj.put("lastActivity",""+formatTimestamp(lastActivity.get(setup.getSubscriptionId())));
        obj.put("lastDataReceived",""+formatTimestamp(dataReceived.get(setup.getSubscriptionId())));
        if (!setup.isActive()) {
            obj.put("status", "deactivated");
            obj.put("healthy",null);
            obj.put("flagAsNotReceivingData", false);
        } else {
            obj.put("status", "active");
            obj.put("healthy", isSubscriptionHealthy(setup.getSubscriptionId()));
            obj.put("flagAsNotReceivingData", (dataReceived.get(setup.getSubscriptionId()) != null && (dataReceived.get(setup.getSubscriptionId())).isBefore(Instant.now().minusSeconds(1800))));
        }
        obj.put("hitcount",hitcount.get(setup.getSubscriptionId()));
        obj.put("objectcount", objectCounter.get(setup.getSubscriptionId()));

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

    public SubscriptionSetup getSubscriptionById(long internalId) {
        for (SubscriptionSetup setup : subscriptions.values()) {
            if (setup.getInternalId() == internalId) {
                return setup;
            }
        }
        return null;
    }

    public void stopSubscription(String subscriptionId) {

        SubscriptionSetup subscriptionSetup = subscriptions.get(subscriptionId);
        if (subscriptionSetup != null) {
            subscriptionSetup.setActive(false);
            subscriptions.put(subscriptionId, subscriptionSetup);

            removeSubscription(subscriptionId);
            logger.info("Handled request to cancel subscription ", subscriptionSetup);
        }
    }

    public void startSubscription(String subscriptionId) {
        SubscriptionSetup subscriptionSetup = subscriptions.get(subscriptionId);
        if (subscriptionSetup != null) {
            subscriptionSetup.setActive(true);
            activatePendingSubscription(subscriptionId);
            logger.info("Handled request to start subscription ", subscriptionSetup);
        }
    }

    public Set<String> getAllUnhealthySubscriptions(int allowedInactivitySeconds) {
        Set<String> subscriptionIds = subscriptions.keySet()
                .stream()
                .filter(subscriptionId -> isActiveSubscription(subscriptionId))
                .filter(subscriptionId -> !isSubscriptionReceivingData(subscriptionId, allowedInactivitySeconds))
                .collect(Collectors.toSet());
        if (subscriptionIds != null & !subscriptionIds.isEmpty()) {
            return subscriptions.getAll(subscriptionIds)
                    .values()
                    .stream()
                    .map(subscriptionSetup -> subscriptionSetup.getVendor())
                    .collect(Collectors.toSet());
        }
        return new HashSet<>();
    }

    private boolean isSubscriptionReceivingData(String subscriptionId, long allowedInactivitySeconds) {
        if (!isActiveSubscription(subscriptionId)) {
            return true;
        }
        boolean isReceiving = true;
        Instant lastDataReceived = dataReceived.get(subscriptionId);
        if (lastDataReceived != null) {
            isReceiving = (Instant.now().minusSeconds(allowedInactivitySeconds).isBefore(lastDataReceived));
        }
        return isReceiving;
    }

    public void dataReceived(String subscriptionId) {
        touchSubscription(subscriptionId);
        if (isActiveSubscription(subscriptionId)) {
            dataReceived.put(subscriptionId, Instant.now());
        }
    }
}
