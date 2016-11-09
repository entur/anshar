package no.rutebanken.anshar.messages.collections;

import com.hazelcast.core.*;
import no.rutebanken.anshar.messages.EstimatedTimetables;
import no.rutebanken.anshar.messages.ProductionTimetables;
import no.rutebanken.anshar.messages.Situations;
import no.rutebanken.anshar.messages.VehicleActivities;
import no.rutebanken.anshar.routes.outbound.OutboundSubscriptionSetup;
import no.rutebanken.anshar.subscription.SubscriptionManager;
import no.rutebanken.anshar.subscription.SubscriptionSetup;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import uk.org.siri.siri20.EstimatedVehicleJourney;
import uk.org.siri.siri20.ProductionTimetableDeliveryStructure;
import uk.org.siri.siri20.PtSituationElement;
import uk.org.siri.siri20.VehicleActivityStructure;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.Timer;

@Configuration
public class DistributedCollection {

    private static Logger logger = LoggerFactory.getLogger(DistributedCollection.class);
    private static HazelcastInstance hazelcastInstance;

    private static Map<String, Map> existingMaps;

    @Value("${anshar.expiry.period.seconds}")
    private int expiryPeriodSeconds = 30;

    public DistributedCollection() {
        if (hazelcastInstance == null) {
            hazelcastInstance = Hazelcast.newHazelcastInstance();
        }
        if (existingMaps == null) {
            existingMaps = new HashMap<>();
        }
    }

    public ExpiringConcurrentMap<String, PtSituationElement> getSituationsMap(){
        return getCachedOrNewMap("anshar.sx");
    }

    public ExpiringConcurrentMap<String, Set<String>> getSituationChangesMap() {
        return getCachedOrNewMap("anshar.sx.changes", 300);
    }

    public ExpiringConcurrentMap<String, EstimatedVehicleJourney> getEstimatedTimetablesMap(){
        return getCachedOrNewMap("anshar.et");
    }

    public ExpiringConcurrentMap<String, Set<String>> getEstimatedTimetableChangesMap() {
        return getCachedOrNewMap("anshar.et.changes", 300);
    }

    public ExpiringConcurrentMap<String, VehicleActivityStructure> getVehiclesMap(){
        return getCachedOrNewMap("anshar.vm");
    }

    public ExpiringConcurrentMap<String, Set<String>> getVehicleChangesMap() {
        return getCachedOrNewMap("anshar.vm.changes", 300);
    }

    public ExpiringConcurrentMap<String, ProductionTimetableDeliveryStructure> getProductionTimetablesMap(){
        return getCachedOrNewMap("anshar.pt");
    }

    public ExpiringConcurrentMap<String, Set<String>> getProductionTimetableChangesMap() {
        return getCachedOrNewMap("anshar.pt.changes", 300);
    }

    /**
     * Theses maps are cached since the HZ-Map is wrapped in another map
     * @param key
     * @return
     */
    private ExpiringConcurrentMap getCachedOrNewMap(String key) {
        if (!existingMaps.containsKey(key)) {
            existingMaps.put(key, new ExpiringConcurrentMap<>(hazelcastInstance.getMap(key), expiryPeriodSeconds));
        }
        return (ExpiringConcurrentMap) existingMaps.get(key);
    }

    /**
     * Theses maps are cached since the HZ-Map is wrapped in another map
     * @param key
     * @return
     */
    private ExpiringConcurrentMap getCachedOrNewMap(String key, int expiryPeriodSeconds) {
        if (!existingMaps.containsKey(key)) {
            existingMaps.put(key, new ExpiringConcurrentMap<>(hazelcastInstance.getMap(key), expiryPeriodSeconds));
        }
        return (ExpiringConcurrentMap) existingMaps.get(key);
    }

    public Map<String,SubscriptionSetup> getActiveSubscriptionsMap() {
        return hazelcastInstance.getMap("anshar.subscriptions.active");
    }

    public Map<String,SubscriptionSetup> getPendingSubscriptionsMap() {
        return hazelcastInstance.getMap("anshar.subscriptions.pending");
    }

    public Map<String, Instant> getLastActivityMap() {
        return hazelcastInstance.getMap("anshar.activity.last");
    }

    public Map<String, Instant> getActivatedTimestampMap() {
        return hazelcastInstance.getMap("anshar.activity.activated");
    }

    public Map<String, Integer> getHitcountMap() {
        return hazelcastInstance.getMap("anshar.activity.hitcount");
    }

    public IMap<String, Instant> getLockMap() {
        return hazelcastInstance.getMap("anshar.locks");
    }

    public String listNodes() {
        JSONObject root = new JSONObject();
        JSONArray clusterMembers = new JSONArray();
        Cluster cluster = hazelcastInstance.getCluster();
        if (cluster != null) {
            Set<Member> members = cluster.getMembers();
            if (members != null && !members.isEmpty()) {
                for (Member member : members) {
                    JSONObject obj = new JSONObject();
                    obj.put("uuid", member.getUuid());
                    obj.put("host", member.getAddress().getHost());
                    obj.put("port", member.getAddress().getPort());
                    obj.put("local", member.localMember());
                    clusterMembers.add(obj);
                }
            }
        }
        root.put("members", clusterMembers);

        JSONObject mapStats = new JSONObject();
        mapStats.put("SX", Situations.getAll().size());
        mapStats.put("VM", VehicleActivities.getAll().size());
        mapStats.put("ET", EstimatedTimetables.getAll().size());
        mapStats.put("PT", ProductionTimetables.getAll().size());


        JSONObject subscriptionStats = new JSONObject();
        subscriptionStats.put("active", SubscriptionManager.getActiveSubscriptions().size());
        subscriptionStats.put("pending", SubscriptionManager.getPendingSubscriptions().size());

        JSONObject counters = new JSONObject();
        counters.put("data", mapStats);
        counters.put("subscriptions", subscriptionStats);
        root.put("counters", counters);
        return root.toString();
    }

    public Map<String, OutboundSubscriptionSetup> getOutboundSubscriptionMap() {
        return hazelcastInstance.getMap("anshar.subscriptions.outbound");
    }

    public Map<String, Timer> getHeartbeatTimerMap() {
        return hazelcastInstance.getMap("anshar.subscriptions.outbound.heartbeat");
    }
}
