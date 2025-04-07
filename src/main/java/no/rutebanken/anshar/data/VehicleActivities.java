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
import no.rutebanken.anshar.metrics.SiriContent;
import no.rutebanken.anshar.routes.siri.helpers.SiriObjectFactory;
import no.rutebanken.anshar.subscription.SiriDataType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import uk.org.siri.siri21.AbstractItemStructure;
import uk.org.siri.siri21.CoordinatesStructure;
import uk.org.siri.siri21.LocationStructure;
import uk.org.siri.siri21.MessageRefStructure;
import uk.org.siri.siri21.Siri;
import uk.org.siri.siri21.VehicleActivityStructure;
import uk.org.siri.siri21.VehicleRef;

import javax.annotation.PostConstruct;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
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
import java.util.stream.Collectors;

@Component
public class VehicleActivities extends SiriRepository<VehicleActivityStructure> {
    private final Logger logger = LoggerFactory.getLogger(VehicleActivities.class);

    @Autowired
    private IMap<SiriObjectStorageKey, VehicleActivityStructure> monitoredVehicles;

    @Autowired
    @Qualifier("getVehicleChangesMap")
    private IMap<String, Set<SiriObjectStorageKey>> changesMap;

    @Autowired
    @Qualifier("getVmChecksumMap")
    private IMap<SiriObjectStorageKey,String> checksumCache;

    @Autowired
    @Qualifier("getLastVmUpdateRequest")
    private IMap<String, Instant> lastUpdateRequested;

    @Autowired
    private SiriObjectFactory siriObjectFactory;

    @Autowired
    private AnsharConfiguration configuration;

    @Autowired
    ExtendedHazelcastService hazelcastService;

    @Value("${anshar.vehicle-activities.remove-extensions:false}")
    private boolean REMOVE_EXTENSIONS;

    @Value("${anshar.feature.setUsingAsync:false}")
    private boolean FEATURE_TOGGLE_USE_ASYNC_SET;

    @Value("${anshar.vehicle-activities.max-validity.duration:}")
    private Duration maxValidityDuration;
    private long maxValidityMillis;

    protected VehicleActivities() {
        super(SiriDataType.VEHICLE_MONITORING);
    }

    @PostConstruct
    private void init() {
        if (FEATURE_TOGGLE_USE_ASYNC_SET) {
            logger.info("Using async set for monitored vehicles");
        }
        maxValidityMillis = maxValidityDuration != null ? maxValidityDuration.toMillis() : -1;
    }

    @PostConstruct
    private void initializeUpdateCommitter() {
        super.initBufferCommitter(hazelcastService, lastUpdateRequested, changesMap, configuration.getChangeBufferCommitFrequency());

        enableCache(monitoredVehicles);
        linkEntriesTtl(monitoredVehicles, changesMap);
    }

    /**
     * @return All vehicle activities
     */
    public Collection<VehicleActivityStructure> getAll() {
        return monitoredVehicles.values();
    }

    public Map<SiriObjectStorageKey, VehicleActivityStructure> getAllAsMap() {
        return monitoredVehicles;
    }

    public int getSize() {
        return monitoredVehicles.keySet().size();
    }

    public Map<String, Integer> getDatasetSize() {
        Map<String, Integer> sizeMap = new HashMap<>();
        long t1 = System.currentTimeMillis();
        monitoredVehicles.keySet().forEach(key -> {
            String datasetId = key.getCodespaceId();

            Integer count = sizeMap.getOrDefault(datasetId, 0);
            sizeMap.put(datasetId, count+1);
        });
        logger.debug("Calculating data-distribution (VM) took {} ms: {}", (System.currentTimeMillis()-t1), sizeMap);
        return sizeMap;
    }

    public Map<String, Integer> getLocalDatasetSize() {
        Map<String, Integer> sizeMap = new HashMap<>();
        long t1 = System.currentTimeMillis();
        monitoredVehicles.localKeySet().forEach(key -> {
            String datasetId = key.getCodespaceId();

            Integer count = sizeMap.getOrDefault(datasetId, 0);
            sizeMap.put(datasetId, count+1);
        });
        logger.debug("Calculating data-distribution (VM) took {} ms: {}", (System.currentTimeMillis()-t1), sizeMap);
        return sizeMap;
    }


    public Integer getDatasetSize(String datasetId) {
        return Math.toIntExact(monitoredVehicles.keySet().stream()
                .filter(key -> datasetId.equals(key.getCodespaceId()))
                .count());
    }

    @Override
    public void clearAllByDatasetId(String datasetId) {

        Set<SiriObjectStorageKey> idsToRemove = monitoredVehicles.keySet(createHzCodespacePredicate(datasetId));

        logger.warn("Removing all data ({} ids) for {}", idsToRemove.size(), datasetId);

        for (SiriObjectStorageKey id : idsToRemove) {
            monitoredVehicles.delete(id);
        }
    }

    public void clearAll() {
        logger.error("Deleting all data - should only be used in test!!!");
        monitoredVehicles.clear();
        cache.clear();
    }

    /**
     * @return All vehicle activities that are still valid
     */
    public Collection<VehicleActivityStructure> getAll(String datasetId) {
        if (datasetId == null) {
            return getAll();
        }

        return getValuesByDatasetId(monitoredVehicles, datasetId);
    }

    /**
     * @return All vehicle activities that have been updated since last request from requestor
     */
    public Collection<VehicleActivityStructure> getAllUpdates(String requestorId, String datasetId) {
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

                Collection<VehicleActivityStructure> changes = monitoredVehicles.getAll(datasetFilteredIdSet).values();

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


    public Siri createServiceDelivery(final String lineRef) {
        SortedSet<VehicleActivityStructure> vehicleActivityStructures = new TreeSet<>(Comparator.comparing(AbstractItemStructure::getRecordedAtTime));

        final Set<SiriObjectStorageKey> lineRefKeys = monitoredVehicles.keySet(createHzLineRefPredicate(lineRef));

        vehicleActivityStructures.addAll(monitoredVehicles.getAll(lineRefKeys).values());

        return siriObjectFactory.createVMServiceDelivery(vehicleActivityStructures);
    }


    public Siri createServiceDelivery(String requestorId, String datasetId, String clientName, List<String> excludedDatasetIds, int maxSize) {
        requestorRefRepository.touchRequestorRef(requestorId, datasetId, clientName, SiriDataType.VEHICLE_MONITORING);

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
            idSet.addAll(monitoredVehicles.keySet());
        }

        Set<SiriObjectStorageKey> requestedIds = filterIdsByDataset(idSet, excludedDatasetIds, datasetId);

        Set<SiriObjectStorageKey> sizeLimitedIds = requestedIds.stream().limit(maxSize).collect(Collectors.toSet());

        Boolean isMoreData = sizeLimitedIds.size() < requestedIds.size();

        //Remove collected objects
        sizeLimitedIds.forEach(idSet::remove);

        Collection<VehicleActivityStructure> values = monitoredVehicles.getAll(sizeLimitedIds).values();

        Siri siri = siriObjectFactory.createVMServiceDelivery(values);

        siri.getServiceDelivery().setMoreData(isMoreData);

        if (isAdHocRequest) {
            logger.info("Returning {}, no requestorRef is set", sizeLimitedIds.size());
        } else {


            //Update change-tracker
            updateChangeTrackers(lastUpdateRequested, changesMap, requestorId, idSet, trackingPeriodMinutes, TimeUnit.MINUTES);


            MessageRefStructure msgRef = new MessageRefStructure();
            msgRef.setValue(requestorId);
            siri.getServiceDelivery().setRequestMessageRef(msgRef);

            logger.info("Returning {}, {} left for requestorRef {}", sizeLimitedIds.size(), idSet.size(), requestorId);
        }

        return siri;
    }

    public long getExpiration(VehicleActivityStructure a) {

        ZonedDateTime validUntil = a.getValidUntilTime();
        if (validUntil != null) {

            ZonedDateTime now = ZonedDateTime.now();

            if (validUntil.getYear() > now.getYear()) {
                // Handle cornercase when validity is set too far ahead - e.g. year 9999
                validUntil = validUntil.withYear(now.getYear()+1);
            }

            long expirationMillis = now.until(
                    validUntil.plus(configuration.getVmGraceperiodMinutes(), ChronoUnit.MINUTES),
                    ChronoUnit.MILLIS
            );

            if (maxValidityMillis > 0) {
                // Return calculated expiration only if it is less than maxValidity
                return Math.min(maxValidityMillis, expirationMillis);
            } else {
                return expirationMillis;
            }
        }

        // No to-validity set - ignore as this is a required field
        return -1;
    }

    public Collection<VehicleActivityStructure> addAll(String datasetId, List<VehicleActivityStructure> vmList) {

        Map<SiriObjectStorageKey, VehicleActivityStructure> changes = new HashMap<>();

        AtomicInteger invalidLocationCounter = new AtomicInteger(0);
        AtomicInteger notMeaningfulCounter = new AtomicInteger(0);
        AtomicInteger outdatedCounter = new AtomicInteger(0);
        AtomicInteger notUpdatedCounter = new AtomicInteger(0);
        prepareMetrics();

        vmList.stream()
                .filter(activity -> activity.getMonitoredVehicleJourney() != null)
                .filter(activity -> activity.getMonitoredVehicleJourney().getVehicleRef() != null)
                .filter(activity -> activity.getMonitoredVehicleJourney().getFramedVehicleJourneyRef() == null ||
                        ( activity.getMonitoredVehicleJourney().getFramedVehicleJourneyRef() != null &&
                                activity.getMonitoredVehicleJourney().getFramedVehicleJourneyRef().getDatedVehicleJourneyRef() != null)
                )
                .forEach(activity -> {
                    TimingTracer timingTracer = new TimingTracer("single-vm");
                    SiriObjectStorageKey key = createKey(datasetId, activity.getMonitoredVehicleJourney());
                    timingTracer.mark("createKey");

                    if (REMOVE_EXTENSIONS && activity.getExtensions() != null) {
                        activity.setExtensions(null);
                        metrics.registerSiriContent(SiriDataType.VEHICLE_MONITORING, datasetId, null, SiriContent.EXTENSION_REMOVED);
                        timingTracer.mark("remove.extensions");
                    }

                    String currentChecksum = calculateChecksum(activity);
                    timingTracer.mark("calculateChecksum.updated");

                    VehicleActivityStructure existing = cache.get(key);
                    timingTracer.mark("getExisting");

                    String existingChecksum = calculateChecksum(existing);

                    timingTracer.mark("calculateChecksum.current");

                    if (isUpdated(existingChecksum, currentChecksum)) {

                        boolean keep = (existing == null); //No existing data i.e. keep

                        if (existing != null &&
                                (activity.getRecordedAtTime() != null && existing.getRecordedAtTime() != null)) {
                            //Newer data has already been processed
                            keep = activity.getRecordedAtTime().isAfter(existing.getRecordedAtTime());
                        }

                        long expiration = getExpiration(activity);
                        timingTracer.mark("getExpiration");

                        resolveContentMetrics(activity, expiration);
                        if (expiration > 0 && keep) {
                            changes.put(key, activity);
                        } else {
                            outdatedCounter.incrementAndGet();
                        }

                        if (!isLocationValid(activity)) {invalidLocationCounter.incrementAndGet();}
                        timingTracer.mark("isLocationValid");

                        // Skip this check for now
                        if (!isActivityMeaningful(activity)) {notMeaningfulCounter.incrementAndGet();}
                        timingTracer.mark("isActivityMeaningful");

                    } else {
                        notUpdatedCounter.incrementAndGet();
                    }

                    long elapsed = timingTracer.getTotalTime();
                    if (elapsed > 500) {
                        logger.info("Adding VM-object with key {} took {} ms: {}", key, elapsed, timingTracer);
                    }

                });
        TimingTracer timingTracer = new TimingTracer("all-vm [" + changes.size() + " changes]");

//        checksumCache.putAll(checksumCacheTmp);
//        timingTracer.mark("checksumCache.putAll");
        if (FEATURE_TOGGLE_USE_ASYNC_SET) {
            monitoredVehicles.setAllAsync(changes);
            timingTracer.mark("monitoredVehicles.setAllAsync");
        } else {
            monitoredVehicles.setAll(changes);
            timingTracer.mark("monitoredVehicles.setAll");
        }

        logger.info("Updated {} (of {}) :: Ignored elements - Missing location:{}, Missing values: {}, Expired: {}, Not updated: {}", changes.size(), vmList.size(), invalidLocationCounter.get(), notMeaningfulCounter.get(), outdatedCounter.get(), notUpdatedCounter.get());

        markDataReceived(SiriDataType.VEHICLE_MONITORING, datasetId, vmList.size(), changes.size(), outdatedCounter.get(), (invalidLocationCounter.get() + notMeaningfulCounter.get() + notUpdatedCounter.get()));

        timingTracer.mark("markDataReceived");
        markIdsAsUpdated(changes.keySet());
        timingTracer.mark("markIdsAsUpdated");

        if (timingTracer.getTotalTime() > 1000) {
            logger.info(timingTracer.toString());
        }

        return changes.values();
    }

    private void resolveContentMetrics(VehicleActivityStructure activity, long expiration) {
        if (activity != null) {
            if (activity.getMonitoredVehicleJourney() != null) {
                String dataSource = activity.getMonitoredVehicleJourney().getDataSource();
                if (activity.getMonitoredVehicleJourney().getOccupancy() != null) {
                    metrics.registerSiriContent(
                            SiriDataType.VEHICLE_MONITORING,
                            dataSource,
                            null,
                            SiriContent.OCCUPANCY_TRIP
                    );
                }

                if (expiration < 0) {
                    metrics.registerSiriContent(
                            SiriDataType.VEHICLE_MONITORING,
                            dataSource,
                            null,
                            SiriContent.TOO_LATE
                    );
                }
            }
        }
    }

    private static boolean isUpdated(String currentChecksum, String existingChecksum) {
        return currentChecksum == null || !currentChecksum.equals(existingChecksum);
    }

    private static String calculateChecksum(VehicleActivityStructure vehicleActivityStructure) {
        String existingChecksum = null;
        if (vehicleActivityStructure != null) {
            ZonedDateTime validUntilTime = vehicleActivityStructure.getValidUntilTime();
            try {
                // Calculate checksum without "ValidUntilTime" - thus ignoring "fake" updates where only validity is updated
                vehicleActivityStructure.setValidUntilTime(null);
                existingChecksum = getChecksum(vehicleActivityStructure);
            } catch (Exception e) {
                //Ignore - data will be updated
            } finally {
                //Set original ValidUntilTime back
                vehicleActivityStructure.setValidUntilTime(validUntilTime);
            }
        }
        return existingChecksum;
    }

    public VehicleActivityStructure add(String datasetId, VehicleActivityStructure activity) {
        if (activity == null ||
                activity.getMonitoredVehicleJourney() == null ||
                activity.getMonitoredVehicleJourney().getVehicleRef() == null) {
            return null;
        }
        List<VehicleActivityStructure> activities = new ArrayList<>();
        activities.add(activity);
        addAll(datasetId, activities);
        return monitoredVehicles.get(createKey(datasetId, activity.getMonitoredVehicleJourney()));
    }

    /*
     * For VehicleActivity/MonitoredVehicleJourney - VehicleLocation is required to be valid according to schema
     * Only valid objects should be kept
     */
    private boolean isLocationValid(VehicleActivityStructure activity) {
        boolean keep = true;
        VehicleActivityStructure.MonitoredVehicleJourney monitoredVehicleJourney = activity.getMonitoredVehicleJourney();

        if (monitoredVehicleJourney != null && monitoredVehicleJourney.getVehicleLocation() != null) {

            LocationStructure vehicleLocation = monitoredVehicleJourney.getVehicleLocation();

            final String vehicleRef = vehicleRefToString(monitoredVehicleJourney);

            if(vehicleLocation.getLongitude() == null && vehicleLocation.getCoordinates() == null) {
                keep = false;
                logger.info("Null location {}", vehicleRef);
                logger.trace("Skipping invalid VehicleActivity - VehicleLocation is required, but is not set.");
            }
            if((vehicleLocation.getLongitude() != null && vehicleLocation.getLongitude().doubleValue() == 0) ||
                    (vehicleLocation.getLatitude() != null && vehicleLocation.getLatitude().doubleValue() == 0)) {
                keep = false;
                logger.info("Invalid location [{}, {}], {}", vehicleLocation.getLongitude(), vehicleLocation.getLatitude(), vehicleRef);
                logger.trace("Skipping invalid VehicleActivity - VehicleLocation is included, but is not set correctly.");
            }
            if (vehicleLocation.getCoordinates() != null) {
                CoordinatesStructure coordinates = vehicleLocation.getCoordinates();
                List<String> values = coordinates.getValues();
                for (String value : values) {
                    logger.info("Found coordinates: {}", value);
                }
            }
        } else {
            keep = false;
        }
        return keep;
    }

    private String vehicleRefToString(VehicleActivityStructure.MonitoredVehicleJourney monitoredVehicleJourney) {
        StringBuilder s = new StringBuilder();
        s.append("[")
                .append("LineRef: ").append(monitoredVehicleJourney.getLineRef() != null ? monitoredVehicleJourney.getLineRef().getValue() : "null")
                .append(",VehicleRef: ").append(monitoredVehicleJourney.getVehicleRef() != null ? monitoredVehicleJourney.getVehicleRef().getValue() : "null")
                .append(",OperatorRef: ").append(monitoredVehicleJourney.getOperatorRef() != null ? monitoredVehicleJourney.getOperatorRef().getValue() : "null")
        .append("]")
        ;
        return s.toString();
    }

    /*
     * A lot of the VM-data received adds no actual value, and does not provide enough data to identify a journey
     * This method identifies these activities, and flags them as trash.
     */
    private boolean isActivityMeaningful(VehicleActivityStructure activity) {
        boolean keep = true;

        if (activity.getValidUntilTime() == null) {
            return false;
        }

        return keep;
    }

    /**
     * Creates unique key - assumes that any operator has a set of unique VehicleRefs
     * @param datasetId
     * @param monitoredVehicleJourney
     * @return
     */
    private SiriObjectStorageKey createKey(String datasetId, VehicleActivityStructure.MonitoredVehicleJourney monitoredVehicleJourney) {
        StringBuilder key = new StringBuilder();


        if (monitoredVehicleJourney.getVehicleRef() != null) {
            VehicleRef vehicleRef = monitoredVehicleJourney.getVehicleRef();
            key.append(vehicleRef.getValue());
        }
        String lineRef = null;
        if (monitoredVehicleJourney.getLineRef() != null) {
            lineRef = monitoredVehicleJourney.getLineRef().getValue();
        }
        String journeyRef = null;
        if (monitoredVehicleJourney.getVehicleJourneyRef() != null) {
            journeyRef = monitoredVehicleJourney.getVehicleJourneyRef().getValue();
        } else if (monitoredVehicleJourney.getFramedVehicleJourneyRef() != null &&
                monitoredVehicleJourney.getFramedVehicleJourneyRef().getDatedVehicleJourneyRef() != null) {
            journeyRef = monitoredVehicleJourney.getFramedVehicleJourneyRef().getDatedVehicleJourneyRef();
        }
        if (journeyRef != null) {
            key.append("-");
            key.append(journeyRef);
        }

        return new SiriObjectStorageKey(datasetId, lineRef, key.toString());

    }
}
