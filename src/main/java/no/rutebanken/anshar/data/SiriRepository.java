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

import com.google.common.collect.Maps;
import com.hazelcast.core.EntryEvent;
import com.hazelcast.map.IMap;
import com.hazelcast.map.listener.EntryAddedListener;
import com.hazelcast.map.listener.EntryEvictedListener;
import com.hazelcast.map.listener.EntryExpiredListener;
import com.hazelcast.map.listener.EntryRemovedListener;
import com.hazelcast.map.listener.EntryUpdatedListener;
import com.hazelcast.query.Predicate;
import jakarta.xml.bind.DatatypeConverter;
import no.rutebanken.anshar.data.collections.ExtendedHazelcastService;
import no.rutebanken.anshar.metrics.PrometheusMetricsService;
import no.rutebanken.anshar.routes.siri.transformer.ApplicationContextHolder;
import no.rutebanken.anshar.subscription.SiriDataType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.SerializationUtils;
import uk.org.siri.siri21.VehicleActivityStructure;

import java.io.Serializable;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

abstract class SiriRepository<T> {

    private IMap<String, Instant> lastUpdateRequested;
    private IMap<String, Set<SiriObjectStorageKey>> changesMap;

    private final SiriDataType SIRI_DATA_TYPE;

    abstract Collection<T> getAll();

    abstract Map<SiriObjectStorageKey, T> getAllAsMap();

    abstract int getSize();

    abstract Collection<T> getAll(String datasetId);

    abstract Collection<T> getAllUpdates(String requestorId, String datasetId);

    abstract Collection<T> addAll(String datasetId, List<T> ptList);

    abstract T add(String datasetId, T timetableDelivery);

    abstract long getExpiration(T s);

    abstract IMap<SiriObjectStorageKey, T> getMainMap();

    private final Logger logger = LoggerFactory.getLogger(SiriRepository.class);

    protected PrometheusMetricsService metrics;

    final Set<SiriObjectStorageKey> dirtyChanges = Collections.synchronizedSet(new HashSet<>());

    private ScheduledExecutorService singleThreadScheduledExecutor;

    // Shared executor for async change tracker updates to prevent thread leak
    private static final ExecutorService changeTrackerExecutor = Executors.newFixedThreadPool(
        5,
        r -> {
            Thread t = new Thread(r, "change-tracker-updater");
            t.setDaemon(true);
            return t;
        }
    );

    @Autowired
    protected RequestorRefRepository requestorRefRepository;

    Map<SiriObjectStorageKey, T> cache = Maps.newConcurrentMap();

    protected SiriRepository (SiriDataType siriDataType) {
        this.SIRI_DATA_TYPE = siriDataType;
    }

    protected void enableCache(IMap<SiriObjectStorageKey, T> map) {
        enableCache(map, null);
    }

    protected void enableCache(IMap<SiriObjectStorageKey, T> map, java.util.function.Predicate<T> includeInCachePredicate) {

        // Entry added - new data
        map.addEntryListener((EntryAddedListener<SiriObjectStorageKey, T>) entryEvent -> {

            if (includeInCachePredicate == null || includeInCachePredicate.test(entryEvent.getValue())) {
                cache.put(entryEvent.getKey(), entryEvent.getValue());
            }
        }, true);

        // Entry updated - new version
        map.addEntryListener((EntryUpdatedListener<SiriObjectStorageKey, T>) entryEvent -> {

            if (includeInCachePredicate == null || includeInCachePredicate.test(entryEvent.getValue())) {
                cache.put(entryEvent.getKey(), entryEvent.getValue());
            }
        }, true);

        //Entry expired by TTL
        map.addEntryListener((EntryExpiredListener<SiriObjectStorageKey, T>) entryEvent -> {

            cache.remove(entryEvent.getKey());
        }, false);

        // Entry evicted
        map.addEntryListener((EntryEvictedListener<SiriObjectStorageKey, T>) entryEvent -> {

            cache.remove(entryEvent.getKey());
        }, false);

        // Entry removed - e.g. "delete all for codespace"
        map.addEntryListener((EntryRemovedListener<SiriObjectStorageKey, T>) entryEvent -> {

            cache.remove(entryEvent.getKey());
        }, false);

        // Initialize cache
        long t1 = System.currentTimeMillis();

        final Map<SiriObjectStorageKey, T> allAsMap = getAllAsMap();
        if (includeInCachePredicate != null) {
            for (Map.Entry<SiriObjectStorageKey, T> entry : allAsMap.entrySet()) {
                if (includeInCachePredicate.test(entry.getValue())) {
                    cache.put(entry.getKey(), entry.getValue());
                }
            }
        } else {
            cache.putAll(allAsMap);
        }
        logger.info("Cache initialized with {} elements in {} ms", cache.size(), (System.currentTimeMillis()-t1));
    }

    /**
     * Links entries across provided Maps.
     *
     * TTL is set on main map, other maps are linked using EntryListeners:
     *   When an object is removed/expired from the main map, it is also removed from the linked maps
     *
     * @param map
     * @param linkedMaps
     */
    void linkEntriesTtl(IMap<SiriObjectStorageKey, T> map,  IMap<String, Set<SiriObjectStorageKey>> linkedChangeMap, Map<SiriObjectStorageKey, ? extends Object>... linkedMaps) {
        {

            // Entry added - new data
            map.addEntryListener((EntryAddedListener<SiriObjectStorageKey, T>) entryEvent -> {
                map.setTtl(entryEvent.getKey(), getExpiration(entryEvent.getValue()), TimeUnit.MILLISECONDS);
            }, true);

            // Entry updated - new version
            map.addEntryListener((EntryUpdatedListener<SiriObjectStorageKey, T>) entryEvent -> {
                map.setTtl(entryEvent.getKey(), getExpiration(entryEvent.getValue()), TimeUnit.MILLISECONDS);
            }, true);

            //Entry expired by TTL
            map.addEntryListener((EntryExpiredListener<SiriObjectStorageKey, T>) entryEvent -> {
                removeFromLinked(linkedChangeMap, entryEvent, linkedMaps);
            }, false);

            // Entry evicted
            map.addEntryListener((EntryEvictedListener<SiriObjectStorageKey, T>) entryEvent -> {
                removeFromLinked(linkedChangeMap, entryEvent, linkedMaps);
            }, false);

            // Entry removed - e.g. "delete all for codespace"
            map.addEntryListener((EntryRemovedListener<SiriObjectStorageKey, T>) entryEvent -> {
                removeFromLinked(linkedChangeMap, entryEvent, linkedMaps);
            }, false);
        }
    }

    void createCleanupJob(IMap<SiriObjectStorageKey, T> map, IMap<String, Set<SiriObjectStorageKey>> linkedChangeMap, long cleanupInterval, long maxValidityMillis) {

        logger.info("Initializing scheduled cleanup job with interval {} seconds", cleanupInterval);
        Executors.newSingleThreadScheduledExecutor()
                .scheduleWithFixedDelay(() -> removeExpired(map, linkedChangeMap, maxValidityMillis), cleanupInterval, cleanupInterval, TimeUnit.SECONDS);
    }

    private void removeExpired(IMap<SiriObjectStorageKey, T> map, IMap<String, Set<SiriObjectStorageKey>> linkedChangeMap, long maxValidityMillis) {
        try {
            logger.debug("Cleaning up expired objects");
            long t1 = System.currentTimeMillis();
            Set<SiriObjectStorageKey> expired = map.entrySet().stream()
                    .filter(entry -> {
                        // Check if entry is expired
                        if (getExpiration(entry.getValue()) < 0) {
                            return true;
                        }
                        // Check if entry is older than maxValidityMillis
                        if (maxValidityMillis > 0) {
                            if (entry.getValue() instanceof VehicleActivityStructure) {
                                ZonedDateTime recordedAtTime = ((VehicleActivityStructure) entry.getValue()).getRecordedAtTime();
                                long timeSinceUpdate = Math.abs(ZonedDateTime.now().until(recordedAtTime, ChronoUnit.MILLIS));
                                return timeSinceUpdate > maxValidityMillis;
                            }
                        }
                        return false;
                    })
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toSet());

            long t2 = System.currentTimeMillis();
            if (!expired.isEmpty()) {
                for (SiriObjectStorageKey key : expired) {
                    map.delete(key);
                }
            }

            long t3 = System.currentTimeMillis();
            if (!expired.isEmpty()) {
                for (String requestorId : linkedChangeMap.keySet()) {
                    Set<SiriObjectStorageKey> keys = linkedChangeMap.get(requestorId);
                    if (keys != null) {
                        keys.removeAll(expired);
                        if (keys.isEmpty()) {
                            linkedChangeMap.remove(requestorId);
                        }
                    }
                }
            }
            long totalCleanupTime = System.currentTimeMillis() - t1;
            if (totalCleanupTime > 1000) {
                logger.info("Cleaning {} expired objects took {} ms, finding {} ms, removing {}, changes {} ms, now have {} objects of type {}",
                        expired.size(), totalCleanupTime, (t2 - t1), (t3 - t2), (System.currentTimeMillis() - t3), map.size(), SIRI_DATA_TYPE);
            }
        } catch (Throwable t) {
            //Catch everything to avoid executor being killed
            logger.info("Exception caught when cleaning up expired data", t);
        }
    }

    private void removeFromLinked(IMap<String, Set<SiriObjectStorageKey>> linkedChangeMap, EntryEvent<SiriObjectStorageKey, T> entryEvent, Map<SiriObjectStorageKey, ?>[] linkedMaps) {
        for (Map<SiriObjectStorageKey, ?> linkedMap : linkedMaps) {
            linkedMap.remove(entryEvent.getKey());
        }
        for (Set<SiriObjectStorageKey> changes : linkedChangeMap.values()) {
            changes.remove(entryEvent.getKey());
        }
    }

    public Collection<T> getAllCachedUpdates(
            String requestorId, String datasetId, String clientTrackingName
    ) {
        return getAllCachedUpdates(requestorId, datasetId, null, clientTrackingName, null);
    }
    public Collection<T> getAllCachedUpdates(
            String requestorId, String datasetId, String lineRef, String clientTrackingName, Integer maxSize
    ) {
        if (maxSize == null) {
            maxSize = Integer.MAX_VALUE;
        }

        if (requestorId != null) {
            try {
                requestorRefRepository.touchRequestorRef(requestorId,
                    datasetId,
                    clientTrackingName,
                    SIRI_DATA_TYPE
                );

                if (changesMap.containsKey(requestorId)) {
                    Set<SiriObjectStorageKey> changes = changesMap.get(requestorId);

                    changes = changes.stream()
                        .filter((k) -> datasetId == null || codespaceMatches(datasetId, k))
                        .filter((k) -> lineRef == null || lineRefMatches(lineRef, k))
                        .limit(maxSize)
                        .collect(Collectors.toSet());

                    List<T> updates = new ArrayList<>();
                    for (SiriObjectStorageKey key : changes) {
                        final T element = cache.get(key);
                        if (element != null) {
                            updates.add(element);
                        }
                    }
                    return updates;
                }
            } finally {
                updateChangeTrackers(lastUpdateRequested,
                    changesMap,
                    requestorId,
                    new HashSet<>(),
                    2,
                    TimeUnit.MINUTES
                );
            }
        }

        return cache
            .entrySet()
            .stream()
            .filter((entry) -> entry.getValue() != null)
            .filter((entry) -> datasetId == null || codespaceMatches(datasetId, entry.getKey()))
            .filter((entry) -> lineRef == null || lineRefMatches(lineRef, entry.getKey()))
            .limit(maxSize)
            .map(Map.Entry::getValue)
            .collect(Collectors.toList());
    }

    void initBufferCommitter(ExtendedHazelcastService hazelcastService, IMap<String, Instant> lastUpdateRequested, IMap<String, Set<SiriObjectStorageKey>> changesMap, int commitFrequency) {
        this.lastUpdateRequested = lastUpdateRequested;
        this.changesMap = changesMap;

        if (singleThreadScheduledExecutor == null) {
            singleThreadScheduledExecutor = Executors.newSingleThreadScheduledExecutor();

        logger.info("Initializing scheduled change-buffer-updater with commit every {} seconds", commitFrequency);

        singleThreadScheduledExecutor.scheduleWithFixedDelay(this::commitChanges, 0, commitFrequency, TimeUnit.SECONDS);
        }

        hazelcastService.addBeforeShuttingDownHook(() -> {
            while (!dirtyChanges.isEmpty()) {
                logger.info("Shutdown triggered - committing {} changes", dirtyChanges.size());
                commitChanges();
            }
            logger.info("ShutDownHook finished");
        });
    }

    /**
     * Commits local change-buffer to cluster
     */
    void commitChanges() {

        try {
            if (!dirtyChanges.isEmpty()) {

                long t1 = System.currentTimeMillis();

                final Set<SiriObjectStorageKey> bufferedChanges = new HashSet<>(dirtyChanges);
                dirtyChanges.clear();

                changesMap.keySet().forEach(key -> {
                    if (!lastUpdateRequested.containsKey(key)) {
                        changesMap.delete(key);
                    }
                });

                if (!changesMap.isEmpty()) {
                    changesMap.executeOnEntries(new AppendChangesToSetEntryProcessor(bufferedChanges));
                    logger.info("Updating changes for {} requestors ({}), committed {} changes, update took {} ms",
                            changesMap.size(), this.getClass().getSimpleName(), bufferedChanges.size(), (System.currentTimeMillis() - t1));
                }
            } else {
                logger.debug("No changes - ignoring commit ({})", this.getClass().getSimpleName());
            }
        } catch (Exception t) {
            //Catch everything to avoid executor being killed
            logger.info("Exception caught when comitting changes", t);
        }
    }


    /**
     * Adds ids to local change-buffer
     * @param changes
     */
    void markIdsAsUpdated(Set<SiriObjectStorageKey> changes) {
        if (!changes.isEmpty()) {
            dirtyChanges.addAll(changes);
            logger.info("Added {} updates to {} dirty-buffer, now has {} pending updates", changes.size(), this.getClass().getSimpleName(), dirtyChanges.size());
        }
    }

    void markDataReceived(SiriDataType dataType, String datasetId, long totalSize, long updatedSize, long expiredSize, long ignoredSize) {
        prepareMetrics();
        metrics.registerIncomingData(dataType, datasetId, totalSize, updatedSize, expiredSize, ignoredSize);
    }

    void prepareMetrics() {
        if (metrics == null) {
            metrics = ApplicationContextHolder.getContext().getBean(PrometheusMetricsService.class);
        }
    }

    void updateChangeTrackers(IMap<String, Instant> lastUpdateRequested, IMap<String, Set<SiriObjectStorageKey>> changesMap,
                              String key, Set<SiriObjectStorageKey> changes, int trackingPeriodMinutes, TimeUnit timeUnit) {
        final String breadcrumbId = MDC.get("camel.breadcrumbId");

        changeTrackerExecutor.execute(() -> {
            try {
                MDC.put("camel.breadcrumbId", breadcrumbId);

                long t1 = System.currentTimeMillis();

                changesMap.executeOnKey(key, new ReplaceSetEntryProcessor(changes));
                changesMap.setTtl(key, trackingPeriodMinutes, timeUnit);

                lastUpdateRequested.set(key, Instant.now(), trackingPeriodMinutes, timeUnit);

                logger.info("Replacing changes for requestor async {} took {} ms. ({})",
                    key,(System.currentTimeMillis() - t1),this.getClass().getSimpleName());
            } finally {
                MDC.remove("camel.breadcrumbId");
            }
        });
        logger.info("Changetracker-update submitted");
    }

    /**
     * Helper method to retrieve multiple values by ids
     * @param collection
     * @param ids
     * @return
     */
    Collection<T> getValuesByIds(Map<String, T> collection, Set<String> ids) {
        Collection<T> result = new ArrayList<>();
        for (String id : ids) {
            T value = collection.get(id);
            if (value != null) {
                result.add(value);
            }
        }
        return result;
    }

    /**
     * Returns values from provided Map where key starts with the provided datasetId
     * @param collection
     * @param datasetId
     * @return
     */
    Collection<T> getValuesByDatasetId(IMap<SiriObjectStorageKey, T> collection, String datasetId) {

        final Set<SiriObjectStorageKey> codespaceKeys = collection.keySet(createHzCodespacePredicate(datasetId));

        return collection.getAll(codespaceKeys).values();
    }

    Set<SiriObjectStorageKey> filterIdsByDataset(final Set<SiriObjectStorageKey> idSet, List<String> excludedDatasetIds, String datasetId) {

        Set<SiriObjectStorageKey> requestedIds = new HashSet<>();
        if (excludedDatasetIds != null && !excludedDatasetIds.isEmpty()) {

            // Return all IDs except 'excludedIds'

            requestedIds.addAll(idSet.stream()
                                .filter(id -> !excludedDatasetIds.contains(id.getCodespaceId()))
                                .collect(Collectors.toSet()));

        } else if (datasetId != null && !datasetId.isEmpty()){


            // Return all IDs that matched datasetId

            requestedIds.addAll(idSet.stream()
                    .filter(id -> datasetId.equals(id.getCodespaceId()))
                    .collect(Collectors.toSet()));

        } else {
            requestedIds.addAll(idSet);
        }

        return requestedIds;
    }

    abstract void clearAllByDatasetId(String datasetId);

    /**
     * @return Map of dataset sizes
     */
    public Map<String, Integer> getDatasetSize() {
        Map<String, Integer> sizeMap = new HashMap<>();
        long t1 = System.currentTimeMillis();
        getMainMap().keySet().forEach(key -> {
            String datasetId = key.getCodespaceId();
            Integer count = sizeMap.getOrDefault(datasetId, 0);
            sizeMap.put(datasetId, count + 1);
        });
        logger.debug("Calculating data-distribution ({}) took {} ms: {}",
            SIRI_DATA_TYPE, (System.currentTimeMillis() - t1), sizeMap);
        return sizeMap;
    }

    /**
     * @return Map of local dataset sizes
     */
    public Map<String, Integer> getLocalDatasetSize() {
        Map<String, Integer> sizeMap = new HashMap<>();
        long t1 = System.currentTimeMillis();
        getMainMap().localKeySet().forEach(key -> {
            String datasetId = key.getCodespaceId();
            Integer count = sizeMap.getOrDefault(datasetId, 0);
            sizeMap.put(datasetId, count + 1);
        });
        logger.debug("Calculating local data-distribution ({}) took {} ms: {}",
            SIRI_DATA_TYPE, (System.currentTimeMillis() - t1), sizeMap);
        return sizeMap;
    }

    /**
     * @param datasetId The dataset to get size for
     * @return Size of dataset
     */
    public Integer getDatasetSize(String datasetId) {
        return Math.toIntExact(getMainMap().keySet().stream()
                .filter(key -> datasetId.equals(key.getCodespaceId()))
                .count());
    }

    Predicate<SiriObjectStorageKey, T> createHzCodespacePredicate(String datasetId) {
        return entry -> {
            return codespaceMatches(datasetId, entry.getKey());
        };
    }

    Predicate<SiriObjectStorageKey, T> createHzLineRefPredicate(String lineRef) {
        return entry -> {
            return lineRefMatches(lineRef, entry.getKey());
        };
    }

    private static boolean codespaceMatches(String datasetId, SiriObjectStorageKey entry) {
        if (entry.getCodespaceId() != null) {
            final String codespaceId = entry.getCodespaceId();
            return codespaceId.equals(datasetId);
        }
        return false;
    }

    private static boolean lineRefMatches(String lineRef, SiriObjectStorageKey entry) {
        String decodedLine = URLDecoder.decode(lineRef, StandardCharsets.UTF_8);
        if (entry.getLineRef() != null) {
            final String ref = entry.getLineRef();

            return ref.equalsIgnoreCase(decodedLine);
        }
        return false;
    }

    /**
     * Compares object-equality by calculating and comparing MD5-checksum
     * @param existing
     * @param updated
     * @return
     */
    static boolean isEqual(Serializable existing, Serializable updated) {
        try {
            String checksumExisting = getChecksum(existing);
            String checksumUpdated = getChecksum(updated);

            return checksumExisting.equals(checksumUpdated);

        } catch (Exception e) {
            //ignore - data will be updated
        }
        return false;
    }

    static String getChecksum(Serializable object) throws NoSuchAlgorithmException {
        byte[] bytes = SerializationUtils.serialize(object);
        MessageDigest md = MessageDigest.getInstance("MD5");
        return DatatypeConverter.printHexBinary(md.digest(bytes));

    }
}
