package no.rutebanken.anshar.data.collections;

import com.hazelcast.config.SerializerConfig;
import com.hazelcast.core.*;
import com.hazelcast.nio.serialization.ByteArraySerializer;
import no.rutebanken.anshar.config.AnsharConfiguration;
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
import uk.org.siri.siri20.*;

import java.io.*;
import java.math.BigInteger;
import java.time.Instant;
import java.util.*;

@Service
public class ExtendedHazelcastService extends HazelCastService {

    private Logger logger = LoggerFactory.getLogger(ExtendedHazelcastService.class);

    public ExtendedHazelcastService(@Autowired KubernetesService kubernetesService, @Autowired AnsharConfiguration cfg) {
        super(kubernetesService, cfg.getHazelcastManagementUrl());
    }

    public HazelcastInstance getHazelcastInstance() {
        return hazelcast;
    }

    @Override
    public List<SerializerConfig> getSerializerConfigs() {

        return Arrays.asList(new SerializerConfig()
                .setTypeClass(Object.class)
                .setImplementation(new ByteArraySerializer() {
                    @Override
                    public byte[] write(Object object) throws IOException {
                        try(ByteArrayOutputStream b = new ByteArrayOutputStream()){
                            try(ObjectOutputStream o = new ObjectOutputStream(b)){
                                o.writeObject(object);
                            }
                            return b.toByteArray();
                        }
                    }

                    @Override
                    public Object read(byte[] buffer) throws IOException {
                        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(buffer);
                        ObjectInputStream in = new ObjectInputStream(byteArrayInputStream);
                        try {
                            return in.readObject();
                        } catch (ClassNotFoundException e) {
                            e.printStackTrace();
                        }
                        return null;
                    }

                    @Override
                    public int getTypeId() {
                        return 1;
                    }

                    @Override
                    public void destroy() {

                    }
                })
        );
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
    public IMap<String,SubscriptionSetup> getSubscriptionsMap() {
        return hazelcast.getMap("anshar.subscriptions.active");
    }

    @Bean
    public IMap<String, Instant> getLastActivityMap() {
        return hazelcast.getMap("anshar.activity.last");
    }

    @Bean
    public IMap<String, Instant> getDataReceivedMap() {
        return hazelcast.getMap("anshar.subscriptions.data.received");
    }


    @Bean
    public IMap<String, Instant> getLastEtUpdateRequest() {
        return hazelcast.getMap("anshar.activity.last.et.update.request");
    }

    @Bean
    public IMap<String, Instant> getLastPtUpdateRequest() {
        return hazelcast.getMap("anshar.activity.last.pt.update.request");
    }


    @Bean
    public IMap<String, Instant> getLastSxUpdateRequest() {
        return hazelcast.getMap("anshar.activity.last.sx.update.request");
    }

    @Bean
    public IMap<String, Instant> getLastVmUpdateRequest() {
        return hazelcast.getMap("anshar.activity.last.vm.update.request");
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
    public IMap<String, Instant> getFailTrackerMap() {
        return hazelcast.getMap("anshar.activity.failtracker");
    }

    @Bean
    public IMap<String, Instant> getLockMap() {
        return hazelcast.getMap("anshar.locks");
    }

    @Bean
    public IMap<Enum<HealthCheckKey>, Instant> getHealthCheckMap() {
        return hazelcast.getMap("anshar.admin.health");
    }

    public String listNodes(Boolean includeStats) {
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

                    if (includeStats != null && includeStats) {
                        JSONObject stats = new JSONObject();
                        Collection<DistributedObject> distributedObjects = hazelcast.getDistributedObjects();
                        for (DistributedObject distributedObject : distributedObjects) {
                            stats.put(distributedObject.getName(), hazelcast.getMap(distributedObject.getName()).getLocalMapStats().toJson());
                        }

                        obj.put("localmapstats", stats);
                    }
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
    public IMap<String, Instant> getHeartbeatTimestampMap() {
        return hazelcast.getMap("anshar.subscriptions.outbound.heartbeat");
    }

    @Bean
    public ISet<String> getUnhealthySubscriptionsSet() {
        return hazelcast.getSet("anshar.subscriptions.unhealthy.notified");
    }

    @Bean
    public IMap<String, Map<SubscriptionSetup.SubscriptionType, Set<String>>> getUnmappedIds() {
        return hazelcast.getMap("anshar.mapping.unmapped");
    }

    @Bean
    public IMap<String, List<String>> getValidationResultRefMap() {
        return hazelcast.getMap("anshar.validation.results.ref");
    }
    @Bean
    public IMap<String, Siri> getValidationResultSiriMap() {
        return hazelcast.getMap("anshar.validation.results.siri");
    }
    @Bean
    public IMap<String, JSONObject> getValidationResultJsonMap() {
        return hazelcast.getMap("anshar.validation.results.json");
    }

    @Bean
    public IMap<String,BigInteger> getObjectCounterMap() {
        return hazelcast.getMap("anshar.activity.objectcount");
    }
}
