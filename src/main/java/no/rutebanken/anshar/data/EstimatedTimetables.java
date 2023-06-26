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
import com.hazelcast.query.Predicates;
import no.rutebanken.anshar.config.AnsharConfiguration;
import no.rutebanken.anshar.data.collections.ExtendedHazelcastService;
import no.rutebanken.anshar.data.util.TimingTracer;
import no.rutebanken.anshar.metrics.SiriContent;
import no.rutebanken.anshar.routes.siri.helpers.SiriObjectFactory;
import no.rutebanken.anshar.subscription.SiriDataType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import uk.org.siri.siri21.EstimatedCall;
import uk.org.siri.siri21.EstimatedVehicleJourney;
import uk.org.siri.siri21.MessageRefStructure;
import uk.org.siri.siri21.QuayRefStructure;
import uk.org.siri.siri21.RecordedCall;
import uk.org.siri.siri21.Siri;
import uk.org.siri.siri21.StopAssignmentStructure;
import uk.org.siri.siri21.StopPointRefStructure;

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

import static no.rutebanken.anshar.routes.siri.transformer.MappingNames.OVERRIDE_MONITORED_FALSE;
import static no.rutebanken.anshar.routes.siri.transformer.MappingNames.OVERRIDE_MONITORED_NO_LONGER_TRUE;
import static no.rutebanken.anshar.routes.siri.transformer.impl.OutboundIdAdapter.getMappedId;
import static no.rutebanken.anshar.routes.siri.transformer.impl.OutboundIdAdapter.getOriginalId;

@Component
public class EstimatedTimetables  extends SiriRepository<EstimatedVehicleJourney> {
    private final Logger logger = LoggerFactory.getLogger(EstimatedTimetables.class);

    private static final long ONE_WEEK_IN_MILLIS = 60 * 60 * 24 * 7 * 1000;

    @Autowired
    private IMap<SiriObjectStorageKey, EstimatedVehicleJourney> timetableDeliveries;

    @Autowired
    @Qualifier("getEtChecksumMap")
    private IMap<SiriObjectStorageKey,String> checksumCache;

    @Autowired
    @Qualifier("getIdForPatternChangesMap")
    private IMap<SiriObjectStorageKey, String> idForPatternChanges;

    @Autowired
    @Qualifier("getIdStartTimeMap")
    private IMap<SiriObjectStorageKey, ZonedDateTime> idStartTimeMap;

    @Autowired
    @Qualifier("getEstimatedTimetableChangesMap")
    private IMap<String, Set<SiriObjectStorageKey>> changesMap;

    @Autowired
    @Qualifier("getLastEtUpdateRequest")
    private IMap<String, Instant> lastUpdateRequested;

    @Autowired
    private AnsharConfiguration configuration;

    @Autowired
    private SiriObjectFactory siriObjectFactory;

    @Autowired
    ExtendedHazelcastService hazelcastService;

    private long hardLimitFutureUpdates = Integer.MAX_VALUE;

    protected EstimatedTimetables() {
        super(SiriDataType.ESTIMATED_TIMETABLE);
    }

    @PostConstruct
    private void initConfig() {
        if (configuration.hardLimitForFutureEtUpdates() != null) {
            hardLimitFutureUpdates = configuration.hardLimitForFutureEtUpdates().toMillis();
        }
    }

    @PostConstruct
    private void initializeUpdateCommitter() {
        super.initBufferCommitter(hazelcastService, lastUpdateRequested, changesMap, configuration.getChangeBufferCommitFrequency());
        enableCache(timetableDeliveries,
            // Only cache monitored/cancelled/extra trips
            value -> (Boolean.TRUE.equals(value.isMonitored()) |
                Boolean.TRUE.equals(value.isCancellation()) |
                Boolean.TRUE.equals(value.isExtraJourney()))
        );
        linkEntriesTtl(timetableDeliveries, changesMap, checksumCache, idStartTimeMap);
    }

    /**
     * @return All ET-elements
     */
    public Collection<EstimatedVehicleJourney> getAll() {
        return timetableDeliveries.values();
    }

    public Map<SiriObjectStorageKey, EstimatedVehicleJourney> getAllAsMap() {
        return timetableDeliveries;
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

        Set<SiriObjectStorageKey> idsToRemove = timetableDeliveries.keySet(createHzCodespacePredicate(datasetId));

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
        cache.clear();
    }

    public Siri createServiceDelivery(final String lineRef) {
        SortedSet<EstimatedVehicleJourney> matchingEstimatedVehicleJourneys = new TreeSet<>((o1, o2) -> {
            ZonedDateTime o1_firstTimestamp = getFirstAimedTime(o1);

            ZonedDateTime o2_firstTimestamp = getFirstAimedTime(o2);

            return o1_firstTimestamp.compareTo(o2_firstTimestamp);
        });

        final Set<SiriObjectStorageKey> lineRefKeys = timetableDeliveries.keySet(createHzLineRefPredicate(lineRef));

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

            //Update change-tracker
            updateChangeTrackers(lastUpdateRequested, changesMap, requestorId, idSet, trackingPeriodMinutes, TimeUnit.MINUTES);

            logger.info("Returning {}, {} left for requestorRef {}", sizeLimitedIds.size(), idSet.size(), requestorId);
        }

        return siri;
    }
    private void resolveContentMetrics(EstimatedVehicleJourney estimatedVehicleJourney, long expiration) {

//        prepareMetrics();

        if (estimatedVehicleJourney != null) {
            // Not separating on SJ-id for now
            String serviceJourneyId = null;//resolveServiceJourneyId(estimatedVehicleJourney);
            String dataSource = getMappedId(estimatedVehicleJourney.getDataSource());

            if (estimatedVehicleJourney.isCancellation() != null && estimatedVehicleJourney.isCancellation()) {
                metrics.registerSiriContent(SiriDataType.ESTIMATED_TIMETABLE, dataSource, serviceJourneyId, SiriContent.TRIP_CANCELLATION);
            }
            if (estimatedVehicleJourney.isExtraJourney() != null && estimatedVehicleJourney.isExtraJourney()) {
                metrics.registerSiriContent(SiriDataType.ESTIMATED_TIMETABLE, dataSource, serviceJourneyId, SiriContent.EXTRA_JOURNEY);
            }

            if (estimatedVehicleJourney.getOccupancy() != null) {
                metrics.registerSiriContent(SiriDataType.ESTIMATED_TIMETABLE, dataSource, serviceJourneyId, SiriContent.OCCUPANCY_TRIP);
            }

            if (estimatedVehicleJourney.getRecordedCalls() != null && estimatedVehicleJourney.getRecordedCalls().getRecordedCalls() != null) {
                List<RecordedCall> recordedCalls = estimatedVehicleJourney.getRecordedCalls().getRecordedCalls();

                for (RecordedCall recordedCall : recordedCalls) {
                    if (recordedCall.isCancellation() != null && recordedCall.isCancellation()) {
                        metrics.registerSiriContent(SiriDataType.ESTIMATED_TIMETABLE, dataSource, serviceJourneyId, SiriContent.STOP_CANCELLATION);
                    }

                    if (recordedCall.getOccupancy() != null) {
                        metrics.registerSiriContent(SiriDataType.ESTIMATED_TIMETABLE, dataSource, serviceJourneyId, SiriContent.OCCUPANCY_STOP);
                    }

                    if (recordedCall.getDestinationDisplaies() != null && !recordedCall.getDestinationDisplaies().isEmpty()) {
                        metrics.registerSiriContent(SiriDataType.ESTIMATED_TIMETABLE, dataSource, serviceJourneyId, SiriContent.DESTINATION_DISPLAY);
                    }

                    StopAssignmentStructure stopAssignment = null;
                    if (!recordedCall.getDepartureStopAssignments().isEmpty()) {
                        stopAssignment = recordedCall.getDepartureStopAssignments().get(0);
                    } else if (!recordedCall.getArrivalStopAssignments().isEmpty()) {
                        stopAssignment = recordedCall.getArrivalStopAssignments().get(0);
                    }
                    if (stopAssignment != null) {
                        QuayRefStructure aimedQuayRef = stopAssignment.getAimedQuayRef();
                        QuayRefStructure expectedQuayRef = stopAssignment.getExpectedQuayRef();
                        if (aimedQuayRef != null && expectedQuayRef != null) {
                            if (!aimedQuayRef.getValue().equals(expectedQuayRef.getValue())) {
                                metrics.registerSiriContent(SiriDataType.ESTIMATED_TIMETABLE, getMappedId(estimatedVehicleJourney.getDataSource()), serviceJourneyId, SiriContent.QUAY_CHANGED);
                            }
                        }
                    }
                }
            }

            if (estimatedVehicleJourney.getEstimatedCalls() != null && estimatedVehicleJourney.getEstimatedCalls().getEstimatedCalls() != null) {
                List<EstimatedCall> estimatedCalls = estimatedVehicleJourney.getEstimatedCalls().getEstimatedCalls();

                for (EstimatedCall estimatedCall : estimatedCalls) {
                    if (estimatedCall.isCancellation() != null && estimatedCall.isCancellation()) {
                        metrics.registerSiriContent(SiriDataType.ESTIMATED_TIMETABLE, dataSource, serviceJourneyId, SiriContent.STOP_CANCELLATION);
                    }

                    if (estimatedCall.getOccupancy() != null) {
                        metrics.registerSiriContent(SiriDataType.ESTIMATED_TIMETABLE, dataSource, serviceJourneyId, SiriContent.OCCUPANCY_STOP);
                    }

                    if (estimatedCall.getDestinationDisplaies() != null && !estimatedCall.getDestinationDisplaies().isEmpty()) {
                        metrics.registerSiriContent(SiriDataType.ESTIMATED_TIMETABLE, dataSource, serviceJourneyId, SiriContent.DESTINATION_DISPLAY);
                    }

                    StopAssignmentStructure stopAssignment = null;
                    if (!estimatedCall.getDepartureStopAssignments().isEmpty()) {
                        stopAssignment = estimatedCall.getDepartureStopAssignments().get(0);
                    } else if (!estimatedCall.getArrivalStopAssignments().isEmpty()) {
                        stopAssignment = estimatedCall.getArrivalStopAssignments().get(0);
                    }

                    if (stopAssignment != null) {
                        QuayRefStructure aimedQuayRef = stopAssignment.getAimedQuayRef();
                        QuayRefStructure expectedQuayRef = stopAssignment.getExpectedQuayRef();
                        if (aimedQuayRef != null && expectedQuayRef != null) {
                            if (!aimedQuayRef.getValue().equals(expectedQuayRef.getValue())) {
                                metrics.registerSiriContent(SiriDataType.ESTIMATED_TIMETABLE, dataSource, serviceJourneyId, SiriContent.QUAY_CHANGED);
                            }
                        }
                    }
                }
            }

            if (expiration > ONE_WEEK_IN_MILLIS) {
                metrics.registerSiriContent(SiriDataType.ESTIMATED_TIMETABLE, dataSource, null, SiriContent.TOO_FAR_AHEAD);
            }
        }
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
            String dataSource = getMappedId(estimatedVehicleJourney.getDataSource());
            if (estimatedVehicleJourney.isCancellation() != null && estimatedVehicleJourney.isCancellation()) {
                if (vehicleRef != null) {
                    logger.info("Cancellation:  Operator {}, vehicleRef {}, Cancelled journey", dataSource, vehicleRef);
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

                    List<StopAssignmentStructure> stopAssignments = estimatedCall.getDepartureStopAssignments();
                    if (estimatedCall.getDepartureStopAssignments().isEmpty()) {
                        stopAssignments = estimatedCall.getArrivalStopAssignments();
                    }


                    if (!stopAssignments.isEmpty()) {
                        StopAssignmentStructure stopAssignment = stopAssignments.get(0);
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
                    logger.info("Cancellation:  Operator {}, vehicleRef {}, stopPointRefs {}", dataSource, vehicleRef, cancelledStops);
                }

                boolean hasQuayChanges = !quayChanges.isEmpty();
                if (hasQuayChanges) {
                    logger.info("Quay changed:  Operator {}, vehicleRef {}, stopPointRefs {}", dataSource, vehicleRef, quayChanges);
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

        final SiriObjectStorageKey key = createKey(getMappedId(vehicleJourney.getDataSource()), vehicleJourney);
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
                if (lastRecordedCall.getActualArrivalTime() != null) {
                    expiryTimestamp = lastRecordedCall.getActualArrivalTime();
                }
                if (lastRecordedCall.getActualDepartureTime() != null) {
                    expiryTimestamp = lastRecordedCall.getActualDepartureTime();
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
        prepareMetrics();
        Map<SiriObjectStorageKey, EstimatedVehicleJourney> changes = new HashMap();

        Map<SiriObjectStorageKey, String> checksumCacheTmp = new HashMap<>();
        Map<SiriObjectStorageKey, ZonedDateTime> idStartTimeMapTmp = new HashMap<>();
        Map<SiriObjectStorageKey, Long> expirationMap = new HashMap<>();

        AtomicInteger outdatedCounter = new AtomicInteger(0);
        AtomicInteger tooFarAheadCounter = new AtomicInteger(0);
        AtomicInteger notUpdatedCounter = new AtomicInteger(0);
        etList.forEach(et -> {
            TimingTracer timingTracer = new TimingTracer("single-et");
            SiriObjectStorageKey key = createKey(datasetId, et);

            timingTracer.mark("createKey");

            String currentChecksum = null;

            // Using "now" as default recordedAtTime
            ZonedDateTime recordedAtTime = et.getRecordedAtTime() != null ? et.getRecordedAtTime(): ZonedDateTime.now();
            try {
                // Calculate checksum without "RecordedTime" - thus ignoring "fake" updates
                et.setRecordedAtTime(null);
                currentChecksum = getChecksum(et);
                timingTracer.mark("getChecksum");
            } catch (Exception e) {
                //Ignore - data will be updated
            } finally {
                //Set original RecordedTime back
                et.setRecordedAtTime(recordedAtTime);
            }

            String existingChecksum = checksumCache.get(key);
            timingTracer.mark("checksumCache.get");
            boolean updated;
//            if (existingChecksum != null && timetableDeliveries.containsKey(key)) {
            if (existingChecksum != null) {
                //Exists - compare values
                updated =  !(currentChecksum.equals(existingChecksum));
                if (updated && et.isMonitored() == null) {
                    et.setMonitored(true);
                }
            } else {
                //Does not exist
                updated = true;
            }
            timingTracer.mark("compareChecksum");

            boolean keep = false;

            EstimatedVehicleJourney existing = null;
            if (updated) {

                existing = timetableDeliveries.get(key);

                timingTracer.mark("getExisting");

                if (existing != null &&
                        (et.getRecordedAtTime() != null && existing.getRecordedAtTime() != null)) {

                    if (et.getRecordedAtTime().isAfter(existing.getRecordedAtTime()) || et.getRecordedAtTime().equals(existing.getRecordedAtTime()) ) {
                        keep = true;
                    } else {
                        logger.info("Newer data has already been processed - ignoring ET-element");
                    }
                    timingTracer.mark("compareRecordedAtTime");
                } else {
                    keep = true;
                }

            } else {
                notUpdatedCounter.incrementAndGet();
            }

            long expiration = getExpiration(et);
            timingTracer.mark("getExpiration");

            if (expiration > hardLimitFutureUpdates) {
                metrics.registerSiriContent(SiriDataType.ESTIMATED_TIMETABLE, datasetId, null, SiriContent.TOO_FAR_AHEAD);
                tooFarAheadCounter.incrementAndGet();
                keep = false;
            }

            if (keep) {

                if (expiration > 0) {

                    resolveContentMetrics(et, expiration);
                    timingTracer.mark("resolveContentMetrics");

                    boolean hasPatternChanges = hasPatternChanges(et);
                    timingTracer.mark("hasPatternChanges");
                    if (hasPatternChanges) {

                        // Keep track of all valid ET with pattern-changes
                        idForPatternChanges.put(key, key.getKey(), expiration, TimeUnit.MILLISECONDS);
                        timingTracer.mark("idForPatternChanges.put");

                        if (et.isCancellation() == null || !et.isCancellation()) {
                            if (et.isMonitored() != null && !et.isMonitored()) {
                                metrics.registerDataMapping(SiriDataType.ESTIMATED_TIMETABLE, datasetId, OVERRIDE_MONITORED_FALSE, 1);
                            }
                            et.setMonitored(true);
                        }
                    }

                    if (existing != null &&
                            (
                                et.isMonitored() != null && !et.isMonitored() &&
                                    existing.isMonitored() != null && existing.isMonitored()
                            )
                    ) {
                        //Previously had monitored=true - keep monitored state to keep
                        metrics.registerDataMapping(SiriDataType.ESTIMATED_TIMETABLE, datasetId, OVERRIDE_MONITORED_NO_LONGER_TRUE, 1);
                        et.setMonitored(true);
                    }

                    changes.put(key, et);
                    timingTracer.mark("changes.put");

                    checksumCacheTmp.put(key, currentChecksum);
                    timingTracer.mark("checksumCache.put");

                    idStartTimeMapTmp.put(key, getFirstAimedTime(et));
                    timingTracer.mark("idStartTimeMap.put");

                    expirationMap.put(key, expiration);

                } else {
                    outdatedCounter.incrementAndGet();
                    timingTracer.mark("outdatedCounter.increment");
                }

            }
            long elapsed = timingTracer.getTotalTime();
            if (elapsed > 500) {
                logger.info("Adding ET-object with key {} took {} ms: {}", key, elapsed, timingTracer);
            }

        });

        logger.info("Updated {} (of {}), {} outdated, {} without changes, {} too far ahead.", changes.size(), etList.size(), outdatedCounter.get(), notUpdatedCounter.get(), tooFarAheadCounter.get());

        markDataReceived(SiriDataType.ESTIMATED_TIMETABLE, datasetId, etList.size(), changes.size(), outdatedCounter.get(), notUpdatedCounter.get() + tooFarAheadCounter.get());
        TimingTracer timingTracer = new TimingTracer("all-et [" + changes.size() + " changes]");

        // TTL is set in EntryListener when objects are added to main map
        checksumCache.setAll(checksumCacheTmp);
        timingTracer.mark("checksumCache.setAll");

        idStartTimeMap.setAll(idStartTimeMapTmp);
        timingTracer.mark("idStartTimeMap.setAll");

        timetableDeliveries.setAll(changes);
        timingTracer.mark("timetableDeliveries.setAll");

        markIdsAsUpdated(changes.keySet());
        timingTracer.mark("markIdsAsUpdated");
        if (timingTracer.getTotalTime() > 3000) {
            logger.info(timingTracer.toString());
        }
        return changes.values();
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
        } else if (element.getDatedVehicleJourneyRef() != null) {

            key.append(element.getDatedVehicleJourneyRef().getValue());
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
                    final StopPointRefStructure stopPointRef = estimatedCalls.get(estimatedCalls.size()-1).getStopPointRef();
                    if (stopPointRef != null) {
                        lastStopId = getOriginalId(stopPointRef.getValue());
                    }
                }
            }
            if (lastStopId == null) {
                if (element.getRecordedCalls() != null && element.getRecordedCalls().getRecordedCalls() != null && !element.getRecordedCalls().getRecordedCalls().isEmpty()) {
                    final List<RecordedCall> recordedCalls = element.getRecordedCalls().getRecordedCalls();
                    if (recordedCalls.get(recordedCalls.size()-1) != null) {
                        final StopPointRefStructure stopPointRef = recordedCalls.get(recordedCalls.size()-1).getStopPointRef();
                        if (stopPointRef != null) {
                            lastStopId = getOriginalId(stopPointRef.getValue());
                        }
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
