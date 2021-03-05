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

import com.hazelcast.collection.ISet;
import com.hazelcast.config.SerializerConfig;
import com.hazelcast.core.DistributedObject;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.LifecycleEvent;
import com.hazelcast.map.IMap;
import com.hazelcast.replicatedmap.ReplicatedMap;
import no.rutebanken.anshar.config.AnsharConfiguration;
import no.rutebanken.anshar.data.RequestorRefStats;
import no.rutebanken.anshar.data.SiriObjectStorageKey;
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
import uk.org.siri.siri20.PtSituationElement;
import uk.org.siri.siri20.VehicleActivityStructure;

import javax.annotation.PreDestroy;
import java.math.BigInteger;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class ExtendedHazelcastService extends HazelCastService {

    private Logger logger = LoggerFactory.getLogger(ExtendedHazelcastService.class);

    public ExtendedHazelcastService(@Autowired KubernetesService kubernetesService, @Autowired AnsharConfiguration cfg) {
        super(kubernetesService);
    }

    public void addBeforeShuttingDownHook(Runnable destroyFunction) {
        hazelcast.getLifecycleService().addLifecycleListener(lifecycleEvent -> {
            logger.info("Lifecycle: Event triggered: {}", lifecycleEvent);
            if (lifecycleEvent.getState().equals(LifecycleEvent.LifecycleState.SHUTTING_DOWN)) {
                logger.info("Lifecycle: Shutting down - committing all changes.");
                destroyFunction.run();
            }
            else {
                logger.info("Lifecycle: Ignoring event {}", lifecycleEvent);
            }
        });
        logger.info("Lifecycle: Shutdownhook added.");
    }

    @PreDestroy
    private void customShutdown() {
        logger.info("Attempting to shutdown through LifecycleService");
        hazelcast.getLifecycleService().shutdown();
        logger.info("Shutdown through LifecycleService");
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
    public IMap<SiriObjectStorageKey, PtSituationElement> getSituationsMap(){
        return hazelcast.getMap("anshar.sx");
    }

    @Bean
    public IMap<String, Set<SiriObjectStorageKey>> getSituationChangesMap() {
        return hazelcast.getMap("anshar.sx.changes");
    }

    @Bean
    public IMap<SiriObjectStorageKey, EstimatedVehicleJourney> getEstimatedTimetablesMap(){
        return hazelcast.getMap("anshar.et");
    }

    @Bean
    public IMap<String, Set<SiriObjectStorageKey>> getEstimatedTimetableChangesMap() {
        return hazelcast.getMap("anshar.et.changes");
    }

    @Bean
    public ReplicatedMap<SiriObjectStorageKey, String> getIdForPatternChangesMap() {
        return hazelcast.getReplicatedMap("anshar.et.index.pattern");
    }

    @Bean
    public ReplicatedMap<SiriObjectStorageKey, String> getSxChecksumMap() {
        return hazelcast.getReplicatedMap("anshar.sx.checksum.cache");
    }

    @Bean
    public ReplicatedMap<SiriObjectStorageKey, String> getEtChecksumMap() {
        return hazelcast.getReplicatedMap("anshar.et.checksum.cache");
    }

    @Bean
    public ReplicatedMap<SiriObjectStorageKey, String> getVmChecksumMap() {
        return hazelcast.getReplicatedMap("anshar.vm.checksum.cache");
    }

    @Bean
    public ReplicatedMap<SiriObjectStorageKey, ZonedDateTime> getIdStartTimeMap() {
        return hazelcast.getReplicatedMap("anshar.et.index.startTime");
    }

    @Bean
    public IMap<SiriObjectStorageKey, VehicleActivityStructure> getVehiclesMap(){
        return hazelcast.getMap("anshar.vm");
    }

    @Bean
    public IMap<String, Set<SiriObjectStorageKey>> getVehicleChangesMap() {
        return hazelcast.getMap("anshar.vm.changes");
    }

    @Bean
    public ReplicatedMap<String,SubscriptionSetup> getSubscriptionsMap() {
        return hazelcast.getReplicatedMap("anshar.subscriptions.active");
    }

    @Bean
    public ReplicatedMap<String,String> getValidationFilterMap() {
        return hazelcast.getReplicatedMap("anshar.validation.filter");
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
