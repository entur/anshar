package no.rutebanken.anshar.messages;

import com.hazelcast.core.IMap;
import no.rutebanken.anshar.routes.mqtt.SiriVmMqttHandler;
import no.rutebanken.anshar.routes.siri.SiriObjectFactory;
import org.quartz.utils.counter.Counter;
import org.quartz.utils.counter.CounterImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Repository;
import uk.org.siri.siri20.*;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Repository
public class VehicleActivities implements SiriRepository<VehicleActivityStructure> {
    private Logger logger = LoggerFactory.getLogger(VehicleActivities.class);

    @Autowired
    private IMap<String, VehicleActivityStructure> vehicleActivities;

    @Autowired
    @Qualifier("getVehicleChangesMap")
    private IMap<String, Set<String>> changesMap;


    @Autowired
    @Qualifier("getLastVmUpdateRequest")
    private IMap<String, Instant> lastUpdateRequested;

    @Autowired
    private SiriVmMqttHandler siriVmMqttHandler;

    @Autowired
    private SiriObjectFactory siriObjectFactory;

    /**
     * @return All vehicle activities
     */
    public Collection<VehicleActivityStructure> getAll() {
        return vehicleActivities.values();
    }

    public int getSize() {
        return vehicleActivities.size();
    }

    /**
     * @return All vehicle activities that are still valid
     */
    public Collection<VehicleActivityStructure> getAll(String datasetId) {
        if (datasetId == null) {
            return getAll();
        }
        Map<String, VehicleActivityStructure> vendorSpecific = new HashMap<>();
        vehicleActivities.keySet().stream().filter(key -> key.startsWith(datasetId + ":")).forEach(key -> {
            VehicleActivityStructure structure = vehicleActivities.get(key);
            if (structure != null) {
                vendorSpecific.put(key, structure);
            }
        });

        return new ArrayList<>(vendorSpecific.values());
    }

    @Override
    public int cleanup() {
        long t1 = System.currentTimeMillis();
        Set<String> keysToRemove = new HashSet<>();
        vehicleActivities.keySet()
                .stream()
                .forEach(key -> {
                    VehicleActivityStructure vehicleActivityStructure = vehicleActivities.get(key);
                    if (vehicleActivityStructure != null) {
                        long expiration = getExpiration(vehicleActivityStructure);
                        if (expiration < 0) {
                            keysToRemove.add(key);
                        }
                    }
                });

        logger.info("Cleanup removed {} expired elements in {} seconds.", keysToRemove.size(), (int)(System.currentTimeMillis()-t1)/1000);
        keysToRemove.forEach(key -> vehicleActivities.delete(key));
        return keysToRemove.size();
    }
    /**
     * @return All vehicle activities that have been updated since last request from requestor
     */
    public Collection<VehicleActivityStructure> getAllUpdates(String requestorId, String datasetId) {
        if (requestorId != null) {

            Set<String> idSet = changesMap.get(requestorId);
            lastUpdateRequested.set(requestorId, Instant.now(), trackingPeriodMinutes, TimeUnit.MINUTES);
            if (idSet != null) {
                Set<String> datasetFilteredIdSet = new HashSet<>();

                if (datasetId != null) {
                    idSet.stream().filter(key -> key.startsWith(datasetId + ":")).forEach(key -> {
                        datasetFilteredIdSet.add(key);
                    });
                } else {
                    datasetFilteredIdSet.addAll(idSet);
                }

                Collection<VehicleActivityStructure> changes = vehicleActivities.getAll(datasetFilteredIdSet).values();

                Set<String> existingSet = changesMap.get(requestorId);
                if (existingSet == null) {
                    existingSet = new HashSet<>();
                }
                existingSet.removeAll(idSet);
                changesMap.set(requestorId, existingSet);

                logger.info("Returning {} changes to requestorRef {}", changes.size(), requestorId);
                return changes;
            } else {

                logger.info("Returning all to requestorRef {}", requestorId);
                changesMap.set(requestorId, new HashSet<>());
            }
        }

        return getAll(datasetId);
    }

    public Siri createServiceDelivery(String requestorId, String datasetId, int maxSize) {

        if (requestorId == null) {
            requestorId = UUID.randomUUID().toString();
        }

        // Get all relevant ids
        Set<String> allIds = new HashSet<>();
        Set<String> idSet = changesMap.getOrDefault(requestorId, allIds);

        if (idSet == allIds) {
            vehicleActivities.keySet().forEach(key -> idSet.add(key));
        }

        lastUpdateRequested.set(requestorId, Instant.now(), trackingPeriodMinutes, TimeUnit.MINUTES);

        //Filter by datasetId
        Set<String> collectedIds = idSet.stream()
                .filter(key -> datasetId == null || key.startsWith(datasetId + ":"))
                .limit(maxSize)
                .collect(Collectors.toSet());

        //Remove collected objects
        collectedIds.forEach(id -> idSet.remove(id));


        logger.info("Returning {}, {} left for requestorRef {}", collectedIds.size(), idSet.size(), requestorId);

        Boolean isMoreData = !idSet.isEmpty();

        //Update change-tracker
        changesMap.set(requestorId, idSet);

        Collection<VehicleActivityStructure> values = vehicleActivities.getAll(collectedIds).values();
        Siri siri = siriObjectFactory.createVMServiceDelivery(values);

        siri.getServiceDelivery().setMoreData(isMoreData);

        MessageRefStructure msgRef = new MessageRefStructure();
        msgRef.setValue(requestorId);
        siri.getServiceDelivery().setRequestMessageRef(msgRef);

        return siri;
    }

    public long getExpiration(VehicleActivityStructure a) {

        ZonedDateTime validUntil = a.getValidUntilTime();
        if (validUntil != null) {
            return ZonedDateTime.now().until(validUntil, ChronoUnit.MILLIS);
        }

        return -1;
    }

    public Collection<VehicleActivityStructure> addAll(String datasetId, List<VehicleActivityStructure> vmList) {
        Set<String> changes = new HashSet<>();

        Counter invalidLocationCounter = new CounterImpl(0);
        Counter notMeaningfulCounter = new CounterImpl(0);
        Counter outdatedCounter = new CounterImpl(0);
        vmList.stream()
                .filter(activity -> activity.getMonitoredVehicleJourney() != null)
                .filter(activity -> activity.getMonitoredVehicleJourney().getVehicleRef() != null)
                .forEach(activity -> {
                    boolean locationValid = isLocationValid(activity);
                    boolean activityMeaningful = isActivityMeaningful(activity);

                    if (locationValid && activityMeaningful) {
                        String key = createKey(datasetId, activity.getMonitoredVehicleJourney().getVehicleRef());

                        VehicleActivityStructure existing = vehicleActivities.get(key);

                        boolean keep = (existing == null); //No existing data i.e. keep

                        if (existing != null &&
                                (activity.getRecordedAtTime() != null && existing.getRecordedAtTime() != null)) {
                            //Newer data has already been processed
                            keep = activity.getRecordedAtTime().isAfter(existing.getRecordedAtTime());
                        }

                        long expiration = getExpiration(activity);

                        if (expiration > 0 && keep) {
                            changes.add(key);
                            vehicleActivities.set(key, activity, expiration, TimeUnit.MILLISECONDS);
                            siriVmMqttHandler.pushToMqtt(datasetId, activity);
                        } else {
                            outdatedCounter.increment();
                        }
                    } else {
                        if (!locationValid) {invalidLocationCounter.increment();}
                        if (!activityMeaningful) {notMeaningfulCounter.increment();}
                    }
                });

        logger.info("Updated {} (of {}) :: Ignored elements - Missing location:{}, Missing values: {}, Skipped: {}", changes.size(), vmList.size(), invalidLocationCounter.getValue(), notMeaningfulCounter.getValue(), outdatedCounter.getValue());

        changesMap.keySet().forEach(requestor -> {
            if (lastUpdateRequested.get(requestor) != null) {
                Set<String> tmpChanges = changesMap.get(requestor);
                tmpChanges.addAll(changes);
                changesMap.set(requestor, tmpChanges);
            } else {
                changesMap.remove(requestor);
            }
        });

        return vehicleActivities.getAll(changes).values();
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
        return vehicleActivities.get(createKey(datasetId, activity.getMonitoredVehicleJourney().getVehicleRef()));
    }

    /*
     * For VehicleActivity/MonitoredVehicleJourney - VehicleLocation is required to be valid according to schema
     * Only valid objects should be kept
     */
    private boolean isLocationValid(VehicleActivityStructure activity) {
        boolean keep = true;
        VehicleActivityStructure.MonitoredVehicleJourney monitoredVehicleJourney = activity.getMonitoredVehicleJourney();
        if (monitoredVehicleJourney != null) {
            LocationStructure vehicleLocation = monitoredVehicleJourney.getVehicleLocation();
            if (vehicleLocation != null) {
                if(vehicleLocation.getLongitude() == null & vehicleLocation.getCoordinates() == null) {
                    keep = false;
                    logger.info("Null location {}", vehicleRefToString(monitoredVehicleJourney));
                    logger.trace("Skipping invalid VehicleActivity - VehicleLocation is required, but is not set.");
                }
                if((vehicleLocation.getLongitude() != null && vehicleLocation.getLongitude().doubleValue() == 0) ||
                        (vehicleLocation.getLatitude() != null && vehicleLocation.getLatitude().doubleValue() == 0)) {
                    keep = false;
                    logger.info("Invalid location [{}, {}], {}", vehicleLocation.getLongitude(), vehicleLocation.getLatitude(), vehicleRefToString(monitoredVehicleJourney));
                    logger.trace("Skipping invalid VehicleActivity - VehicleLocation is included, but is not set correctly.");
                }
                if (vehicleLocation.getCoordinates() != null) {
                    CoordinatesStructure coordinates = vehicleLocation.getCoordinates();
                    List<String> values = coordinates.getValues();
                    for (String value : values) {
                        logger.info("Found coordinates: {}", value);
                    }
                }
            }
        } else {
            keep = false;
        }
        return keep;
    }

    private String vehicleRefToString(VehicleActivityStructure.MonitoredVehicleJourney monitoredVehicleJourney) {
        StringBuffer s = new StringBuffer();
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
            //VehicleRef vehicleRef = monitoredVehicleJourney.getVehicleRef();
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
     * @param vehicleRef
     * @return
     */
    private String createKey(String datasetId, VehicleRef vehicleRef) {
        StringBuffer key = new StringBuffer();
        key.append(datasetId).append(":").append(vehicleRef.getValue());

        return key.toString();
    }
}
