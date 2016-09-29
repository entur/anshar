package no.rutebanken.anshar.messages;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.org.siri.siri20.*;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class VehicleActivities extends DistributedCollection {
    private static Logger logger = LoggerFactory.getLogger(VehicleActivities.class);

    private static Map<String, VehicleActivityStructure> vehicleActivities = getVehiclesMap();

    /**
     * @return All vehicle activities that are still valid
     */
    public static List<VehicleActivityStructure> getAll() {
        removeExpiredElements();

        return new ArrayList<>(vehicleActivities.values());
    }

    /**
     * @return All vehicle activities that are still valid
     */
    public static List<VehicleActivityStructure> getAll(String vendor) {
        removeExpiredElements();

        Map<String, VehicleActivityStructure> vendorSpecific = new HashMap<>();
        vehicleActivities.keySet().stream().filter(key -> key.startsWith(vendor + ":")).forEach(key -> {
            VehicleActivityStructure structure = vehicleActivities.get(key);
            if (structure != null) {
                vendorSpecific.put(key, structure);
            }
        });

        return new ArrayList<>(vendorSpecific.values());
    }

    private static synchronized void removeExpiredElements() {

        List<String> itemsToRemove = new ArrayList<>();
        for (String key : vehicleActivities.keySet()) {
            VehicleActivityStructure current = vehicleActivities.get(key);
            if (!isStillValid(current)) {
                itemsToRemove.add(key);
            }
        }

        for (String rm : itemsToRemove) {
            vehicleActivities.remove(rm);
        }

    }

    private static boolean isStillValid(VehicleActivityStructure a) {
        if (a == null) {
            //Other parallel thread may have removed object
            return false;
        }
        ZonedDateTime validUntilTime = a.getValidUntilTime();

        if (validUntilTime == null || validUntilTime.isAfter(ZonedDateTime.now())) {
            return true;
        }

        return false;
    }

    public static VehicleActivityStructure add(VehicleActivityStructure activity, String datasetId) {
        if (activity == null) {
            return activity;
        }
        boolean keep = isLocationValid(activity) && isActivityMeaningful(activity);

        if (keep) {
            VehicleActivityStructure previousValue = vehicleActivities.put(createKey(datasetId, activity), activity);

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

        if (activity.getItemIdentifier() != null) {
            // Identifier already exists
            return key.append(datasetId).append(activity.getItemIdentifier()).toString();
        }

        //Create identifier based on other information
        key.append(datasetId).append(":")
                .append((activity.getMonitoredVehicleJourney().getLineRef() != null ? activity.getMonitoredVehicleJourney().getLineRef().getValue() : "null"))
                .append(":")
                .append((activity.getMonitoredVehicleJourney().getVehicleRef() != null ? activity.getMonitoredVehicleJourney().getVehicleRef().getValue() :"null"))
                .append(":")
                .append((activity.getMonitoredVehicleJourney().getDirectionRef() != null ? activity.getMonitoredVehicleJourney().getDirectionRef().getValue() :"null"))
                .append(":")
                .append((activity.getMonitoredVehicleJourney().getOriginAimedDepartureTime() != null ? activity.getMonitoredVehicleJourney().getOriginAimedDepartureTime().toLocalDateTime().toString() :"null"));
        return key.toString();
    }
}
