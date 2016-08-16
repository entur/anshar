package no.rutebanken.anshar.messages;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.org.siri.siri20.LocationStructure;
import uk.org.siri.siri20.VehicleActivityStructure;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

public class Vehicles extends DistributedCollection {
    private static Logger logger = LoggerFactory.getLogger(Vehicles.class);

    private static List<VehicleActivityStructure> vehicleActivities = getVehiclesList();

    /**
     * @return All vehicle activities that are still valid
     */
    public static List<VehicleActivityStructure> getAll() {
        removeExpiredElements();

        return vehicleActivities;
    }

    private static void removeExpiredElements() {

        List<VehicleActivityStructure> itemsToRemove = new ArrayList<>();

        for (int i = 0; i < vehicleActivities.size(); i++) {
            VehicleActivityStructure current = vehicleActivities.get(i);
            if ( !isStillValid(current)) {
                itemsToRemove.add(current);
            }
        }
        vehicleActivities.removeAll(itemsToRemove);
    }

    private static boolean isStillValid(VehicleActivityStructure a) {
        boolean isStillValid = false;
        ZonedDateTime validUntilTime = a.getValidUntilTime();

        //Keep if at least one is valid
        if (validUntilTime == null) {
            isStillValid = true;
        } else if (validUntilTime.isAfter(ZonedDateTime.now())) {
            isStillValid = true;
        }

        //For VehicleActivity/MonitoredVehicleJourney - VehicleLocation is required to be valid according to schema
        VehicleActivityStructure.MonitoredVehicleJourney monitoredVehicleJourney = a.getMonitoredVehicleJourney();
        if (monitoredVehicleJourney != null) {
            LocationStructure vehicleLocation = monitoredVehicleJourney.getVehicleLocation();
            if (vehicleLocation != null) {
                if(vehicleLocation.getLongitude() == null & vehicleLocation.getCoordinates() == null) {
                    isStillValid = false;
                    logger.info("Invalid VehicleActivity - VehicleLocation is not set");
                }
            }
        }
        return isStillValid;
    }

    public static void add(VehicleActivityStructure activity) {
        int indexToReplace = -1;
        for (int i = 0; i < vehicleActivities.size(); i++) {
            VehicleActivityStructure element = vehicleActivities.get(i);
            if (element.getMonitoredVehicleJourney().getVehicleRef().getValue().equals(
                    activity.getMonitoredVehicleJourney().getVehicleRef().getValue()) &&
               element.getMonitoredVehicleJourney().getCourseOfJourneyRef().getValue().equals(
                            activity.getMonitoredVehicleJourney().getCourseOfJourneyRef().getValue())) {

                //Same Identifier already exists - replace existing
                logger.info("Updating VehicleActivity for VehicleRef/CourseOfJourney [{}]/[{}]",
                        activity.getMonitoredVehicleJourney().getVehicleRef().getValue(),
                        activity.getMonitoredVehicleJourney().getCourseOfJourneyRef().getValue());
                indexToReplace = i;
                break; //Found item to replace - no need to continue
            }
        }
        if (indexToReplace >= 0) {
            vehicleActivities.remove(indexToReplace);
            vehicleActivities.add(indexToReplace, activity);
        } else {
            vehicleActivities.add(activity);
        }
    }
}
