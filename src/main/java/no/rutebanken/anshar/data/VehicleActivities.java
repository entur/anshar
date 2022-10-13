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
import org.quartz.utils.counter.Counter;
import org.quartz.utils.counter.CounterImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Repository;
import uk.org.siri.siri21.AbstractItemStructure;
import uk.org.siri.siri21.CoordinatesStructure;
import uk.org.siri.siri21.CourseOfJourneyRefStructure;
import uk.org.siri.siri21.DirectionRefStructure;
import uk.org.siri.siri21.LineRef;
import uk.org.siri.siri21.LocationStructure;
import uk.org.siri.siri21.MessageRefStructure;
import uk.org.siri.siri21.Siri;
import uk.org.siri.siri21.VehicleActivityStructure;
import uk.org.siri.siri21.VehicleRef;

import javax.annotation.PostConstruct;
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
import java.util.stream.Collectors;

@Repository
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

    protected VehicleActivities() {
        super(SiriDataType.VEHICLE_MONITORING);
    }

    @PostConstruct
    private void initializeUpdateCommitter() {
        super.initBufferCommitter(hazelcastService, lastUpdateRequested, changesMap, configuration.getChangeBufferCommitFrequency());

        enableCache(monitoredVehicles);
        linkEntriesTtl(monitoredVehicles, changesMap, checksumCache);
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
            checksumCache.remove(id);
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

            if (validUntil.getYear() > ZonedDateTime.now().getYear()) {
                // Handle cornercase when validity is set too far ahead - e.g. year 9999
                validUntil = validUntil.withYear(ZonedDateTime.now().getYear()+1);
            }

            return ZonedDateTime.now().until(
                    validUntil.plus(configuration.getVmGraceperiodMinutes(), ChronoUnit.MINUTES),
                    ChronoUnit.MILLIS
            );
        }

        // No to-validity set - keep until graceperiod expires
        return configuration.getVmGraceperiodMinutes()*60*1000;
    }

    public Collection<VehicleActivityStructure> addAll(String datasetId, List<VehicleActivityStructure> vmList) {

        Map<SiriObjectStorageKey, VehicleActivityStructure> changes = new HashMap<>();
        Map<SiriObjectStorageKey, String> checksumCacheTmp = new HashMap<>();

        Counter invalidLocationCounter = new CounterImpl(0);
        Counter notMeaningfulCounter = new CounterImpl(0);
        Counter outdatedCounter = new CounterImpl(0);
        Counter notUpdatedCounter = new CounterImpl(0);

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
                    String currentChecksum = null;
                    ZonedDateTime validUntilTime = activity.getValidUntilTime();
                    try {
                        // Calculate checksum without "ValidUntilTime" - thus ignoring "fake" updates where only validity is updated
                        activity.setValidUntilTime(null);
                        currentChecksum = getChecksum(activity);
                        timingTracer.mark("getChecksum");
                    } catch (Exception e) {
                        //Ignore - data will be updated
                    } finally {
                        //Set original ValidUntilTime back
                        activity.setValidUntilTime(validUntilTime);
                    }

                    String existingChecksum = checksumCache.get(key);
                    timingTracer.mark("checksumCache.get");

                    boolean updated;
//                    if (existingChecksum != null && monitoredVehicles.containsKey(key)) {
                    if (existingChecksum != null) {
                        //Exists - compare values
                        updated =  !(currentChecksum.equals(existingChecksum));
                    } else {
                        //Does not exist
                        updated = true;
                    }
                    timingTracer.mark("compareChecksum");

                    if (updated) {

                        VehicleActivityStructure existing = monitoredVehicles.get(key);
                        timingTracer.mark("getExisting");

                        boolean keep = (existing == null); //No existing data i.e. keep

                        if (existing != null &&
                                (activity.getRecordedAtTime() != null && existing.getRecordedAtTime() != null)) {
                            //Newer data has already been processed
                            keep = activity.getRecordedAtTime().isAfter(existing.getRecordedAtTime());
                        }

                        long expiration = getExpiration(activity);
                        timingTracer.mark("getExpiration");

                        if (expiration > 0 && keep) {
                            changes.put(key, activity);
                            checksumCacheTmp.put(key, currentChecksum);
                        } else {
                            outdatedCounter.increment();

                            //Keeping all checksums for at least 5 minutes to avoid stale data
                            checksumCache.set(key, currentChecksum, 5, TimeUnit.MINUTES);
                            timingTracer.mark("checksumCache.set");

                        }

                        if (!isLocationValid(activity)) {invalidLocationCounter.increment();}
                        timingTracer.mark("isLocationValid");

                        // Skip this check for now
//                        if (!isActivityMeaningful(activity)) {notMeaningfulCounter.increment();}
//                        timingTracer.mark("isActivityMeaningful");

                    } else {
                        notUpdatedCounter.increment();
                    }

                    long elapsed = timingTracer.getTotalTime();
                    if (elapsed > 500) {
                        logger.info("Adding VM-object with key {} took {} ms: {}", key, elapsed, timingTracer);
                    }

                });
        TimingTracer timingTracer = new TimingTracer("all-vm [" + changes.size() + " changes]");

        checksumCache.setAll(checksumCacheTmp);
        timingTracer.mark("checksumCache.setAll");
        monitoredVehicles.setAll(changes);
        timingTracer.mark("monitoredVehicles.setAll");

        logger.info("Updated {} (of {}) :: Ignored elements - Missing location:{}, Missing values: {}, Expired: {}, Not updated: {}", changes.size(), vmList.size(), invalidLocationCounter.getValue(), notMeaningfulCounter.getValue(), outdatedCounter.getValue(), notUpdatedCounter.getValue());

        markDataReceived(SiriDataType.VEHICLE_MONITORING, datasetId, vmList.size(), changes.size(), outdatedCounter.getValue(), (invalidLocationCounter.getValue() + notMeaningfulCounter.getValue() + notUpdatedCounter.getValue()));

        timingTracer.mark("markDataReceived");
        markIdsAsUpdated(changes.keySet());
        timingTracer.mark("markIdsAsUpdated");

        if (timingTracer.getTotalTime() > 1000) {
            logger.info(timingTracer.toString());
        }

        return changes.values();
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

        VehicleActivityStructure.MonitoredVehicleJourney monitoredVehicleJourney = activity.getMonitoredVehicleJourney();
        if (monitoredVehicleJourney != null) {
            LineRef lineRef = monitoredVehicleJourney.getLineRef();
            CourseOfJourneyRefStructure courseOfJourneyRef = monitoredVehicleJourney.getCourseOfJourneyRef();
            DirectionRefStructure directionRef = monitoredVehicleJourney.getDirectionRef();
            if (lineRef == null && courseOfJourneyRef == null && directionRef == null) {
                keep = false;
                logger.trace("Assumed meaningless VehicleActivity skipped - LineRef, CourseOfJourney and DirectionRef is null.");
            }

        } else {
            keep = false;
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

        return new SiriObjectStorageKey(datasetId, lineRef, key.toString());

    }
}
