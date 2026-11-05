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
import com.hazelcast.query.Predicates;
import jakarta.annotation.PreDestroy;
import jakarta.xml.bind.DatatypeConverter;
import no.rutebanken.anshar.metrics.PrometheusMetricsService;
import no.rutebanken.anshar.routes.siri.transformer.ApplicationContextHolder;
import no.rutebanken.anshar.subscription.SiriDataType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.SerializationUtils;
import uk.org.siri.siri21.OccupancyEnumeration;
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
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
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

    protected static boolean isMeaningfulOccupancy(OccupancyEnumeration occupancyEnumeration) {
        return Optional.ofNullable(occupancyEnumeration)
                .filter(occ -> occ != OccupancyEnumeration.UNKNOWN && occ != OccupancyEnumeration.UNDEFINED)
                .isPresent();
    }

    abstract Collection<T> getAllUpdates(String requestorId, String datasetId);

    abstract Collection<T> addAll(String datasetId, List<T> ptList);

    abstract T add(String datasetId, T timetableDelivery);

    abstract long getExpiration(T s);

    abstract IMap<SiriObjectStorageKey, T> getMainMap();

    /**
     * @return Predicate matching the objects that are kept in the local cache - null if all objects are kept
     */
    Predicate<SiriObjectStorageKey, T> getCachedDataPredicate() {
        return null;
    }

    private final Logger logger = LoggerFactory.getLogger(SiriRepository.class);

    protected PrometheusMetricsService metrics;

    final Set<SiriObjectStorageKey> dirtyChanges = Collections.synchronizedSet(new HashSet<>());

    private ScheduledExecutorService singleThreadScheduledExecutor;

    private final List<ScheduledExecutorService> cleanupExecutors = new CopyOnWriteArrayList<>();

    // Shared executor for async change tracker updates to prevent thread leak
    // Queue is bounded to limit memory usage - when full, the update is executed by the calling thread
    private static final ExecutorService changeTrackerExecutor = new ThreadPoolExecutor(
        5,
        5,
        0L,
        TimeUnit.MILLISECONDS,
        new LinkedBlockingQueue<>(1000),
        r -> {
            Thread t = new Thread(r, "change-tracker-updater");
            t.setDaemon(true);
            return t;
        },
        new ThreadPoolExecutor.CallerRunsPolicy()
    );

    private static final int CLEANUP_BATCH_SIZE = 500;

    @Autowired
    protected RequestorRefRepository requestorRefRepository;

    Map<SiriObjectStorageKey, T> cache = Maps.newConcurrentMap();

    private volatile boolean cacheEnabled = false;

    private static final long DATASET_SIZE_SNAPSHOT_MILLIS = 60_000;
    private volatile Map<String, Integer> datasetSizeSnapshot;
    private volatile long datasetSizeSnapshotTime;
    private java.util.function.Predicate<T> includeInCachePredicate;

    protected SiriRepository (SiriDataType siriDataType) {
        this.SIRI_DATA_TYPE = siriDataType;
    }

    protected void enableCache(IMap<SiriObjectStorageKey, T> map, boolean keepLocalCopy) {
        enableCache(map, null, keepLocalCopy);
    }

    /**
     * @param keepLocalCopy if false, no local copy of the data is kept - "cached" requests are then served from the main map
     */
    protected void enableCache(IMap<SiriObjectStorageKey, T> map, java.util.function.Predicate<T> includeInCachePredicate, boolean keepLocalCopy) {
        this.includeInCachePredicate = includeInCachePredicate;
        if (!keepLocalCopy) {
            logger.info("Local cache is disabled for {}", SIRI_DATA_TYPE);
            return;
        }
        cacheEnabled = true;

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
            } else {
                // No longer matches - remove previous version
                cache.remove(entryEvent.getKey());
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

    /**
     * Schedules periodic removal of expired objects.
     *
     * @param linkedMaps maps keyed by the same id as the main map (e.g. checksums) - entries are removed together with the expired object
     */
    @SafeVarargs
    final ScheduledExecutorService createCleanupJob(IMap<SiriObjectStorageKey, T> map, IMap<String, Set<SiriObjectStorageKey>> linkedChangeMap, long cleanupInterval, long maxValidityMillis, IMap<SiriObjectStorageKey, ?>... linkedMaps) {

        logger.info("Initializing scheduled cleanup job with interval {} seconds", cleanupInterval);
        ScheduledExecutorService cleanupExecutor = Executors.newSingleThreadScheduledExecutor();
        cleanupExecutor.scheduleWithFixedDelay(() -> removeExpired(map, linkedChangeMap, maxValidityMillis, linkedMaps), cleanupInterval, cleanupInterval, TimeUnit.SECONDS);
        cleanupExecutors.add(cleanupExecutor);
        return cleanupExecutor;
    }

    private void removeExpired(IMap<SiriObjectStorageKey, T> map, IMap<String, Set<SiriObjectStorageKey>> linkedChangeMap, long maxValidityMillis, IMap<SiriObjectStorageKey, ?>[] linkedMaps) {
        try {
            logger.debug("Cleaning up expired objects");
            long t1 = System.currentTimeMillis();
            // Values are fetched in batches to avoid loading all data into memory at once
            // Copied to a HashSet - IMap.keySet() deserializes lazily, and contains() is a linear scan that
            // deserializes every key, which makes removeStaleCacheEntries O(n^2)
            final Set<SiriObjectStorageKey> allKeys = new HashSet<>(map.keySet());
            final Set<SiriObjectStorageKey> expired = new HashSet<>();
            final Set<SiriObjectStorageKey> batch = new HashSet<>();
            for (Iterator<SiriObjectStorageKey> it = allKeys.iterator(); it.hasNext(); ) {
                batch.add(it.next());
                if (batch.size() >= CLEANUP_BATCH_SIZE || !it.hasNext()) {
                    for (Map.Entry<SiriObjectStorageKey, T> entry : map.getAll(batch).entrySet()) {
                        if (isExpired(entry.getValue(), maxValidityMillis)) {
                            expired.add(entry.getKey());
                        }
                    }
                    batch.clear();
                }
            }

            removeStaleCacheEntries(map, allKeys);

            long t2 = System.currentTimeMillis();
            final Set<SiriObjectStorageKey> removed = new HashSet<>();
            if (!expired.isEmpty()) {
                // Expiry is checked again when removing - an object may have been updated since it was found to be expired
                map.executeOnKeys(expired, new RemoveIfExpiredEntryProcessor<T>(SIRI_DATA_TYPE, maxValidityMillis))
                        .forEach((key, isRemoved) -> {
                            if (Boolean.TRUE.equals(isRemoved)) {
                                removed.add(key);
                            }
                        });
                for (SiriObjectStorageKey key : removed) {
                    for (IMap<SiriObjectStorageKey, ?> linkedMap : linkedMaps) {
                        linkedMap.delete(key);
                    }
                }
            }

            long t3 = System.currentTimeMillis();
            if (!removed.isEmpty() && !linkedChangeMap.isEmpty()) {
                linkedChangeMap.executeOnEntries(new RemoveChangesFromSetEntryProcessor(removed));
            }
            long totalCleanupTime = System.currentTimeMillis() - t1;
            if (totalCleanupTime > 1000) {
                logger.info("Cleaning {} expired objects took {} ms, finding {} ms, removing {}, changes {} ms, now have {} objects of type {}",
                        removed.size(), totalCleanupTime, (t2 - t1), (t3 - t2), (System.currentTimeMillis() - t3), map.size(), SIRI_DATA_TYPE);
            }
        } catch (Throwable t) {
            //Catch everything to avoid executor being killed
            logger.info("Exception caught when cleaning up expired data", t);
        }
    }

    boolean isExpired(T value, long maxValidityMillis) {
        // Check if entry is expired
        if (getExpiration(value) < 0) {
            return true;
        }
        // Check if entry is older than maxValidityMillis
        if (maxValidityMillis > 0) {
            if (value instanceof VehicleActivityStructure) {
                ZonedDateTime recordedAtTime = ((VehicleActivityStructure) value).getRecordedAtTime();
                long timeSinceUpdate = Math.abs(ZonedDateTime.now().until(recordedAtTime, ChronoUnit.MILLIS));
                return timeSinceUpdate > maxValidityMillis;
            }
        }
        return false;
    }

    /**
     * Removes cached objects that no longer exist in the main map - e.g. if the remove/expire-event was lost
     */
    private void removeStaleCacheEntries(IMap<SiriObjectStorageKey, T> map, Set<SiriObjectStorageKey> existingKeys) {
        if (!cacheEnabled) {
            return;
        }
        for (SiriObjectStorageKey key : cache.keySet()) {
            if (!existingKeys.contains(key)) {
                T cached = cache.get(key);
                // Verify against main map in case the object was added after the keys were fetched
                if (cached != null && !map.containsKey(key)) {
                    cache.remove(key, cached);
                }
            }
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
    /**
     * How long changes are tracked for requestors of cached data
     */
    private static final int CACHED_UPDATES_TRACKING_MINUTES = 2;

    public Collection<T> getAllCachedUpdates(
            String requestorId, String datasetId, String lineRef, String clientTrackingName, Integer maxSize
    ) {
        return getCachedUpdates(requestorId, datasetId, lineRef, clientTrackingName, maxSize).updates();
    }

    /**
     * Returns cached data matching datasetId and lineRef, limited to maxSize.
     * <p>
     * With a requestorId, the first request returns the current data, and later requests only return what has changed
     * since. Matching objects held back because of maxSize are kept for the requestor, and returned by the next request.
     */
    public CachedUpdates<T> getCachedUpdates(
            String requestorId, String datasetId, String lineRef, String clientTrackingName, Integer maxSize
    ) {
        final int limit = maxSize == null ? Integer.MAX_VALUE : maxSize;

        if (requestorId == null) {
            final List<Map.Entry<SiriObjectStorageKey, T>> snapshot = getCachedSnapshot(datasetId, lineRef);
            final List<T> updates = snapshot.stream()
                .limit(limit)
                .map(Map.Entry::getValue)
                .collect(Collectors.toList());
            return new CachedUpdates<>(updates, snapshot.size() > updates.size());
        }

        requestorRefRepository.touchRequestorRef(requestorId,
            datasetId,
            clientTrackingName,
            SIRI_DATA_TYPE
        );

        try {
            final Set<SiriObjectStorageKey> trackedChanges = changesMap.get(requestorId);

            if (trackedChanges != null) {
                final List<SiriObjectStorageKey> matching = new ArrayList<>();
                final Set<SiriObjectStorageKey> notMatching = new HashSet<>();
                for (SiriObjectStorageKey key : trackedChanges) {
                    if ((datasetId == null || codespaceMatches(datasetId, key)) &&
                        (lineRef == null || lineRefMatches(lineRef, key))) {
                        matching.add(key);
                    } else {
                        notMatching.add(key);
                    }
                }

                final Set<SiriObjectStorageKey> selected = new HashSet<>(matching.subList(0, Math.min(limit, matching.size())));

                // Changes not matching the request are dropped, matching changes held back are kept for the next request
                final Set<SiriObjectStorageKey> claimed = claimChanges(requestorId, selected, notMatching);

                return new CachedUpdates<>(getCachedValues(claimed), matching.size() > selected.size());
            }

            // First request from this requestor - return current data
            startTracking(requestorId, CACHED_UPDATES_TRACKING_MINUTES, TimeUnit.MINUTES);

            final List<Map.Entry<SiriObjectStorageKey, T>> snapshot = getCachedSnapshot(datasetId, lineRef);
            final List<T> updates = new ArrayList<>();
            final Set<SiriObjectStorageKey> snapshotKeys = new HashSet<>();
            final Set<SiriObjectStorageKey> selected = new HashSet<>();
            for (Map.Entry<SiriObjectStorageKey, T> entry : snapshot) {
                snapshotKeys.add(entry.getKey());
                if (updates.size() < limit) {
                    updates.add(entry.getValue());
                    selected.add(entry.getKey());
                }
            }
            takeChanges(requestorId, true, snapshotKeys, selected);

            return new CachedUpdates<>(updates, snapshotKeys.size() > selected.size());

        } finally {
            refreshTracking(requestorId, CACHED_UPDATES_TRACKING_MINUTES, TimeUnit.MINUTES);
        }
    }

    /**
     * @return cached objects for the given keys - keys without a cached object are skipped
     */
    private List<T> getCachedValues(Collection<SiriObjectStorageKey> keys) {
        List<T> updates = new ArrayList<>();
        if (cacheEnabled) {
            for (SiriObjectStorageKey key : keys) {
                final T element = cache.get(key);
                if (element != null) {
                    updates.add(element);
                }
            }
        } else {
            // No local copy - read from main map
            for (T element : getMainMap().getAll(new HashSet<>(keys)).values()) {
                if (includeInCachePredicate == null || includeInCachePredicate.test(element)) {
                    updates.add(element);
                }
            }
        }
        return updates;
    }

    /**
     * @return all cached entries matching datasetId and lineRef
     */
    private List<Map.Entry<SiriObjectStorageKey, T>> getCachedSnapshot(String datasetId, String lineRef) {
        if (!cacheEnabled) {
            // No local copy - read from main map, filtering is done by the cluster-members
            Predicate<SiriObjectStorageKey, T> keyPredicate = getCachedDataPredicate();
            if (datasetId != null) {
                Predicate<SiriObjectStorageKey, T> datasetPredicate = createHzCodespacePredicate(datasetId);
                keyPredicate = keyPredicate == null ? datasetPredicate : Predicates.and(keyPredicate, datasetPredicate);
            }
            if (lineRef != null) {
                Predicate<SiriObjectStorageKey, T> lineRefPredicate = createHzLineRefPredicate(lineRef);
                keyPredicate = keyPredicate == null ? lineRefPredicate : Predicates.and(keyPredicate, lineRefPredicate);
            }
            Set<Map.Entry<SiriObjectStorageKey, T>> entries = keyPredicate == null ? getMainMap().entrySet() : getMainMap().entrySet(keyPredicate);
            return entries
                .stream()
                .filter((entry) -> entry.getValue() != null)
                .filter((entry) -> includeInCachePredicate == null || includeInCachePredicate.test(entry.getValue()))
                .toList();
        }

        return cache
            .entrySet()
            .stream()
            .filter((entry) -> entry.getValue() != null)
            .filter((entry) -> datasetId == null || codespaceMatches(datasetId, entry.getKey()))
            .filter((entry) -> lineRef == null || lineRefMatches(lineRef, entry.getKey()))
            .toList();
    }

    void initBufferCommitter(IMap<String, Instant> lastUpdateRequested, IMap<String, Set<SiriObjectStorageKey>> changesMap, int commitFrequency) {
        this.lastUpdateRequested = lastUpdateRequested;
        this.changesMap = changesMap;

        if (singleThreadScheduledExecutor == null) {
            singleThreadScheduledExecutor = Executors.newSingleThreadScheduledExecutor();

            logger.info("Initializing scheduled change-buffer-updater with commit every {} seconds", commitFrequency);
            singleThreadScheduledExecutor.scheduleWithFixedDelay(this::commitChanges, 0, commitFrequency, TimeUnit.SECONDS);
        }
    }

    @PreDestroy
    void flushOnShutdown() {
        cleanupExecutors.forEach(ScheduledExecutorService::shutdownNow);
        if (singleThreadScheduledExecutor != null) {
            singleThreadScheduledExecutor.shutdown();
        }
        while (!dirtyChanges.isEmpty()) {
            logger.info("Shutdown triggered - committing {} changes", dirtyChanges.size());
            commitChanges();
        }
        logger.info("ShutDownHook finished");
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

    /**
     * Starts tracking changes for a requestor's first request. This is done before current data is read, so changes
     * committed while the request is handled are kept for the next request.
     */
    void startTracking(String requestorId, int trackingPeriod, TimeUnit timeUnit) {
        // Change sets without lastUpdateRequested are removed by commitChanges
        lastUpdateRequested.set(requestorId, Instant.now(), trackingPeriod, timeUnit);
        changesMap.executeOnKey(requestorId, new AppendChangesToSetEntryProcessor(new HashSet<>()));
        changesMap.setTtl(requestorId, trackingPeriod, timeUnit);
    }

    /**
     * Removes keys from the requestor's change set in one atomic operation.
     *
     * @param claimed   keys returned to the requestor
     * @param discarded keys removed without being returned
     * @return the claimed keys that were still in the set - keys taken by a concurrent request are left out
     */
    Set<SiriObjectStorageKey> claimChanges(String requestorId, Set<SiriObjectStorageKey> claimed, Set<SiriObjectStorageKey> discarded) {
        if (claimed.isEmpty() && discarded.isEmpty()) {
            return new HashSet<>();
        }
        Set<SiriObjectStorageKey> result = changesMap.executeOnKey(requestorId,
            new ClaimChangesEntryProcessor(new HashSet<>(claimed), new HashSet<>(discarded)));
        return result != null ? result : new HashSet<>();
    }

    /**
     * Updates the requestor's change set after selecting the keys to return.
     *
     * @param isFirstRequest true if tracking was started by this request - pendingIds are then the current data
     * @param pendingIds     the keys the selection was made from
     * @param selectedIds    the keys selected to be returned
     * @return the keys to return
     */
    Set<SiriObjectStorageKey> takeChanges(String requestorId, boolean isFirstRequest,
                                          Set<SiriObjectStorageKey> pendingIds, Set<SiriObjectStorageKey> selectedIds) {
        if (isFirstRequest) {
            // Keys held back from the current data are returned by the next request
            Set<SiriObjectStorageKey> remaining = new HashSet<>(pendingIds);
            remaining.removeAll(selectedIds);
            if (!remaining.isEmpty()) {
                changesMap.executeOnKey(requestorId, new AppendChangesToSetEntryProcessor(remaining));
            }
            return selectedIds;
        }
        return claimChanges(requestorId, selectedIds, new HashSet<>());
    }

    /**
     * Extends how long changes are tracked for the requestor. Done async to avoid delaying the response.
     */
    void refreshTracking(String requestorId, int trackingPeriod, TimeUnit timeUnit) {
        final String breadcrumbId = MDC.get("camel.breadcrumbId");

        changeTrackerExecutor.execute(() -> {
            // May be executed by the calling thread if the queue is full - its breadcrumb must be kept
            final String previousBreadcrumbId = MDC.get("camel.breadcrumbId");
            try {
                MDC.put("camel.breadcrumbId", breadcrumbId);

                changesMap.setTtl(requestorId, trackingPeriod, timeUnit);
                lastUpdateRequested.set(requestorId, Instant.now(), trackingPeriod, timeUnit);
            } finally {
                if (previousBreadcrumbId != null) {
                    MDC.put("camel.breadcrumbId", previousBreadcrumbId);
                } else {
                    MDC.remove("camel.breadcrumbId");
                }
            }
        });
    }

    void updateChangeTrackers(IMap<String, Instant> lastUpdateRequested, IMap<String, Set<SiriObjectStorageKey>> changesMap,
                              String key, Set<SiriObjectStorageKey> changes, int trackingPeriodMinutes, TimeUnit timeUnit) {
        final String breadcrumbId = MDC.get("camel.breadcrumbId");

        changeTrackerExecutor.execute(() -> {
            // May be executed by the calling thread if the queue is full - its breadcrumb must be kept
            final String previousBreadcrumbId = MDC.get("camel.breadcrumbId");
            try {
                MDC.put("camel.breadcrumbId", breadcrumbId);

                long t1 = System.currentTimeMillis();

                changesMap.executeOnKey(key, new ReplaceSetEntryProcessor(changes));
                changesMap.setTtl(key, trackingPeriodMinutes, timeUnit);

                lastUpdateRequested.set(key, Instant.now(), trackingPeriodMinutes, timeUnit);

                logger.info("Replacing changes for requestor async {} took {} ms. ({})",
                    key,(System.currentTimeMillis() - t1),this.getClass().getSimpleName());
            } finally {
                if (previousBreadcrumbId != null) {
                    MDC.put("camel.breadcrumbId", previousBreadcrumbId);
                } else {
                    MDC.remove("camel.breadcrumbId");
                }
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
     * @return Map of dataset sizes using the local cache (avoids distributed keySet scan)
     */
    public Map<String, Integer> getCachedDatasetSize() {
        if (!cacheEnabled) {
            // No local copy - limit how often the cluster is queried
            Map<String, Integer> sizeMap = datasetSizeSnapshot;
            if (sizeMap == null || System.currentTimeMillis() - datasetSizeSnapshotTime > DATASET_SIZE_SNAPSHOT_MILLIS) {
                // Counting the same objects as the local cache would have held
                final Predicate<SiriObjectStorageKey, T> cachedDataPredicate = getCachedDataPredicate();
                final Map<String, Integer> counts = new HashMap<>();
                (cachedDataPredicate == null ? getMainMap().keySet() : getMainMap().keySet(cachedDataPredicate))
                        .forEach(key -> counts.merge(key.getCodespaceId(), 1, Integer::sum));
                sizeMap = counts;
                datasetSizeSnapshot = sizeMap;
                datasetSizeSnapshotTime = System.currentTimeMillis();
            }
            return new HashMap<>(sizeMap);
        }
        Map<String, Integer> sizeMap = new HashMap<>();
        cache.keySet().forEach(key -> sizeMap.merge(key.getCodespaceId(), 1, Integer::sum));
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
