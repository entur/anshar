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
import no.rutebanken.anshar.data.collections.ExtendedHazelcastService;
import no.rutebanken.anshar.metrics.PrometheusMetricsService;
import no.rutebanken.anshar.routes.siri.transformer.ApplicationContextHolder;
import no.rutebanken.anshar.subscription.SiriDataType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.SerializationUtils;

import javax.xml.bind.DatatypeConverter;
import java.io.Serializable;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static no.rutebanken.anshar.routes.siri.transformer.SiriValueTransformer.SEPARATOR;

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

    private final Logger logger = LoggerFactory.getLogger(SiriRepository.class);

    protected PrometheusMetricsService metrics;

    final Set<SiriObjectStorageKey> dirtyChanges = Collections.synchronizedSet(new HashSet<>());

    private ScheduledExecutorService singleThreadScheduledExecutor;

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
        return getAllCachedUpdates(requestorId, datasetId, clientTrackingName, null);
    }
    public Collection<T> getAllCachedUpdates(
            String requestorId, String datasetId, String clientTrackingName, Integer maxSize
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
                        .filter((k) -> datasetId == null || k.getCodespaceId().equals(datasetId))
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
            .filter((entry) -> datasetId == null || entry.getKey().getCodespaceId().equals(datasetId))
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
                commitChanges();
            }
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

        ExecutorService executorService = Executors.newSingleThreadExecutor();
        executorService.execute(() -> {
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

        final Set<SiriObjectStorageKey> codespaceKeys = collection.keySet(createCodespacePredicate(datasetId));

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


    Predicate<SiriObjectStorageKey, T> createCodespacePredicate(String datasetId) {
        return entry -> {
            if (entry.getKey().getCodespaceId() != null) {
                final String codespaceId = entry.getKey().getCodespaceId();
                return codespaceId.equals(datasetId);
            }
            return false;
        };
    }

    Predicate<SiriObjectStorageKey, T> createLineRefPredicate(String lineRef) {
        return entry -> {
            String decodedLine = URLDecoder.decode(lineRef, StandardCharsets.UTF_8);
            if (entry.getKey().getLineRef() != null) {
                final String ref = entry.getKey().getLineRef();

                return ref.startsWith(decodedLine + SEPARATOR) ||
                        ref.endsWith(SEPARATOR + decodedLine) ||
                        ref.equalsIgnoreCase(decodedLine);
            }
            return false;
        };
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
