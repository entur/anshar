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

package no.rutebanken.anshar.data;

import com.hazelcast.map.IMap;
import no.rutebanken.anshar.config.AnsharConfiguration;
import no.rutebanken.anshar.data.collections.ExtendedHazelcastService;
import no.rutebanken.anshar.data.util.TimingTracer;
import no.rutebanken.anshar.routes.siri.helpers.SiriObjectFactory;
import no.rutebanken.anshar.subscription.SiriDataType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import uk.org.siri.siri21.FacilityConditionStructure;
import uk.org.siri.siri21.HalfOpenTimestampOutputRangeStructure;
import uk.org.siri.siri21.MessageRefStructure;
import uk.org.siri.siri21.Siri;

import javax.annotation.PostConstruct;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Component
public class Facilities extends SiriRepository<FacilityConditionStructure> {
    private final Logger logger = LoggerFactory.getLogger(Facilities.class);

    @Autowired
    private IMap<SiriObjectStorageKey , FacilityConditionStructure> facilityConditions;

    @Autowired
    @Qualifier("getFmChecksumMap")
    private IMap<SiriObjectStorageKey,String> checksumCache;

    @Autowired
    @Qualifier("getFacilitiesChangesMap")
    private IMap<String, Set<SiriObjectStorageKey>> changesMap;

    @Autowired
    @Qualifier("getLastFmUpdateRequest")
    private IMap<String, Instant> lastUpdateRequested;

    @Autowired
    private SiriObjectFactory siriObjectFactory;

    @Autowired
    private AnsharConfiguration configuration;

    @Autowired
    ExtendedHazelcastService hazelcastService;

    protected Facilities() {
        super(SiriDataType.FACILITY_MONITORING);
    }

    @PostConstruct
    private void initializeUpdateCommitter() {
        super.initBufferCommitter(hazelcastService, lastUpdateRequested, changesMap, configuration.getChangeBufferCommitFrequency());

        enableCache(facilityConditions);

//        linkEntriesTtl(situationElements, changesMap, checksumCache);
        if (configuration.processFM()) {
            createCleanupJob(facilityConditions, changesMap, configuration.getCleanupIntervalSeconds(), -1);
        }
    }

    /**
     * @return All situationElements
     */
    public Collection<FacilityConditionStructure> getAll() {
        return facilityConditions.values();
    }

    public Map<SiriObjectStorageKey, FacilityConditionStructure> getAllAsMap() {
        return facilityConditions;
    }

    public int getSize() {
        return facilityConditions.size();
    }

    @Override
    IMap<SiriObjectStorageKey, FacilityConditionStructure> getMainMap() {
        return facilityConditions;
    }

    @Override
    public void clearAllByDatasetId(String datasetId) {

        Set<SiriObjectStorageKey> idsToRemove = facilityConditions.keySet(createHzCodespacePredicate(datasetId));

        logger.warn("Removing all data ({} ids) for {}", idsToRemove.size(), datasetId);

        for (SiriObjectStorageKey id : idsToRemove) {
            facilityConditions.remove(id);
            checksumCache.remove(id);
        }
    }

    public void clearAll() {
        logger.error("Deleting all data - should only be used in test!!!");
        facilityConditions.clear();
        checksumCache.clear();
        cache.clear();
    }

    public Siri createServiceDelivery(String requestorId, String datasetId, String clientName, int maxSize) {

        requestorRefRepository.touchRequestorRef(requestorId, datasetId, clientName, SiriDataType.FACILITY_MONITORING);

        int trackingPeriodMinutes = configuration.getTrackingPeriodMinutes();

        boolean isAdHocRequest = false;

        if (requestorId == null) {
            requestorId = UUID.randomUUID().toString();
            isAdHocRequest = true;
        }

        // Get all relevant ids
        Set<SiriObjectStorageKey> allIds = new HashSet<>();
        Set<SiriObjectStorageKey> idSet = changesMap.getOrDefault(requestorId, allIds);

        if (idSet == allIds) {
            idSet.addAll(facilityConditions.keySet());
        }

        //Filter by datasetId
        Set<SiriObjectStorageKey> requestedIds = idSet.stream()
                .filter(key -> datasetId == null || key.getCodespaceId().equals(datasetId))
                .collect(Collectors.toSet());
        long start = System.currentTimeMillis();

        Set<SiriObjectStorageKey> sizeLimitedIds = requestedIds.stream().limit(maxSize).collect(Collectors.toSet());

        Boolean isMoreData = sizeLimitedIds.size() < requestedIds.size();

        //Remove collected objects
        sizeLimitedIds.forEach(idSet::remove);

        Collection<FacilityConditionStructure> values = facilityConditions.getAll(sizeLimitedIds).values();

        Siri siri = siriObjectFactory.createFMServiceDelivery(values);
        siri.getServiceDelivery().setMoreData(isMoreData);
        logger.info("Creating SIRI-delivery: {} ms", (System.currentTimeMillis()-start));

        if (isAdHocRequest) {
            logger.info("Returning {}, no requestorRef is set", sizeLimitedIds.size());
        } else {


            MessageRefStructure msgRef = new MessageRefStructure();
            msgRef.setValue(requestorId);
            siri.getServiceDelivery().setRequestMessageRef(msgRef);

            //Update change-tracker
            updateChangeTrackers(lastUpdateRequested, changesMap, requestorId, idSet, trackingPeriodMinutes, TimeUnit.MINUTES);

            logger.info("Returning {}, {} left for requestorRef {}", sizeLimitedIds.size(), idSet.size(), requestorId);
        }

        return siri;
    }
    /**
     * @return All vehicle activities that are still valid
     */
    public Collection<FacilityConditionStructure> getAll(String datasetId) {
        if (datasetId == null) {
            return getAll();
        }

        return getValuesByDatasetId(facilityConditions, datasetId);
    }


    /**
     * @return All vehicle activities that have been updated since last request from requestor
     */
    public Collection<FacilityConditionStructure> getAllUpdates(String requestorId, String datasetId) {
        if (requestorId != null) {

            Set<SiriObjectStorageKey> idSet = changesMap.get(requestorId);
            lastUpdateRequested.set(requestorId, Instant.now(), configuration.getTrackingPeriodMinutes(), TimeUnit.MINUTES);
            if (idSet != null) {
                Set<SiriObjectStorageKey> datasetFilteredIdSet = new HashSet<>();

                if (datasetId != null) {
                    idSet.stream().filter(key -> key.getCodespaceId().equals(datasetId)).forEach(datasetFilteredIdSet::add);
                } else {
                    datasetFilteredIdSet.addAll(idSet);
                }
                Collection<FacilityConditionStructure> changes = facilityConditions.getAll(datasetFilteredIdSet).values();

                // Data may have been updated
                Set<SiriObjectStorageKey> existingSet = changesMap.get(requestorId);
                if (existingSet == null) {
                    existingSet = new HashSet<>();
                }
                //Remove returned ids
                existingSet.removeAll(idSet);

                updateChangeTrackers(lastUpdateRequested, changesMap, requestorId, existingSet, configuration.getTrackingPeriodMinutes(), TimeUnit.MINUTES);

                logger.info("Returning {} changes to requestorRef {}", changes.size(), requestorId);
                return changes;
            } else {
                logger.info("Returning all to requestorRef {}", requestorId);
            }

            updateChangeTrackers(lastUpdateRequested, changesMap, requestorId, new HashSet<>(), configuration.getTrackingPeriodMinutes(), TimeUnit.MINUTES);

        }

        return getAll(datasetId);
    }

    public long getExpiration(FacilityConditionStructure facilityCondition) {
        HalfOpenTimestampOutputRangeStructure validityPeriod = facilityCondition.getValidityPeriod();

        ZonedDateTime expiry = null;

        if (validityPeriod != null && validityPeriod.getEndTime() != null) {
            expiry = validityPeriod.getEndTime();
        }

        if (expiry != null && expiry.getYear() < 2100) {
            return ZonedDateTime.now().until(expiry.plusMinutes(configuration.getFmGraceperiodMinutes()), ChronoUnit.MILLIS);
        } else {
            // No expiration set - keep "forever"
            return  ZonedDateTime.now().until(ZonedDateTime.now().plusYears(10), ChronoUnit.MILLIS);
        }
    }

    public Collection<FacilityConditionStructure> addAll(String datasetId, List<FacilityConditionStructure> fmList) {
        Map<SiriObjectStorageKey, FacilityConditionStructure> changes = new HashMap<>();
        Map<SiriObjectStorageKey, String> checksumTmp = new HashMap<>();

        AtomicInteger alreadyExpiredCounter = new AtomicInteger(0);
        AtomicInteger ignoredCounter = new AtomicInteger(0);
        fmList.stream()
                .forEach(situation -> {

            if (situation.getFacilityRef() == null) {
                logger.warn("FacilityRef is null, skipping");
                return;
            }

            TimingTracer timingTracer = new TimingTracer("single-fm");

            SiriObjectStorageKey key = createKey(datasetId, situation);
            timingTracer.mark("createKey");
            String currentChecksum = null;
            try {
                currentChecksum = getChecksum(situation);
                timingTracer.mark("getChecksum");
            } catch (Exception e) {
                //Ignore - data will be updated
            }

            String existingChecksum = checksumCache.get(key);
            timingTracer.mark("checksumCache.get");
            boolean updated;
            if (existingChecksum != null && facilityConditions.containsKey(key)) { // Checksum not compared if actual situation does not exist
                //Exists - compare values
                updated =  !(currentChecksum.equals(existingChecksum));

            } else {
                //Does not exist
                updated = true;
            }
            timingTracer.mark("compareChecksum");

            if (updated) {
                timingTracer.mark("keepByProgressStatus");
                long expiration = getExpiration(situation);
                timingTracer.mark("getExpiration");
                if (expiration > 0) { //expiration < 0 => already expired
                    changes.put(key, situation);
                    checksumTmp.put(key, currentChecksum);
                } else if (facilityConditions.containsKey(key)) {
                    // Situation is no longer valid
                    facilityConditions.delete(key);
                    timingTracer.mark("situationElements.delete");
                    checksumCache.remove(key);
                    timingTracer.mark("checksumCache.remove");
                }
                if (expiration < 0) {
                    alreadyExpiredCounter.incrementAndGet();
                }
            } else {
                ignoredCounter.incrementAndGet();
            }

            long elapsed = timingTracer.getTotalTime();
            if (elapsed > 500) {
                logger.info("Adding FM-object with key {} took {} ms: {}", key, elapsed, timingTracer);
            }
        });
        TimingTracer timingTracer = new TimingTracer("all-fm [" + changes.size() + " changes]");

        logger.info("Updated {} (of {}) :: Already expired: {}, Unchanged: {}", changes.size(), fmList.size(), alreadyExpiredCounter.get(), ignoredCounter.get());

        checksumCache.setAll(checksumTmp);
        timingTracer.mark("checksumCache.setAll");
        facilityConditions.setAll(changes);
        timingTracer.mark("monitoredVehicles.setAll");

        markDataReceived(SiriDataType.FACILITY_MONITORING, datasetId, fmList.size(), changes.size(), alreadyExpiredCounter.get(), ignoredCounter.get());
        timingTracer.mark("markDataReceived");

        markIdsAsUpdated(changes.keySet());
        timingTracer.mark("markIdsAsUpdated");

        if (timingTracer.getTotalTime() > 1000) {
            logger.info(timingTracer.toString());
        }

        return changes.values();
    }

    public FacilityConditionStructure add(String datasetId, FacilityConditionStructure situation) {
        if (situation == null) {
            return null;
        }
        List<FacilityConditionStructure> situationList = new ArrayList<>();
        situationList.add(situation);
        addAll(datasetId, situationList);
        return facilityConditions.get(createKey(datasetId, situation));
    }

    private static SiriObjectStorageKey createKey(String datasetId, FacilityConditionStructure element) {

        String key = datasetId + ":" +
                (element.getFacilityRef() != null ? element.getFacilityRef().getValue() : "null");

        return new SiriObjectStorageKey(datasetId, null, key);
    }
}
