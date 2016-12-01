package no.rutebanken.anshar.messages.collections;

import com.hazelcast.config.Config;
import com.hazelcast.config.JoinConfig;
import com.hazelcast.config.MulticastConfig;
import com.hazelcast.config.NetworkConfig;
import com.hazelcast.config.SSLConfig;
import com.hazelcast.config.TcpIpConfig;
import com.hazelcast.core.Cluster;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.IMap;
import com.hazelcast.core.Member;
import no.rutebanken.anshar.messages.EstimatedTimetables;
import no.rutebanken.anshar.messages.ProductionTimetables;
import no.rutebanken.anshar.messages.Situations;
import no.rutebanken.anshar.messages.VehicleActivities;
import no.rutebanken.anshar.routes.outbound.OutboundSubscriptionSetup;
import no.rutebanken.anshar.subscription.SubscriptionManager;
import no.rutebanken.anshar.subscription.SubscriptionSetup;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.rutebanken.hazelcasthelper.service.HazelCastService;
import org.rutebanken.hazelcasthelper.service.KubernetesService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import uk.org.siri.siri20.EstimatedVehicleJourney;
import uk.org.siri.siri20.ProductionTimetableDeliveryStructure;
import uk.org.siri.siri20.PtSituationElement;
import uk.org.siri.siri20.VehicleActivityStructure;

import java.math.BigInteger;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.UUID;

@Service
public class DistributedCollection extends HazelCastService {

    private static Logger logger = LoggerFactory.getLogger(DistributedCollection.class);

    /**
     * @deprecated Use expiry as implemented in hazelcast as IMAP.put("abc", "def", 1, TimeUnit.SECONDS)
     */
    @Deprecated
    private static Map<String, Map> existingMaps;

    @Value("${anshar.expiry.period.seconds}")
    private int expiryPeriodSeconds = 30;

    public DistributedCollection(@Autowired KubernetesService kubernetesService) {
        super(kubernetesService);
        if (existingMaps == null) {
            existingMaps = new HashMap<>();
        }
    }

    /**
     * @deprecated Please autowire the dependencies, and delete this method
     */
    @Deprecated
    public DistributedCollection() {
        super(null);
        logger.warn("** WARNING ** This methods should be replaced with autowired spring niceness");
        logger.warn("              Running hazelcast with LOCAL configuration ONLY");
        final Config cfg = new Config()
                .setInstanceName(UUID.randomUUID().toString())
                .setProperty("hazelcast.phone.home.enabled", "false");
        final JoinConfig joinCfg = new JoinConfig()
                .setMulticastConfig(new MulticastConfig().setEnabled(false))
                .setTcpIpConfig(new TcpIpConfig().setEnabled(false));
        cfg.setNetworkConfig(
                new NetworkConfig()
                        .setPortAutoIncrement(true)
                        .setJoin(joinCfg)
                        .setSSLConfig(new SSLConfig().setEnabled(false))
        );

        if (hazelcast == null) {
            hazelcast = Hazelcast.newHazelcastInstance(cfg);
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
     */
    private ExpiringConcurrentMap getCachedOrNewMap(String key) {
        if (!existingMaps.containsKey(key)) {
            existingMaps.put(key, new ExpiringConcurrentMap<>(hazelcast.getMap(key), expiryPeriodSeconds));
        }
        return (ExpiringConcurrentMap) existingMaps.get(key);
    }

    /**
     * Theses maps are cached since the HZ-Map is wrapped in another map
     */
    private ExpiringConcurrentMap getCachedOrNewMap(String key, int expiryPeriodSeconds) {
        if (!existingMaps.containsKey(key)) {
            existingMaps.put(key, new ExpiringConcurrentMap<>(hazelcast.getMap(key), expiryPeriodSeconds));
        }
        return (ExpiringConcurrentMap) existingMaps.get(key);
    }

    public Map<String,SubscriptionSetup> getActiveSubscriptionsMap() {
        return hazelcast.getMap("anshar.subscriptions.active");
    }

    public Map<String,SubscriptionSetup> getPendingSubscriptionsMap() {
        return hazelcast.getMap("anshar.subscriptions.pending");
    }

    public Map<String, Instant> getLastActivityMap() {
        return hazelcast.getMap("anshar.activity.last");
    }

    public Map<String, Instant> getActivatedTimestampMap() {
        return hazelcast.getMap("anshar.activity.activated");
    }

    public Map<String, Integer> getHitcountMap() {
        return hazelcast.getMap("anshar.activity.hitcount");
    }

    public IMap<String, Instant> getLockMap() {
        return hazelcast.getMap("anshar.locks");
    }

    public String listNodes() {
        JSONObject root = new JSONObject();
        JSONArray clusterMembers = new JSONArray();
        Cluster cluster = hazelcast.getCluster();
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
        return hazelcast.getMap("anshar.subscriptions.outbound");
    }

    public Map<String, Timer> getHeartbeatTimerMap() {
        return hazelcast.getMap("anshar.subscriptions.outbound.heartbeat");
    }

    public Map<String,String> getStopPlaceMappings() {
        return hazelcast.getMap("anshar.mapping.stopplaces");
    }

    public Map<String,BigInteger> getObjectCounterMap() {
        return hazelcast.getMap("anshar.activity.objectcount");
    }
}
