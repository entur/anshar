package no.rutebanken.anshar.messages;

import com.hazelcast.core.IMap;
import org.quartz.utils.counter.Counter;
import org.quartz.utils.counter.CounterImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Repository;
import uk.org.siri.siri20.*;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Repository
public class VehicleActivities {
    private Logger logger = LoggerFactory.getLogger(VehicleActivities.class);

    @Autowired
    private IMap<String, VehicleActivityStructure> vehicleActivities;

    @Autowired
    @Qualifier("getVehicleChangesMap")
    private IMap<String, Set<String>> changesMap;

    /**
     * @return All vehicle activities that are still valid
     */
    public List<VehicleActivityStructure> getAll() {
        return new ArrayList<>(vehicleActivities.values());
    }

    public int getSize() {
        return vehicleActivities.size();
    }

    /**
     * @return All vehicle activities that are still valid
     */
    public List<VehicleActivityStructure> getAll(String datasetId) {
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


    /**
     * @return All vehicle activities that have been updated since last request from requestor
     */
    public List<VehicleActivityStructure> getAllUpdates(String requestorId) {
        if (requestorId != null) {

            Set<String> idSet = changesMap.get(requestorId);
            if (idSet != null) {
                List<VehicleActivityStructure> changes = new ArrayList<>();

                idSet.stream().forEach(key -> {
                    VehicleActivityStructure element = vehicleActivities.get(key);
                    if (element != null) {
                        changes.add(element);
                    }
                });
                Set<String> existingSet = changesMap.get(requestorId);
                if (existingSet == null) {
                    existingSet = new HashSet<>();
                }
                existingSet.removeAll(idSet);
                changesMap.put(requestorId, existingSet);
                return changes;
            } else {
                changesMap.put(requestorId, new HashSet<>());
            }
        }

        return getAll();
    }

    private long getExpiration(VehicleActivityStructure a) {

        ZonedDateTime validUntil = a.getValidUntilTime();
        if (validUntil != null) {
            return ZonedDateTime.now().until(validUntil, ChronoUnit.MILLIS);
        }

        return 0;
    }

    public void addAll(String datasetId, List<VehicleActivityStructure> vmList) {
        Set<String> changes = new HashSet<>();

        Counter invalidLocationCounter = new CounterImpl(0);
        Counter notMeaningfulCounter = new CounterImpl(0);
        Counter outdatedCounter = new CounterImpl(0);
        vmList.forEach(activity -> {
            boolean locationValid = isLocationValid(activity);
            boolean activityMeaningful = isActivityMeaningful(activity);

            if (locationValid && activityMeaningful) {
                String key = createKey(datasetId, activity);

                VehicleActivityStructure existing = vehicleActivities.get(key);

                boolean keep = (existing == null); //No existing data i.e. keep

                if (existing != null &&
                        (activity.getRecordedAtTime() != null && existing.getRecordedAtTime() != null)) {
                    //Newer data has already been processed
                    keep = activity.getRecordedAtTime().isAfter(existing.getRecordedAtTime());
                }

                long expiration = getExpiration(activity);

                if (expiration >= 0 && keep) {
                    changes.add(key);
                    vehicleActivities.put(key, activity, expiration, TimeUnit.MILLISECONDS);
                } else {
                    outdatedCounter.increment();
                }
            } else {
                if (locationValid) {invalidLocationCounter.increment();}
                if (activityMeaningful) {notMeaningfulCounter.increment();}
            }
        });

        logger.info("Updated {} :: Ignored elements - Missing location:{}, Missing values: {}, Outdated: {}", changes.size(), invalidLocationCounter.getValue(), notMeaningfulCounter.getValue(), outdatedCounter.getValue());

        changesMap.keySet().forEach(requestor -> {
            Set<String> tmpChanges = changesMap.get(requestor);
            tmpChanges.addAll(changes);
            changesMap.put(requestor, tmpChanges);
        });

    }

    VehicleActivityStructure add(String datasetId, VehicleActivityStructure activity) {
        if (activity == null) {
            return null;
        }
        List<VehicleActivityStructure> activities = new ArrayList<>();
        activities.add(activity);
        addAll(datasetId, activities);
        return vehicleActivities.get(createKey(datasetId, activity));
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
                    logger.trace("Skipping invalid VehicleActivity - VehicleLocation is required, but is not set.");
                }
            }
        } else {
            keep = false;
        }
        return keep;
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

    private String createKey(String datasetId, VehicleActivityStructure activity) {
        StringBuffer key = new StringBuffer();
        key.append(datasetId).append(":");

        if (activity.getItemIdentifier() != null) {
            // Identifier already exists
            return key.append(activity.getItemIdentifier()).toString();
        }

        //Create identifier based on other information
        key.append((activity.getMonitoredVehicleJourney().getLineRef() != null ? activity.getMonitoredVehicleJourney().getLineRef().getValue() : "null"))
                .append(":")
                .append((activity.getMonitoredVehicleJourney().getVehicleRef() != null ? activity.getMonitoredVehicleJourney().getVehicleRef().getValue() :"null"))
                .append(":")
                .append((activity.getMonitoredVehicleJourney().getDirectionRef() != null ? activity.getMonitoredVehicleJourney().getDirectionRef().getValue() :"null"))
                .append(":")
                .append((activity.getMonitoredVehicleJourney().getOriginAimedDepartureTime() != null ? activity.getMonitoredVehicleJourney().getOriginAimedDepartureTime().toLocalDateTime().toString() :"null"));
        return key.toString();
    }
}
