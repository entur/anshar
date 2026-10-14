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

package no.rutebanken.anshar.routes.health;

import com.hazelcast.map.IMap;
import no.rutebanken.anshar.data.collections.ExtendedHazelcastService;
import no.rutebanken.anshar.data.collections.HealthCheckKey;
import no.rutebanken.anshar.subscription.SiriDataType;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@SuppressWarnings("unchecked")
@Service
@Configuration
public class HealthManager {

    private final Logger logger = LoggerFactory.getLogger(HealthManager.class);

    @Autowired
    @Qualifier("getHealthCheckMap")
    private IMap<Enum<HealthCheckKey>, Instant> healthCheckMap;

    @Autowired
    private ExtendedHazelcastService hazelcastService;

    @Autowired
    @Qualifier("getUnmappedIds")
    private IMap<String, Map<SiriDataType, Set<String>>> unmappedIds;

    @Value("${anshar.admin.health.allowed.inactivity.seconds:999}")
    private long allowedInactivityTime;

    @Value("${anshar.healthcheck.interval.seconds}")
    private int healthCheckInterval = 30;

    @Value("${anshar.mapping.unmapped.max.ids:1000}")
    private int maxUnmappedIds = 1000;

    // Max number of ids, per dataset and datatype, that are kept until there is room
    public static final int MAX_PENDING_UNMAPPED_IDS = 10000;

    // Ids that could not be added because the limit was reached - added when there is room
    private final Map<String, Set<String>> pendingUnmappedIds = new ConcurrentHashMap<>();

    /**
     * Only checks the local member - a distributed map operation blocks while the owner of the partition
     * is leaving the cluster, which made the liveness probe kill healthy pods during rollouts.
     */
    public boolean isHazelcastAlive() {
        return hazelcastService.getHazelcastInstance().getLifecycleService().isRunning();
    }

    @Bean
    public Instant serverStartTime() {
        if (!healthCheckMap.containsKey(HealthCheckKey.SERVER_START_TIME)) {
            healthCheckMap.set(HealthCheckKey.SERVER_START_TIME, Instant.now());
        }
        return healthCheckMap.get(HealthCheckKey.SERVER_START_TIME);
    }

    public void dataReceived() {
        healthCheckMap.set(HealthCheckKey.HEALTH_CHECK_INCOMING_DATA, Instant.now());
    }


    public boolean isReceivingData() {
        Instant lastReceivedData = healthCheckMap.get(HealthCheckKey.HEALTH_CHECK_INCOMING_DATA);
        if (lastReceivedData != null) {
            long lastReceivedMillis = lastReceivedData.toEpochMilli();

            long seconds = (Instant.now().toEpochMilli() - lastReceivedMillis) / (1000);
            if (seconds > allowedInactivityTime) {
                logger.warn("Last received data: {}, {} seconds ago", lastReceivedData, seconds);
                return false;
            }
        }
        return true;
    }

    public long getSecondsSinceDataReceived() {
        Instant lastReceivedData = healthCheckMap.get(HealthCheckKey.HEALTH_CHECK_INCOMING_DATA);
        if (lastReceivedData != null) {
            long lastReceivedMillis = lastReceivedData.toEpochMilli();

            return (Instant.now().toEpochMilli() - lastReceivedMillis) / (1000);
        }
        return -1;
    }

    public Map<SiriDataType, Set<String>> getUnmappedIds(String datasetId) {
        return unmappedIds.getOrDefault(datasetId, new HashMap<>());
    }

    public synchronized Map<SiriDataType, Set<String>> clearUnmappedIds(String datasetId) {
        for (SiriDataType type : SiriDataType.values()) {
            pendingUnmappedIds.remove(pendingKey(type, datasetId));
        }
        return unmappedIds.remove(datasetId);
    }

    public JSONObject getUnmappedIdsAsJson(String datasetId) {
        JSONObject result = new JSONObject();
        if (datasetId == null) {
            return result;
        }

        Map<SiriDataType, Set<String>> dataSetUnmapped = getUnmappedIds(datasetId);

        if (dataSetUnmapped != null) {
            JSONArray typesList = new JSONArray();
            for (SiriDataType type : dataSetUnmapped.keySet()) {
                JSONObject typeObject = new JSONObject();
                Set<String> unmappedIdsForType = dataSetUnmapped.get(type);

                JSONArray unmapped = new JSONArray();
                unmapped.addAll(unmappedIdsForType);

                typeObject.put("type", type.toString());
                typeObject.put("count", unmappedIdsForType.size());
                typeObject.put("ids", unmapped);

                typesList.add(typeObject);
            }
            result.put("unmapped", typesList);
        }
        return result;
    }

    public synchronized void addUnmappedId(SiriDataType type, String datasetId, String id) {
        Map<SiriDataType, Set<String>> unmappedIds = getUnmappedIds(datasetId);

        Set<String> ids = unmappedIds.getOrDefault(type, new HashSet<>());
        if (ids.contains(id)) {
            return;
        }
        if (ids.size() >= maxUnmappedIds) {
            // Limit reached - keep id until there is room
            Set<String> pending = pendingUnmappedIds.computeIfAbsent(pendingKey(type, datasetId), k -> new HashSet<>());
            if (pending.size() < MAX_PENDING_UNMAPPED_IDS) {
                pending.add(id);
            }
            return;
        }
        ids.add(id);
        addPendingUnmappedIds(type, datasetId, ids);
        unmappedIds.put(type, ids);

        this.unmappedIds.set(datasetId, unmappedIds);
    }

    public synchronized void removeUnmappedId(SiriDataType type, String datasetId, String id) {
        Set<String> pending = pendingUnmappedIds.get(pendingKey(type, datasetId));
        if (pending != null) {
            pending.remove(id);
        }

        Map<SiriDataType, Set<String>> unmappedIds = getUnmappedIds(datasetId);

        Set<String> ids = unmappedIds.get(type);
        if (ids == null || !ids.remove(id)) {
            return;
        }
        addPendingUnmappedIds(type, datasetId, ids);

        this.unmappedIds.set(datasetId, unmappedIds);
    }

    private void addPendingUnmappedIds(SiriDataType type, String datasetId, Set<String> ids) {
        Set<String> pending = pendingUnmappedIds.get(pendingKey(type, datasetId));
        if (pending != null) {
            for (Iterator<String> it = pending.iterator(); it.hasNext() && ids.size() < maxUnmappedIds; ) {
                ids.add(it.next());
                it.remove();
            }
        }
    }

    private static String pendingKey(SiriDataType type, String datasetId) {
        return datasetId + "|" + type;
    }
}
