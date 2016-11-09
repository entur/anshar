package no.rutebanken.anshar.messages;

import no.rutebanken.anshar.messages.collections.DistributedCollection;
import no.rutebanken.anshar.messages.collections.ExpiringConcurrentMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.org.siri.siri20.*;

import java.time.ZonedDateTime;
import java.util.*;

public class VehicleActivities {
    private static Logger logger = LoggerFactory.getLogger(VehicleActivities.class);

    static ExpiringConcurrentMap<String, VehicleActivityStructure> vehicleActivities;
    static ExpiringConcurrentMap<String, Set<String>> changesMap;

    static {
        DistributedCollection dc = new DistributedCollection();
        vehicleActivities = dc.getVehiclesMap();
        changesMap = dc.getVehicleChangesMap();
    }

    /**
     * @return All vehicle activities that are still valid
     */
    public static List<VehicleActivityStructure> getAll() {
        return new ArrayList<>(vehicleActivities.values());
    }

    /**
     * @return All vehicle activities that are still valid
     */
    public static List<VehicleActivityStructure> getAll(String datasetId) {
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
    public static List<VehicleActivityStructure> getAllUpdates(String requestorId) {
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
                existingSet.removeAll(idSet);
                changesMap.put(requestorId, existingSet);
                return changes;
            } else {
                changesMap.put(requestorId, new HashSet<>());
            }
        }

        return getAll();
    }

    private static ZonedDateTime getExpiration(VehicleActivityStructure a) {
        return a.getValidUntilTime();
    }

    public static VehicleActivityStructure add(VehicleActivityStructure activity, String datasetId) {
        if (activity == null) {
            return activity;
        }
        boolean keep = isLocationValid(activity) && isActivityMeaningful(activity);

        if (keep) {
            String key = createKey(datasetId, activity);

            ZonedDateTime expiration = getExpiration(activity);
            if (expiration != null && expiration.isBefore(ZonedDateTime.now())) {
                //Ignore elements that have already expired
                return null;
            }

            changesMap.keySet().forEach(requestor -> {
                Set<String> changes = changesMap.get(requestor);
                changes.add(key);
                changesMap.put(requestor, changes);
            });

            VehicleActivityStructure previousValue = vehicleActivities.put(key, activity, expiration);

            if (previousValue != null) {
                //Activity has been updated
                if (activity.getRecordedAtTime().isAfter(previousValue.getRecordedAtTime())) {
                    return activity;
                }
            } else {
                //New activity
                return activity;
            }
        }
        return null;
    }

    /*
     * For VehicleActivity/MonitoredVehicleJourney - VehicleLocation is required to be valid according to schema
     * Only valid objects should be kept
     */
    private static boolean isLocationValid(VehicleActivityStructure activity) {
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
    private static boolean isActivityMeaningful(VehicleActivityStructure activity) {
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

    private static String createKey(String datasetId, VehicleActivityStructure activity) {
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
