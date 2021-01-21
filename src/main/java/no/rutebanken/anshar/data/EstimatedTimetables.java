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
import com.hazelcast.replicatedmap.ReplicatedMap;
import com.hazelcast.query.Predicates;
import no.rutebanken.anshar.config.AnsharConfiguration;
import no.rutebanken.anshar.data.collections.ExtendedHazelcastService;
import no.rutebanken.anshar.routes.siri.helpers.SiriObjectFactory;
import no.rutebanken.anshar.subscription.SiriDataType;
import org.quartz.utils.counter.Counter;
import org.quartz.utils.counter.CounterImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Repository;
import uk.org.siri.siri20.EstimatedCall;
import uk.org.siri.siri20.EstimatedVehicleJourney;
import uk.org.siri.siri20.MessageRefStructure;
import uk.org.siri.siri20.QuayRefStructure;
import uk.org.siri.siri20.RecordedCall;
import uk.org.siri.siri20.Siri;
import uk.org.siri.siri20.StopAssignmentStructure;
import uk.org.siri.siri20.StopPointRef;

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
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static no.rutebanken.anshar.routes.siri.transformer.impl.OutboundIdAdapter.getOriginalId;

@Repository
public class EstimatedTimetables  extends SiriRepository<EstimatedVehicleJourney> {
    private final Logger logger = LoggerFactory.getLogger(EstimatedTimetables.class);

    @Autowired
    private IMap<SiriObjectStorageKey, EstimatedVehicleJourney> timetableDeliveries;

    @Autowired
    @Qualifier("getEtChecksumMap")
    private ReplicatedMap<SiriObjectStorageKey,String> checksumCache;

    @Autowired
    @Qualifier("getIdForPatternChangesMap")
    private ReplicatedMap<SiriObjectStorageKey, String> idForPatternChanges;

    @Autowired
    @Qualifier("getIdStartTimeMap")
    private ReplicatedMap<SiriObjectStorageKey, ZonedDateTime> idStartTimeMap;

    @Autowired
    @Qualifier("getEstimatedTimetableChangesMap")
    private IMap<String, Set<SiriObjectStorageKey>> changesMap;

    @Autowired
    @Qualifier("getLastEtUpdateRequest")
    private IMap<String, Instant> lastUpdateRequested;

    @Autowired
    private AnsharConfiguration configuration;

    @Autowired
    private RequestorRefRepository requestorRefRepository;

    @Autowired
    private SiriObjectFactory siriObjectFactory;

    @Autowired
    ExtendedHazelcastService hazelcastService;

    @PostConstruct
    private void initializeUpdateCommitter() {
        super.initBufferCommitter(hazelcastService, lastUpdateRequested, changesMap, configuration.getChangeBufferCommitFrequency());
    }

    /**
     * @return All ET-elements
     */
    public Collection<EstimatedVehicleJourney> getAll() {
        return timetableDeliveries.values();
    }

    /**
     * @return All updates that are flagged as monitored OR that has cancellations or changes in stop-pattern
     */
    public Collection<EstimatedVehicleJourney> getAllMonitored() {

        long t1 = System.currentTimeMillis();

        com.hazelcast.query.Predicate cancelledPredicate = Predicates.equal("cancellation", "true");
        com.hazelcast.query.Predicate monitoredPredicate = Predicates.equal( "monitored", "true");

        Collection<EstimatedVehicleJourney> monitoredVehicleJourneys = timetableDeliveries.values(Predicates.or(monitoredPredicate, cancelledPredicate));
        logger.info("Got {} monitored journeys in {} ms", monitoredVehicleJourneys.size(), (System.currentTimeMillis()-t1));

        return monitoredVehicleJourneys;
    }

    public int getSize() {
        return timetableDeliveries.keySet().size();
    }


    public Map<String, Integer> getDatasetSize() {
        Map<String, Integer> sizeMap = new HashMap<>();
        long t1 = System.currentTimeMillis();
        timetableDeliveries.keySet().forEach(key -> {
                        String datasetId = key.getCodespaceId();

                        Integer count = sizeMap.getOrDefault(datasetId, 0);
                        sizeMap.put(datasetId, count+1);
                    });
        logger.debug("Calculating data-distribution (ET) took {} ms: {}", (System.currentTimeMillis()-t1), sizeMap);
        return sizeMap;
    }

    public Map<String, Integer> getLocalDatasetSize() {
        Map<String, Integer> sizeMap = new HashMap<>();
        long t1 = System.currentTimeMillis();
        timetableDeliveries.localKeySet().forEach(key -> {
                        String datasetId = key.getCodespaceId();

                        Integer count = sizeMap.getOrDefault(datasetId, 0);
                        sizeMap.put(datasetId, count+1);
                    });
        logger.debug("Calculating local data-distribution (ET) took {} ms: {}", (System.currentTimeMillis()-t1), sizeMap);
        return sizeMap;
    }

    public Integer getDatasetSize(String datasetId) {
        return Math.toIntExact(timetableDeliveries.keySet().stream()
                .filter(key -> datasetId.equals(key.getCodespaceId()))
                .count());
    }

    @Override
    public void clearAllByDatasetId(String datasetId) {

        Set<SiriObjectStorageKey> idsToRemove = timetableDeliveries.keySet(createCodespacePredicate(datasetId));

        logger.warn("Removing all data ({} ids) for {}", idsToRemove.size(), datasetId);

        for (SiriObjectStorageKey id : idsToRemove) {
            timetableDeliveries.delete(id);

            checksumCache.remove(id);
            idStartTimeMap.remove(id);
            idForPatternChanges.remove(id);
        }
    }

    public void clearAll() {
        logger.error("Deleting all data - should only be used in test!!!");
        timetableDeliveries.clear();
        checksumCache.clear();
        idStartTimeMap.clear();
        idForPatternChanges.clear();
        changesMap.clear();
        lastUpdateRequested.clear();
    }

    public Siri createServiceDelivery(final String lineRef) {
        SortedSet<EstimatedVehicleJourney> matchingEstimatedVehicleJourneys = new TreeSet<>((o1, o2) -> {
            ZonedDateTime o1_firstTimestamp = getFirstAimedTime(o1);

            ZonedDateTime o2_firstTimestamp = getFirstAimedTime(o2);

            return o1_firstTimestamp.compareTo(o2_firstTimestamp);
        });

        final Set<SiriObjectStorageKey> lineRefKeys = timetableDeliveries.keySet(createLineRefPredicate(lineRef));

        matchingEstimatedVehicleJourneys.addAll(timetableDeliveries.getAll(lineRefKeys).values());

        return siriObjectFactory.createETServiceDelivery(matchingEstimatedVehicleJourneys);
    }

    public Siri createServiceDelivery(String requestorId, String datasetId, int maxSize) {
        return createServiceDelivery(requestorId, datasetId, maxSize, -1);
    }

    public Siri createServiceDelivery(String requestorId, String datasetId, int maxSize, long previewInterval) {
        return createServiceDelivery(requestorId, datasetId, null, null, maxSize, previewInterval);
    }

    public Siri createServiceDelivery(String requestorId, String datasetId, String clientTrackingName, List<String> excludedDatasetIds, int maxSize, long previewInterval) {

        requestorRefRepository.touchRequestorRef(requestorId, datasetId, clientTrackingName, SiriDataType.ESTIMATED_TIMETABLE);

        int trackingPeriodMinutes = configuration.getTrackingPeriodMinutes();

        boolean isAdHocRequest = false;

        if (requestorId == null) {
            requestorId = UUID.randomUUID().toString();
            trackingPeriodMinutes = configuration.getAdHocTrackingPeriodMinutes();
            isAdHocRequest = true;
        }

        // Get all relevant ids
        Set<SiriObjectStorageKey> allIds = new HashSet<>();
        Set<SiriObjectStorageKey> idSet = changesMap.getOrDefault(requestorId, allIds);

        if (idSet == allIds) {
            idSet.addAll(timetableDeliveries
                    .keySet(entry -> datasetId == null || ((SiriObjectStorageKey) entry.getKey()).getCodespaceId().equals(datasetId))
            );
        }

        //Filter by datasetId
        Set<SiriObjectStorageKey> requestedIds = filterIdsByDataset(idSet, excludedDatasetIds, datasetId);

        final ZonedDateTime previewExpiry = ZonedDateTime.now().plusSeconds(previewInterval / 1000);

        Set<SiriObjectStorageKey> startTimes = new HashSet<>();

        if (previewInterval >= 0) {
            long t1 = System.currentTimeMillis();
            startTimes.addAll(idStartTimeMap
                        .entrySet().stream()
                        .filter(entry -> entry.getValue().isBefore(previewExpiry))
                        .map(Map.Entry::getKey)
                        .collect(Collectors.toSet()));

            logger.info("Found {} ids starting within {} ms in {} ms", startTimes.size(), previewInterval, (System.currentTimeMillis()-t1));
        }

        final AtomicInteger previewIntervalInclusionCounter = new AtomicInteger();
        final AtomicInteger previewIntervalExclusionCounter = new AtomicInteger();
        Predicate<SiriObjectStorageKey> previewIntervalFilter =  id -> {

            if (idForPatternChanges.containsKey(id) || startTimes.contains(id)) {
                // Is valid in requested previewInterval
                previewIntervalInclusionCounter.incrementAndGet();
                return true;
            }

            previewIntervalExclusionCounter.incrementAndGet();
            return false;
        };

        long t1 = System.currentTimeMillis();
        Set<SiriObjectStorageKey> sizeLimitedIds = requestedIds
                .stream()
                .filter(id -> previewInterval < 0 || previewIntervalFilter.test(id))
                .limit(maxSize)
                .collect(Collectors.toSet());

        long t2 = System.currentTimeMillis();
        //Remove collected objects
        sizeLimitedIds.forEach(idSet::remove);

        long t3 = System.currentTimeMillis();

        logger.info("Filter by startTime: {}, limiting size: {} ms", (t2 - t1), (t3 - t2));

        t1 = System.currentTimeMillis();

        Boolean isMoreData = (previewIntervalExclusionCounter.get() + sizeLimitedIds.size()) < requestedIds.size();

        Collection<EstimatedVehicleJourney> values = timetableDeliveries.getAll(sizeLimitedIds).values();
        logger.info("Fetching data: {} ms", (System.currentTimeMillis()-t1));
        t1 = System.currentTimeMillis();

        Siri siri = siriObjectFactory.createETServiceDelivery(values);
        logger.info("Creating SIRI-delivery: {} ms", (System.currentTimeMillis()-t1));

        siri.getServiceDelivery().setMoreData(isMoreData);

        if (isAdHocRequest) {
            logger.info("Returning {}, no requestorRef is set", sizeLimitedIds.size());
        } else {

            MessageRefStructure msgRef = new MessageRefStructure();
            msgRef.setValue(requestorId);
            siri.getServiceDelivery().setRequestMessageRef(msgRef);

            if (idSet.size() > timetableDeliveries.size()) {
                //Remove outdated ids
                idSet.removeIf(id -> !timetableDeliveries.containsKey(id));
            }

            //Update change-tracker
            updateChangeTrackers(lastUpdateRequested, changesMap, requestorId, idSet, trackingPeriodMinutes, TimeUnit.MINUTES);

            logger.info("Returning {}, {} left for requestorRef {}", sizeLimitedIds.size(), idSet.size(), requestorId);
        }

        return siri;
    }

    /**
     * Returns true if EstimatedVehicleJourney has any cancellations or quay-changes
     * @param estimatedVehicleJourney
     * @return
     */
    private boolean hasPatternChanges(EstimatedVehicleJourney estimatedVehicleJourney) {
        if (estimatedVehicleJourney != null) {
            String vehicleRef = null;
            if (estimatedVehicleJourney.getVehicleRef() != null && estimatedVehicleJourney.getVehicleRef().getValue() != null) {
                vehicleRef = estimatedVehicleJourney.getVehicleRef().getValue();
            }
            if (estimatedVehicleJourney.isCancellation() != null && estimatedVehicleJourney.isCancellation()) {
                if (vehicleRef != null) {
                    logger.info("Cancellation:  Operator {}, vehicleRef {}, Cancelled journey", estimatedVehicleJourney.getDataSource(), vehicleRef);
                }
                return true;
            }
            if (estimatedVehicleJourney.isExtraJourney() != null && estimatedVehicleJourney.isExtraJourney()) {
                return true;
            }
            if (estimatedVehicleJourney.getEstimatedCalls() != null && estimatedVehicleJourney.getEstimatedCalls().getEstimatedCalls() != null) {
                List<EstimatedCall> estimatedCalls = estimatedVehicleJourney.getEstimatedCalls().getEstimatedCalls();

                ArrayList<String> cancelledStops = new ArrayList<>();
                ArrayList<String> quayChanges = new ArrayList<>();
                for (EstimatedCall estimatedCall : estimatedCalls) {
                    if (estimatedCall.isCancellation() != null && estimatedCall.isCancellation()) {
                        cancelledStops.add(estimatedCall.getStopPointRef().getValue());
                    }
                    StopAssignmentStructure stopAssignment = estimatedCall.getDepartureStopAssignment();
                    if (stopAssignment == null) {
                        stopAssignment = estimatedCall.getArrivalStopAssignment();
                    }
                    if (stopAssignment != null) {
                        QuayRefStructure aimedQuayRef = stopAssignment.getAimedQuayRef();
                        QuayRefStructure expectedQuayRef = stopAssignment.getExpectedQuayRef();
                        if (aimedQuayRef != null && expectedQuayRef != null) {
                            if (!aimedQuayRef.getValue().equals(expectedQuayRef.getValue())) {
                                quayChanges.add(aimedQuayRef.getValue() + " => " + expectedQuayRef.getValue());
                            }
                        }
                    }
                }
                boolean hasCancelledStops = !cancelledStops.isEmpty();
                if (hasCancelledStops && vehicleRef != null) {
                    logger.info("Cancellation:  Operator {}, vehicleRef {}, stopPointRefs {}", estimatedVehicleJourney.getDataSource(), vehicleRef, cancelledStops);
                }

                boolean hasQuayChanges = !quayChanges.isEmpty();
                if (hasQuayChanges) {
                    logger.info("Quay changed:  Operator {}, vehicleRef {}, stopPointRefs {}", estimatedVehicleJourney.getDataSource(), vehicleRef, quayChanges);
                }
                return hasCancelledStops || hasQuayChanges;
            }
        }
        return false;
    }

    public Collection<EstimatedVehicleJourney> getAllUpdates(String requestorId, String datasetId) {
        if (requestorId != null) {

            Set<SiriObjectStorageKey> idSet = changesMap.get(requestorId);
            lastUpdateRequested.put(requestorId, Instant.now(), configuration.getTrackingPeriodMinutes(), TimeUnit.MINUTES);

            if (idSet != null) {
                Set<SiriObjectStorageKey> datasetFilteredIdSet = new HashSet<>();

                if (datasetId != null) {
                    idSet.stream().filter(key -> key.getCodespaceId().equals(datasetId)).forEach(datasetFilteredIdSet::add);
                } else {
                    datasetFilteredIdSet.addAll(idSet);
                }

                Collection<EstimatedVehicleJourney> changes = timetableDeliveries.getAll(datasetFilteredIdSet).values();

                Set<SiriObjectStorageKey> existingSet = changesMap.get(requestorId);
                if (existingSet == null) {
                    existingSet = new HashSet<>();
                }
                //Remove returned ids
                existingSet.removeAll(idSet);

                //Remove outdated ids
                existingSet.removeIf(id -> !timetableDeliveries.containsKey(id));

                updateChangeTrackers(lastUpdateRequested, changesMap, requestorId, existingSet, configuration.getTrackingPeriodMinutes(), TimeUnit.MINUTES);


                logger.info("Returning {} changes to requestorRef {}", changes.size(), requestorId);
                return changes;
            } else {

                logger.info("Returning all to requestorRef {}", requestorId);
                updateChangeTrackers(lastUpdateRequested, changesMap, requestorId, new HashSet<>(), configuration.getTrackingPeriodMinutes(), TimeUnit.MINUTES);
            }
        }

        return getAll(datasetId);
    }

    public Collection<EstimatedVehicleJourney> getAll(String datasetId) {
        if (datasetId == null || datasetId.isEmpty()) {
            return getAll();
        }
        return  getValuesByDatasetId(timetableDeliveries, datasetId);
    }

    private ZonedDateTime getFirstAimedTime(EstimatedVehicleJourney vehicleJourney) {

        if (vehicleJourney.getRecordedCalls() != null && !vehicleJourney.getRecordedCalls().getRecordedCalls().isEmpty()) {
            List<RecordedCall> recordedCalls = vehicleJourney.getRecordedCalls().getRecordedCalls();
            RecordedCall firstRecordedCall = recordedCalls.get(0);

            if (firstRecordedCall.getAimedDepartureTime() != null) {
                return firstRecordedCall.getAimedDepartureTime();
            }

            if (firstRecordedCall.getAimedArrivalTime() != null) {
                return firstRecordedCall.getAimedArrivalTime();
            }
        }

        if (vehicleJourney.getEstimatedCalls() != null && !vehicleJourney.getEstimatedCalls().getEstimatedCalls().isEmpty()) {
            List<EstimatedCall> estimatedCalls = vehicleJourney.getEstimatedCalls().getEstimatedCalls();
            EstimatedCall firstEstimatedCall = estimatedCalls.get(0);

            if (firstEstimatedCall.getAimedDepartureTime() != null) {
                return firstEstimatedCall.getAimedDepartureTime();
            }
            if (firstEstimatedCall.getAimedArrivalTime() != null) {
                return firstEstimatedCall.getAimedArrivalTime();
            }
        }

        final SiriObjectStorageKey key = createKey(vehicleJourney.getDataSource(), vehicleJourney);
        logger.warn("Unable to find aimed time for VehicleJourney with key {}, returning 'now'", key);

        return ZonedDateTime.now();
    }

    public long getExpiration(EstimatedVehicleJourney vehicleJourney) {
        ZonedDateTime expiryTimestamp = getLatestArrivalTime(vehicleJourney);

        if (expiryTimestamp != null) {
            return ZonedDateTime.now().until(expiryTimestamp.plus(configuration.getEtGraceperiodMinutes(), ChronoUnit.MINUTES), ChronoUnit.MILLIS);
        } else {
            return -1;
        }
    }

    public static ZonedDateTime getLatestArrivalTime(EstimatedVehicleJourney vehicleJourney) {
        ZonedDateTime expiryTimestamp = null;
        if (vehicleJourney != null) {
            if (vehicleJourney.getRecordedCalls() != null && !vehicleJourney.getRecordedCalls().getRecordedCalls().isEmpty()) {
                List<RecordedCall> recordedCalls = vehicleJourney.getRecordedCalls().getRecordedCalls();
                RecordedCall lastRecordedCall = recordedCalls.get(recordedCalls.size() - 1);

                if (lastRecordedCall.getAimedArrivalTime() != null) {
                    expiryTimestamp = lastRecordedCall.getAimedArrivalTime();
                }
                if (lastRecordedCall.getAimedDepartureTime() != null) {
                    expiryTimestamp = lastRecordedCall.getAimedDepartureTime();
                }
                if (lastRecordedCall.getExpectedArrivalTime() != null) {
                    expiryTimestamp = lastRecordedCall.getExpectedArrivalTime();
                }
                if (lastRecordedCall.getExpectedDepartureTime() != null) {
                    expiryTimestamp = lastRecordedCall.getExpectedDepartureTime();
                }

            }
            if (vehicleJourney.getEstimatedCalls() != null && !vehicleJourney.getEstimatedCalls().getEstimatedCalls().isEmpty()) {
                List<EstimatedCall> estimatedCalls = vehicleJourney.getEstimatedCalls().getEstimatedCalls();
                EstimatedCall lastEstimatedCall = estimatedCalls.get(estimatedCalls.size() - 1);

                if (lastEstimatedCall.getAimedArrivalTime() != null) {
                    expiryTimestamp = lastEstimatedCall.getAimedArrivalTime();
                }
                if (lastEstimatedCall.getAimedDepartureTime() != null) {
                    expiryTimestamp = lastEstimatedCall.getAimedDepartureTime();
                }
                if (lastEstimatedCall.getExpectedArrivalTime() != null) {
                    expiryTimestamp = lastEstimatedCall.getExpectedArrivalTime();
                }
                if (lastEstimatedCall.getExpectedDepartureTime() != null) {
                    expiryTimestamp = lastEstimatedCall.getExpectedDepartureTime();
                }
            }
        }
        return expiryTimestamp;
    }

    public Collection<EstimatedVehicleJourney> addAll(String datasetId, List<EstimatedVehicleJourney> etList) {

        Set<SiriObjectStorageKey> changes = new HashSet<>();
        Set<EstimatedVehicleJourney> addedData = new HashSet<>();

        Counter outdatedCounter = new CounterImpl(0);
        Counter notUpdatedCounter = new CounterImpl(0);
        List<String> unchangedIds = new ArrayList<>();
        etList.forEach(et -> {
            SiriObjectStorageKey key = createKey(datasetId, et);

            String currentChecksum = null;
            ZonedDateTime recordedAtTime = et.getRecordedAtTime();
            try {
                // Calculate checksum without "RecordedTime" - thus ignoring "fake" updates
                et.setRecordedAtTime(null);
                currentChecksum = getChecksum(et);
            } catch (Exception e) {
                //Ignore - data will be updated
            } finally {
                //Set original RecordedTime back
                et.setRecordedAtTime(recordedAtTime);
            }

            String existingChecksum = checksumCache.get(key);
            boolean updated;
            if (existingChecksum != null && timetableDeliveries.containsKey(key)) {
                //Exists - compare values
                updated =  !(currentChecksum.equals(existingChecksum));
                if (updated && et.isMonitored() == null) {
                    et.setMonitored(true);
                }
            } else {
                //Does not exist
                updated = true;
            }

            boolean keep = false;

            EstimatedVehicleJourney existing = null;
            if (updated) {

                existing = timetableDeliveries.get(key);

                if (existing != null &&
                        (et.getRecordedAtTime() != null && existing.getRecordedAtTime() != null)) {

                    if (et.getRecordedAtTime().isAfter(existing.getRecordedAtTime()) || et.getRecordedAtTime().equals(existing.getRecordedAtTime()) ) {
                        keep = true;
                    } else {
                        logger.info("Newer data has already been processed - ignoring ET-element");
                    }
                } else {
                    keep = true;
                }

            } else {
                notUpdatedCounter.increment();
                if (datasetId.equals("KOL")) {
                    try {
                        unchangedIds.add(et.getFramedVehicleJourneyRef().getDatedVehicleJourneyRef());
                    } catch (Throwable t) {
                        logger.info("Could not get DatedVehicleJourneyRef.", t);
                    }
                }
            }
            if (keep) {

                long expiration = getExpiration(et);
                if (expiration > 0) {

                    if (hasPatternChanges(et)) {
                        // Keep track of all valid ET with pattern-changes
                        idForPatternChanges.put(key, key.getKey(), expiration, TimeUnit.MILLISECONDS);
                        if (et.isMonitored() == null) {
                            et.setMonitored(true);
                        }
                    }

                    changes.add(key);
                    addedData.add(et);
                    timetableDeliveries.set(key, et, expiration, TimeUnit.MILLISECONDS);
                    checksumCache.put(key, currentChecksum, expiration, TimeUnit.MILLISECONDS);

                    idStartTimeMap.put(key, getFirstAimedTime(et), expiration, TimeUnit.MILLISECONDS);
                } else {
                    outdatedCounter.increment();
                }

            }
        });

        logger.info("Updated {} (of {}), {} outdated, {} without changes", changes.size(), etList.size(), outdatedCounter.getValue(), notUpdatedCounter.getValue());


        if (datasetId.equals("KOL")) {
            logger.info("Unchanged ids: {}", unchangedIds);
        }

        markDataReceived(SiriDataType.ESTIMATED_TIMETABLE, datasetId, etList.size(), changes.size(), outdatedCounter.getValue(), notUpdatedCounter.getValue());

        markIdsAsUpdated(changes);

        return addedData;
    }

    public EstimatedVehicleJourney add(String datasetId, EstimatedVehicleJourney delivery) {
        if (delivery == null) {return null;}

        List<EstimatedVehicleJourney> deliveries = new ArrayList<>();
        deliveries.add(delivery);
        addAll(datasetId, deliveries);
        return timetableDeliveries.get(createKey(datasetId, delivery));
    }

    private static SiriObjectStorageKey createKey(String datasetId, EstimatedVehicleJourney element) {

        StringBuilder key = new StringBuilder();
        if (element.getFramedVehicleJourneyRef() != null) {
            String dataFrameRef = element.getFramedVehicleJourneyRef().getDataFrameRef() != null ? element.getFramedVehicleJourneyRef().getDataFrameRef().getValue():"null";

            key.append(dataFrameRef)
                    .append(":")
                    .append(element.getFramedVehicleJourneyRef().getDatedVehicleJourneyRef());
        } else if (element.isExtraJourney() != null && element.getEstimatedVehicleJourneyCode() != null) {

            key.append(datasetId).append(":ExtraJourney:")
                    .append(element.isExtraJourney())
                    .append(":")
                    .append(element.getEstimatedVehicleJourneyCode());
        } else {
            String lastStopId = null;
            if (element.getEstimatedCalls() != null && element.getEstimatedCalls().getEstimatedCalls() != null && !element.getEstimatedCalls().getEstimatedCalls().isEmpty()) {
                final List<EstimatedCall> estimatedCalls = element.getEstimatedCalls().getEstimatedCalls();
                if (estimatedCalls.get(estimatedCalls.size()-1) != null) {
                    final StopPointRef stopPointRef = estimatedCalls.get(estimatedCalls.size()-1).getStopPointRef();
                    if (stopPointRef != null) {
                        lastStopId = getOriginalId(stopPointRef.getValue());
                    }
                }
            }
            key.append((element.getOperatorRef() != null ? element.getOperatorRef().getValue() : "null"))
                    .append(":")
                    .append((element.getVehicleRef() != null ? element.getVehicleRef().getValue() : "null"))
                    .append(":")
                    .append((element.getDirectionRef() != null ? element.getDirectionRef().getValue() : "null"))
                    .append(":")
                    .append(element.getDatedVehicleJourneyRef() != null ? element.getDatedVehicleJourneyRef().getValue() : null)
                    .append(":")
                    .append(lastStopId)
            ;
        }

        String line = null;
        if (element.getLineRef() != null) {
            line = element.getLineRef().getValue();
        }
        return new SiriObjectStorageKey(datasetId, line, key.toString());
    }
}
