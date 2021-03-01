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

import no.rutebanken.anshar.data.RequestorRefStats;
import no.rutebanken.anshar.data.SiriObjectStorageKey;
import no.rutebanken.anshar.routes.outbound.OutboundSubscriptionSetup;
import no.rutebanken.anshar.subscription.SiriDataType;
import no.rutebanken.anshar.subscription.SubscriptionSetup;
import org.json.simple.JSONObject;
import org.redisson.Redisson;
import org.redisson.api.RLock;
import org.redisson.api.RMap;
import org.redisson.api.RMapCache;
import org.redisson.api.RSet;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;
import uk.org.siri.siri20.EstimatedVehicleJourney;
import uk.org.siri.siri20.PtSituationElement;
import uk.org.siri.siri20.VehicleActivityStructure;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class RedisService {

    private Logger logger = LoggerFactory.getLogger(RedisService.class);

    RedissonClient redisson;

    public RedisService(RedisProperties redisProperties) {

        logger.info("Redis configured with url = " + redisProperties.getHost() + ":" + redisProperties.getPort());

        Config config = new Config();
        config.useReplicatedServers()
                .addNodeAddress("redis://" + redisProperties.getHost() + ":" + redisProperties.getPort())
                .setSubscriptionConnectionMinimumIdleSize(10)
                .setSubscriptionConnectionPoolSize(500)
                .setSlaveConnectionMinimumIdleSize(10)
                .setSlaveConnectionPoolSize(640)
                .setMasterConnectionMinimumIdleSize(32)
                .setMasterConnectionPoolSize(640)
                .setRetryAttempts(5);;

        redisson = Redisson.create(config);

    }

    public void shutdown() {
        redisson.shutdown();
    }

    public void clearAllRedisMaps() {
        logger.warn("Flushing all data");
        redisson.getKeys().flushdb();
        logger.warn("Data flushed");
    }

    @Bean
    public RMapCache<SiriObjectStorageKey, PtSituationElement> getSituationsMap(){
        return redisson.getMapCache("anshar.sx");
    }

    @Bean
    public RMapCache<SiriObjectStorageKey, EstimatedVehicleJourney> getEstimatedTimetablesMap(){
        return redisson.getMapCache("anshar.et");
    }

    @Bean
    public RMapCache<SiriObjectStorageKey, VehicleActivityStructure> getVehiclesMap(){
        return redisson.getMapCache("anshar.vm");
    }

    @Bean
    public RMapCache<String, Set<SiriObjectStorageKey>> getSituationChangesMap() {
        return redisson.getMapCache("anshar.sx.changes");
    }

    @Bean
    public RMapCache<String, Set<SiriObjectStorageKey>> getEstimatedTimetableChangesMap() {
        return redisson.getMapCache("anshar.et.changes");
    }

    @Bean
    public RMapCache<SiriObjectStorageKey, String> getIdForPatternChangesMap() {
        return redisson.getMapCache("anshar.et.index.pattern");
    }

    @Bean
    public RMapCache<SiriObjectStorageKey, String> getSxChecksumMap() {
        return redisson.getMapCache("anshar.sx.checksum.cache");
    }

    @Bean
    public RMapCache<SiriObjectStorageKey, String> getEtChecksumMap() {
        return redisson.getMapCache("anshar.et.checksum.cache");
    }

    @Bean
    public RMapCache<SiriObjectStorageKey, String> getVmChecksumMap() {
        return redisson.getMapCache("anshar.vm.checksum.cache");
    }

    @Bean
    public RMapCache<SiriObjectStorageKey, ZonedDateTime> getIdStartTimeMap() {
        return redisson.getMapCache("anshar.et.index.startTime");
    }

    @Bean
    public RMapCache<String, Set<SiriObjectStorageKey>> getVehicleChangesMap() {
        return redisson.getMapCache("anshar.vm.changes");
    }

    @Bean
    public RMap<String, SubscriptionSetup> getSubscriptionsMap() {
        return redisson.getMap("anshar.subscriptions.active");
    }

    @Bean
    public RMap<String,String> getValidationFilterMap() {
        return redisson.getMap("anshar.validation.filter");
    }

    @Bean
    public RMap<String, Instant> getLastActivityMap() {
        return redisson.getMap("anshar.activity.last");
    }

    @Bean
    public RMap<String, Instant> getDataReceivedMap() {
        return redisson.getMap("anshar.subscriptions.data.received");
    }


    @Bean
    public RMap<String, Long> getReceivedBytesMap() {
        return redisson.getMap("anshar.subscriptions.data.received.bytes");
    }


    @Bean
    public RMapCache<String, Instant> getLastEtUpdateRequest() {
        return redisson.getMapCache("anshar.activity.last.et.update.request");
    }

    @Bean
    public RMapCache<String, Instant> getLastSxUpdateRequest() {
        return redisson.getMapCache("anshar.activity.last.sx.update.request");
    }

    @Bean
    public RMapCache<String, Instant> getLastVmUpdateRequest() {
        return redisson.getMapCache("anshar.activity.last.vm.update.request");
    }

    @Bean
    public RMap<String, Instant> getActivatedTimestampMap() {
        return redisson.getMap("anshar.activity.activated");
    }

    @Bean
    public RMap<String, Integer> getHitcountMap() {
        return redisson.getMap("anshar.activity.hitcount");
    }

    @Bean
    public RMap<String, String> getForceRestartMap() {
        return redisson.getMap("anshar.subscriptions.restart");
    }

    @Bean
    public RMap<String, Instant> getFailTrackerMap() {
        return redisson.getMap("anshar.activity.failtracker");
    }

    @Bean
    public RMap<Enum<HealthCheckKey>, Instant> getHealthCheckMap() {
        return redisson.getMap("anshar.admin.health");
    }

    @Bean
    public RMapCache<String, OutboundSubscriptionSetup> getOutboundSubscriptionMap() {
        return redisson.getMapCache("anshar.subscriptions.outbound");
    }

    @Bean
    public RMapCache<String, Instant> getHeartbeatTimestampMap() {
        return redisson.getMapCache("anshar.subscriptions.outbound.heartbeat");
    }

    @Bean
    public RSet<String> getUnhealthySubscriptionsSet() {
        return redisson.getSet("anshar.subscriptions.unhealthy.notified");
    }

    @Bean
    public RMap<String, Map<SiriDataType, Set<String>>> getUnmappedIds() {
        return redisson.getMap("anshar.mapping.unmapped");
    }

    @Bean
    public RMap<String, List<String>> getValidationResultRefMap() {
        return redisson.getMap("anshar.validation.results.ref");
    }
    @Bean
    public RMap<String, byte[]> getValidationResultSiriMap() {
        return redisson.getMap("anshar.validation.results.siri");
    }
    @Bean
    public RMap<String, JSONObject> getValidationResultJsonMap() {
        return redisson.getMap("anshar.validation.results.json");
    }
    @Bean
    public RMap<String, Long> getValidationSizeTracker() {
        return redisson.getMap("anshar.validation.results.size");
    }

    @Bean
    public RMap<String, BigInteger> getObjectCounterMap() {
        return redisson.getMap("anshar.activity.objectcount");
    }
    @Bean
    public RMapCache<String[], RequestorRefStats> getRequestorRefs() {
        return redisson.getMapCache("anshar.activity.requestorref");
    }
}
