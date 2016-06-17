package no.rutebanken.anshar.messages;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.org.siri.siri20.VehicleActivityStructure;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

public class Vehicles {
    private static Logger logger = LoggerFactory.getLogger(Vehicles.class);

    private static List<VehicleActivityStructure> vehicleActivities = new ArrayList<>();

    private static Duration maximumAge = Duration.ofHours(6);

    /**
     * @return All vehicle activities that are still valid
     */
    public static List<VehicleActivityStructure> getAll() {
        vehicleActivities.removeIf(a -> {
            boolean isStillValid = false;
            ZonedDateTime validUntilTime = a.getValidUntilTime();

            //Keep if at least one is valid
            if (validUntilTime == null) {
                isStillValid = true;
            } else if (validUntilTime.isAfter(ZonedDateTime.now())) {
                isStillValid = true;
            }
            return !isStillValid;
        });

        return vehicleActivities;
    }

    public static void add(VehicleActivityStructure activity) {

        int indexToReplace = -1;
        for (int i = 0; i < vehicleActivities.size(); i++) {
            VehicleActivityStructure element = vehicleActivities.get(i);
            if (element.getMonitoredVehicleJourney().getVehicleRef().getValue().equals(
                    activity.getMonitoredVehicleJourney().getVehicleRef().getValue())) {

                //Same Identifier already exists - replace existing
                indexToReplace = i;
                break; //Found item to replace - no need to continue
            }
        }
        //Ignore if activity recorded is too old
        if (activity.getRecordedAtTime() == null || activity.getRecordedAtTime().plusSeconds(maximumAge.getSeconds()).isAfter(ZonedDateTime.now())) {
            if (indexToReplace >= 0) {
                vehicleActivities.remove(indexToReplace);
                vehicleActivities.add(indexToReplace, activity);
            } else {
                vehicleActivities.add(activity);
            }
        }
    }
}
