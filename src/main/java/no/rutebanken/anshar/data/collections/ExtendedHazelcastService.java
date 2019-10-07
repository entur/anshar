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

package no.rutebanken.anshar.data.collections;

import com.hazelcast.config.SerializerConfig;
import com.hazelcast.core.*;
import no.rutebanken.anshar.config.AnsharConfiguration;
import no.rutebanken.anshar.data.RequestorRefStats;
import no.rutebanken.anshar.routes.outbound.OutboundSubscriptionSetup;
import no.rutebanken.anshar.subscription.SiriDataType;
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

import java.math.BigInteger;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
public class ExtendedHazelcastService extends HazelCastService {

    private Logger logger = LoggerFactory.getLogger(ExtendedHazelcastService.class);

    private AnsharConfiguration cfg;

    public ExtendedHazelcastService(@Autowired KubernetesService kubernetesService, @Autowired AnsharConfiguration cfg) {
        super(kubernetesService, cfg.getHazelcastManagementUrl());
        this.cfg = cfg;
    }

    public HazelcastInstance getHazelcastInstance() {
        return hazelcast;
    }

    @Override
    public List<SerializerConfig> getSerializerConfigs() {

        return Arrays.asList(
                new SerializerConfig()
                    .setTypeClass(EstimatedVehicleJourney.class)
                    .setImplementation(new KryoSerializer()),
                new SerializerConfig()
                    .setTypeClass(PtSituationElement.class)
                    .setImplementation(new KryoSerializer()),
                new SerializerConfig()
                    .setTypeClass(VehicleActivityStructure.class)
                    .setImplementation(new KryoSerializer()),
                new SerializerConfig()
                    .setTypeClass(JSONObject.class)
                    .setImplementation(new KryoSerializer())

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
    public ReplicatedMap<String, String> getIdForPatternChangesMap() {
        return hazelcast.getReplicatedMap("anshar.et.index.pattern");
    }

    @Bean
    public ReplicatedMap<String, String> getSxChecksumMap() {
        return hazelcast.getReplicatedMap("anshar.sx.checksum.cache");
    }

    @Bean
    public ReplicatedMap<String, String> getEtChecksumMap() {
        return hazelcast.getReplicatedMap("anshar.et.checksum.cache");
    }

    @Bean
    public IMap<String, String> getVmChecksumMap() {
        return hazelcast.getMap("anshar.vm.checksum.cache");
    }

    @Bean
    public ReplicatedMap<String, ZonedDateTime> getIdStartTimeMap() {
        return hazelcast.getReplicatedMap("anshar.et.index.startTime");
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
    public ReplicatedMap<String,SubscriptionSetup> getSubscriptionsMap() {
        return hazelcast.getReplicatedMap("anshar.subscriptions.active");
    }

    @Bean
    public ReplicatedMap<String, Instant> getLastActivityMap() {
        return hazelcast.getReplicatedMap("anshar.activity.last");
    }

    @Bean
    public ReplicatedMap<String, Instant> getDataReceivedMap() {
        return hazelcast.getReplicatedMap("anshar.subscriptions.data.received");
    }


    @Bean
    public IMap<String, Long> getReceivedBytesMap() {
        return hazelcast.getMap("anshar.subscriptions.data.received.bytes");
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
        return migrateReplicatedMapToIMap("anshar.activity.last.sx.update.request");
    }

    @Bean
    public IMap<String, Instant> getLastVmUpdateRequest() {
        return migrateReplicatedMapToIMap("anshar.activity.last.vm.update.request");
    }

    /**
     * Temporary method that migrates existing data from ReplicatedMap to IMap
     * @param mapName
     * @return
     */
    private IMap<String, Instant> migrateReplicatedMapToIMap(String mapName) {
        IMap<String, Instant> hazelcastMap = hazelcast.getMap(mapName);

        ReplicatedMap<String, Instant> replicatedMap = hazelcast.getReplicatedMap(mapName);
        if (!replicatedMap.isEmpty()) {

            // TTL-values are specifically for the change-tracker maps used in this migration
            replicatedMap.forEach((key, value) -> hazelcastMap.set(key, value, cfg.getTrackingPeriodMinutes(), TimeUnit.MINUTES));
            replicatedMap.clear();

            logger.info("Migrated {} objects from ReplicatedMap to IMap for {}.", hazelcastMap.size(), mapName);
        }
        return hazelcastMap;
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
    public IMap<String, String> getForceRestartMap() {
        return hazelcast.getMap("anshar.subscriptions.restart");
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
    public IMap<String, Map<SiriDataType, Set<String>>> getUnmappedIds() {
        return hazelcast.getMap("anshar.mapping.unmapped");
    }

    @Bean
    public IMap<String, List<String>> getValidationResultRefMap() {
        return hazelcast.getMap("anshar.validation.results.ref");
    }
    @Bean
    public IMap<String, byte[]> getValidationResultSiriMap() {
        return hazelcast.getMap("anshar.validation.results.siri");
    }
    @Bean
    public IMap<String, JSONObject> getValidationResultJsonMap() {
        return hazelcast.getMap("anshar.validation.results.json");
    }
    @Bean
    public IMap<String, Long> getValidationSizeTracker() {
        return hazelcast.getMap("anshar.validation.results.size");
    }

    @Bean
    public IMap<String,BigInteger> getObjectCounterMap() {
        return hazelcast.getMap("anshar.activity.objectcount");
    }
    @Bean
    public IMap<String[], RequestorRefStats> getRequestorRefs() {
        return hazelcast.getMap("anshar.activity.requestorref");
    }
}
