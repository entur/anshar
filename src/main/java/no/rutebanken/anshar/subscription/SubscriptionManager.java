/*
 * Licensed under the EUPL, Version 1.2 or – as soon they will be approved by
 * the European Commission - subsequent versions of the EUPL (the "Licence");
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 *
 *   https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and
 * limitations under the Licence.
 */

package no.rutebanken.anshar.subscription;


import com.hazelcast.core.IMap;
import com.hazelcast.map.EntryBackupProcessor;
import com.hazelcast.map.EntryProcessor;
import no.rutebanken.anshar.data.*;
import no.rutebanken.anshar.routes.health.HealthManager;
import no.rutebanken.anshar.routes.siri.helpers.SiriObjectFactory;
import no.rutebanken.anshar.subscription.helpers.RequestType;
import org.apache.commons.io.FileUtils;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigInteger;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

import static no.rutebanken.anshar.subscription.SiriDataType.*;

@Service
public class SubscriptionManager {

    final int HEALTHCHECK_INTERVAL_FACTOR = 5;
    private final Logger logger = LoggerFactory.getLogger(SubscriptionManager.class);

    @Autowired
    @Qualifier("getSubscriptionsMap")
    public IMap<String, SubscriptionSetup> subscriptions;

    @Autowired
    @Qualifier("getLastActivityMap")
    private IMap<String, java.time.Instant> lastActivity;

    @Autowired
    @Qualifier("getDataReceivedMap")
    private IMap<String, java.time.Instant> dataReceived;

    @Autowired
    @Qualifier("getReceivedBytesMap")
    private IMap<String, Long> receivedBytes;

    @Autowired
    @Qualifier("getActivatedTimestampMap")
    IMap<String, java.time.Instant> activatedTimestamp;

    @Value("${anshar.environment}")
    private String environment;

    @Autowired
    @Qualifier("getHitcountMap")
    private IMap<String, Integer> hitcount;

    @Autowired
    @Qualifier("getForceRestartMap")
    private IMap<String, String> forceRestart;

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
    @Qualifier("getLastSxUpdateRequest")
    private IMap<String, Instant> lastSxUpdateRequested;

    @Autowired
    @Qualifier("getEstimatedTimetableChangesMap")
    private IMap<String, Set<String>> etChanges;

    @Autowired
    @Qualifier("getLastEtUpdateRequest")
    private IMap<String, Instant> lastEtUpdateRequested;

    @Autowired
    @Qualifier("getVehicleChangesMap")
    private IMap<String, Set<String>> vmChanges;

    @Autowired
    @Qualifier("getLastVmUpdateRequest")
    private IMap<String, Instant> lastVmUpdateRequested;

    @Autowired
    private RequestorRefRepository requestorRefRepository;

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
                logger.info("Remote service has been restarted, forcing subscription to be restarted [{}]", setup);
                forceRestart(subscriptionId);
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

    public JSONObject getSubscriptionsForCodespace(String codespace) {
        JSONObject jsonSubscriptions = new JSONObject();
        JSONArray filteredSubscriptions = new JSONArray();

        filteredSubscriptions.addAll(subscriptions.values().stream()
                .filter(subscription -> subscription.getDatasetId().equals(codespace))
                .map(this::getJsonObject)
                .filter(Objects::nonNull)
                .collect(Collectors.toList()));

        jsonSubscriptions.put("subscriptions", filteredSubscriptions);
        return jsonSubscriptions;
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
            if (!receivedBytes.containsKey(subscriptionId)) {
                receivedBytes.set(subscriptionId, 0L);
            }
            return true;
        }

        logger.warn("Pending subscriptionId [{}] NOT found", subscriptionId);
        return false;
    }

    public boolean isNewSubscription(String subscriptionId) {
        return lastActivity.get(subscriptionId) == null;
    }

    public Instant getLastDataReceived(String subscriptionId) {
        return dataReceived.get(subscriptionId);
    }

    void forceRestart(String subscriptionId) {
        forceRestart.set(subscriptionId, subscriptionId);
    }

    public boolean isForceRestart(String subscriptionId) {
        if (forceRestart.containsKey(subscriptionId)) {
            logger.info("Subscription {} has triggered a forced restart", subscriptionId);
            return forceRestart.remove(subscriptionId) != null;
        }
        return false;
    }

    public Boolean isSubscriptionHealthy(String subscriptionId) {
        return isSubscriptionHealthy(subscriptionId, HEALTHCHECK_INTERVAL_FACTOR);
    }
    private Boolean isSubscriptionHealthy(String subscriptionId, int healthCheckIntervalFactor) {
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
                Instant activated = activatedTimestamp.get(subscriptionId);
                if (activated != null) {
                    if (activeSubscription.getRestartTime() != null && activeSubscription.getRestartTime().indexOf(":") > 0) {
                        // Allowing subscriptions to be restarted at specified time
                        ZonedDateTime restartTime = ZonedDateTime.of(LocalDate.now(), LocalTime.parse(activeSubscription.getRestartTime()), ZoneId.systemDefault());
                        if (restartTime.isBefore(ZonedDateTime.now()) && activated.atZone(ZoneId.systemDefault()).isBefore(restartTime)) {
                            logger.info("Subscription [{}] configured for nightly restart at {}.", activeSubscription.toString(), restartTime);
                            forceRestart(subscriptionId);
                            return false;
                        }
                    }
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

        JSONArray etSubscriptions = new JSONArray();
        etSubscriptions.addAll(this.subscriptions.values().stream()
                .filter(subscriptionSetup -> subscriptionSetup.getSubscriptionType() == ESTIMATED_TIMETABLE)
                .map(this::getJsonObject)
                .filter(Objects::nonNull)
                .collect(Collectors.toList())
        );

        JSONArray vmSubscriptions = new JSONArray();
        vmSubscriptions.addAll(this.subscriptions.values().stream()
                .filter(subscriptionSetup -> subscriptionSetup.getSubscriptionType() == VEHICLE_MONITORING)
                .map(this::getJsonObject)
                .filter(Objects::nonNull)
                .collect(Collectors.toList())
        );

        JSONArray sxSubscriptions = new JSONArray();
        sxSubscriptions.addAll(this.subscriptions.values().stream()
                .filter(subscriptionSetup -> subscriptionSetup.getSubscriptionType() == SITUATION_EXCHANGE)
                .map(this::getJsonObject)
                .filter(Objects::nonNull)
                .collect(Collectors.toList())
        );

        JSONObject etType = new JSONObject();
        etType.put("typeName", ""+ ESTIMATED_TIMETABLE);
        etType.put("subscriptions", etSubscriptions);
        JSONObject vmType = new JSONObject();
        vmType.put("typeName", ""+ VEHICLE_MONITORING);
        vmType.put("subscriptions", vmSubscriptions);
        JSONObject sxType = new JSONObject();
        sxType.put("typeName", ""+ SITUATION_EXCHANGE);
        sxType.put("subscriptions", sxSubscriptions);

        stats.add(etType);
        stats.add(vmType);
        stats.add(sxType);

        result.put("types", stats);

        JSONArray pollingClients = new JSONArray();

        JSONObject etPolling = new JSONObject();
        etPolling.put("typeName", ""+ ESTIMATED_TIMETABLE);
        etPolling.put("polling", getIdAndCount(etChanges, ESTIMATED_TIMETABLE));
        JSONObject vmPolling = new JSONObject();
        vmPolling.put("typeName", ""+ VEHICLE_MONITORING);
        vmPolling.put("polling", getIdAndCount(vmChanges, VEHICLE_MONITORING));
        JSONObject sxPolling = new JSONObject();
        sxPolling.put("typeName", ""+ SITUATION_EXCHANGE);
        sxPolling.put("polling", getIdAndCount(sxChanges, SITUATION_EXCHANGE));

        pollingClients.add(etPolling);
        pollingClients.add(vmPolling);
        pollingClients.add(sxPolling);

        result.put("polling", pollingClients);

        result.put("environment", environment);
        result.put("serverStarted", formatTimestamp(siriObjectFactory.serverStartTime));
        result.put("secondsSinceDataReceived", healthManager.getSecondsSinceDataReceived());
        JSONObject count = new JSONObject();

        Map<String, Integer> etDatasetSize = et.getDatasetSize();
        Map<String, Integer> vmDatasetSize = vm.getDatasetSize();
        Map<String, Integer> sxDatasetSize = sx.getDatasetSize();

        count.put("sx", sxDatasetSize.values().stream().mapToInt(Number::intValue).sum());
        count.put("et", etDatasetSize.values().stream().mapToInt(Number::intValue).sum());
        count.put("vm", vmDatasetSize.values().stream().mapToInt(Number::intValue).sum());

        count.put("distribution", getCountPerDataset(etDatasetSize, vmDatasetSize, sxDatasetSize));

        result.put("elements", count);

        return result;
    }

    private JSONArray getIdAndCount(Map<String, Set<String>> map, SiriDataType dataType) {
        JSONArray count = new JSONArray();
        for (String key : map.keySet()) {
            JSONObject keyValue = new JSONObject();
            keyValue.put("id", key);
            keyValue.put("count", map.getOrDefault(key, new HashSet<>()).size());

            RequestorRefStats stats = requestorRefRepository.getStats(key, dataType);

            keyValue.put("clientTrackingName", stats != null && stats.clientName != null ? stats.clientName:"");
            keyValue.put("datasetId", stats != null && stats.datasetId != null ? stats.datasetId:"");

            List<String> lastRequests = new ArrayList<>();
            if (stats != null && stats.lastRequests != null) {
                lastRequests = stats.lastRequests;
            }
            if (lastRequests.isEmpty()) {
                lastRequests.add("");
            }

            keyValue.put("lastRequests", lastRequests);

            count.add(keyValue);
        }
        return count;
    }

    private JSONArray getCountPerDataset(Map<String, Integer> etDatasetSize, Map<String, Integer> vmDatasetSize, Map<String, Integer> sxDatasetSize) {
        JSONArray etDatasetCount = new JSONArray();

        Set<String> allKeys = new HashSet<>();
        allKeys.addAll(etDatasetSize.keySet());
        allKeys.addAll(vmDatasetSize.keySet());
        allKeys.addAll(sxDatasetSize.keySet());

        for (String datasetId : allKeys) {
            JSONObject counter = new JSONObject();
            counter.put("datasetId", datasetId);
            counter.put("etCount", etDatasetSize.getOrDefault(datasetId,0));
            counter.put("vmCount", vmDatasetSize.getOrDefault(datasetId,0));
            counter.put("sxCount", sxDatasetSize.getOrDefault(datasetId,0));
            etDatasetCount.add(counter);
        }
        return etDatasetCount;
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

        Long byteCount = receivedBytes.get(setup.getSubscriptionId());
        obj.put("bytecount", byteCount);
        obj.put("bytecountLabel", byteCount != null ? FileUtils.byteCountToDisplaySize(byteCount):null);

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
        if (subscriptionId != null) {
            SubscriptionSetup subscriptionSetup = subscriptions.get(subscriptionId);
            if (subscriptionSetup != null) {
                subscriptionSetup.setActive(false);
                subscriptions.put(subscriptionId, subscriptionSetup);

                removeSubscription(subscriptionId);
                logger.info("Handled request to cancel subscription ", subscriptionSetup);
            }
        }
    }

    public void startSubscription(String subscriptionId) {
        if (subscriptionId != null) {
            SubscriptionSetup subscriptionSetup = subscriptions.get(subscriptionId);
            if (subscriptionSetup != null) {
                subscriptionSetup.setActive(true);
                activatePendingSubscription(subscriptionId);
                logger.info("Handled request to start subscription ", subscriptionSetup);
            }
        }
    }

    public Set<String> getAllUnhealthySubscriptions(int allowedInactivitySeconds) {
        Set<String> subscriptionIds = subscriptions.keySet()
                .stream()
                .filter(this::isActiveSubscription)
                .filter(subscriptionId -> !isSubscriptionReceivingData(subscriptionId, allowedInactivitySeconds))
                .collect(Collectors.toSet());
        if (subscriptionIds != null && !subscriptionIds.isEmpty()) {
            return subscriptions.getAll(subscriptionIds)
                    .values()
                    .stream()
                    .map(SubscriptionSetup::getVendor)
                    .collect(Collectors.toSet());
        }
        return new HashSet<>();
    }

    public boolean isSubscriptionReceivingData(String subscriptionId, long allowedInactivitySeconds) {
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
        dataReceived(subscriptionId, 0);
    }
    public void dataReceived(String subscriptionId, int receivedByteCount) {
        touchSubscription(subscriptionId);
        if (isActiveSubscription(subscriptionId)) {
            dataReceived.set(subscriptionId, Instant.now());

            if (receivedByteCount > 0) {
                receivedBytes.set(subscriptionId,
                        receivedBytes.getOrDefault(subscriptionId, 0L) + receivedByteCount);
            }
        }
    }

    /**
     * Silently updates subscription
     * @param subscriptionSetup
     */
    public void updateSubscription(SubscriptionSetup subscriptionSetup) {
        subscriptions.set(subscriptionSetup.getSubscriptionId(), subscriptionSetup);
    }
}
