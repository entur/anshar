package no.rutebanken.anshar.messages.collections;

import com.hazelcast.core.Cluster;
import com.hazelcast.core.IMap;
import com.hazelcast.core.Member;
import no.rutebanken.anshar.routes.outbound.OutboundSubscriptionSetup;
import no.rutebanken.anshar.subscription.SubscriptionSetup;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.rutebanken.hazelcasthelper.service.HazelCastService;
import org.rutebanken.hazelcasthelper.service.KubernetesService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;
import uk.org.siri.siri20.EstimatedVehicleJourney;
import uk.org.siri.siri20.ProductionTimetableDeliveryStructure;
import uk.org.siri.siri20.PtSituationElement;
import uk.org.siri.siri20.VehicleActivityStructure;

import javax.annotation.PostConstruct;
import java.math.BigInteger;
import java.time.Instant;
import java.util.Set;
import java.util.Timer;

@Service
public class DistributedCollection extends HazelCastService {

    private Logger logger = LoggerFactory.getLogger(DistributedCollection.class);

    @PostConstruct
    @Override
    public void init() {
        super.init();
    }

    public DistributedCollection(@Autowired KubernetesService kubernetesService) {
        super(kubernetesService);
    }

    @Bean
    public IMap<String, PtSituationElement> getSituationsMap(){
        return hazelcast.getMap("anshar.sx");
    }

    @Bean
    public IMap<String, Set<String>> getSituationChangesMap() {
        return hazelcast.getMap("anshar.sx.changes");
    }

    @Bean
    public IMap<String, EstimatedVehicleJourney> getEstimatedTimetablesMap(){
        return hazelcast.getMap("anshar.et");
    }

    @Bean
    public IMap<String, Set<String>> getEstimatedTimetableChangesMap() {
        return hazelcast.getMap("anshar.et.changes");
    }

    @Bean
    public IMap<String, VehicleActivityStructure> getVehiclesMap(){
        return hazelcast.getMap("anshar.vm");
    }

    @Bean
    public IMap<String, Set<String>> getVehicleChangesMap() {
        return hazelcast.getMap("anshar.vm.changes");
    }

    @Bean
    public IMap<String, ProductionTimetableDeliveryStructure> getProductionTimetablesMap(){
        return hazelcast.getMap("anshar.pt");
    }

    @Bean
    public IMap<String, Set<String>> getProductionTimetableChangesMap() {
        return hazelcast.getMap("anshar.pt.changes");
    }

    @Bean
    public IMap<String,SubscriptionSetup> getActiveSubscriptionsMap() {
        return hazelcast.getMap("anshar.subscriptions.active");
    }

    @Bean
    public IMap<String,SubscriptionSetup> getPendingSubscriptionsMap() {
        return hazelcast.getMap("anshar.subscriptions.pending");
    }

    @Bean
    public IMap<String, Instant> getLastActivityMap() {
        return hazelcast.getMap("anshar.activity.last");
    }

    @Bean
    public IMap<String, Instant> getActivatedTimestampMap() {
        return hazelcast.getMap("anshar.activity.activated");
    }

    @Bean
    public IMap<String, Integer> getHitcountMap() {
        return hazelcast.getMap("anshar.activity.hitcount");
    }

    @Bean
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
        return root.toString();
    }

    @Bean
    public IMap<String, OutboundSubscriptionSetup> getOutboundSubscriptionMap() {
        return hazelcast.getMap("anshar.subscriptions.outbound");
    }

    @Bean
    public IMap<String, Timer> getHeartbeatTimerMap() {
        return hazelcast.getMap("anshar.subscriptions.outbound.heartbeat");
    }

    @Bean
    public IMap<String,String> getStopPlaceMappings() {
        return hazelcast.getMap("anshar.mapping.stopplaces");
    }

    @Bean
    public IMap<String,BigInteger> getObjectCounterMap() {
        return hazelcast.getMap("anshar.activity.objectcount");
    }
}
