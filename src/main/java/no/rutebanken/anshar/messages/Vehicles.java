package no.rutebanken.anshar.messages;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.org.siri.siri20.LocationStructure;
import uk.org.siri.siri20.VehicleActivityStructure;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Vehicles extends DistributedCollection {
    private static Logger logger = LoggerFactory.getLogger(Vehicles.class);

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

    public static void add(VehicleActivityStructure activity, String vendor) {
        boolean isLocationSet = true;
        //For VehicleActivity/MonitoredVehicleJourney - VehicleLocation is required to be valid according to schema
        VehicleActivityStructure.MonitoredVehicleJourney monitoredVehicleJourney = activity.getMonitoredVehicleJourney();
        if (monitoredVehicleJourney != null) {
            LocationStructure vehicleLocation = monitoredVehicleJourney.getVehicleLocation();
            if (vehicleLocation != null) {
                if(vehicleLocation.getLongitude() == null & vehicleLocation.getCoordinates() == null) {
                    isLocationSet = false;
                    logger.trace("Skipping invalid VehicleActivity - VehicleLocation is required, but is not set.");
                }
            }
        }
        if (isLocationSet) {
            VehicleActivityStructure previous = vehicleActivities.put(createKey(vendor, activity), activity);
        }
    }

    private static String createKey(String vendor, VehicleActivityStructure activity) {
        StringBuffer key = new StringBuffer();

        key.append(vendor).append(":")
                .append((activity.getMonitoredVehicleJourney().getLineRef() != null ? activity.getMonitoredVehicleJourney().getLineRef().getValue() : "null"))
                .append(":")
                .append((activity.getMonitoredVehicleJourney().getVehicleRef() != null ? activity.getMonitoredVehicleJourney().getVehicleRef().getValue() :"null"))
                .append(":")
                .append((activity.getMonitoredVehicleJourney().getDirectionRef() != null ? activity.getMonitoredVehicleJourney().getDirectionRef().getValue() :"null"));
        return key.toString();
    }
}
