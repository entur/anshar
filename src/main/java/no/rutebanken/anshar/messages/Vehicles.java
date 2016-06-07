package no.rutebanken.anshar.messages;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.org.siri.siri20.VehicleActivityStructure;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

public class Vehicles {
    private static Logger logger = LoggerFactory.getLogger(Vehicles.class);

    private static List<VehicleActivityStructure> vehicleActivities = new ArrayList<>();

    /**
     * @return All vehicle activities that are still valid
     */
    public static List<VehicleActivityStructure> getAll() {
        vehicleActivities.removeIf(s -> {
            boolean isStillValid = false;
            ZonedDateTime validUntilTime = s.getValidUntilTime();
            //Keep if at least one is valid
            if (validUntilTime == null || validUntilTime.isAfter(ZonedDateTime.now())) {
                isStillValid = true;
            } else {
                //No validity - keep "forever"
                isStillValid = true;
            }
            return !isStillValid;
        });

        return vehicleActivities;
    }

    public static void add(VehicleActivityStructure situation) {

        int indexToReplace = -1;
        for (int i = 0; i < vehicleActivities.size(); i++) {
            VehicleActivityStructure element = vehicleActivities.get(i);
            if (element.getMonitoredVehicleJourney().getVehicleRef().getValue().equals(
                    situation.getMonitoredVehicleJourney().getVehicleRef().getValue())) {

                //Same Identifier already exists - replace existing
                indexToReplace = i;
                break; //Found item to replace - no need to continue
            }
        }
        if (indexToReplace >= 0) {
            vehicleActivities.remove(indexToReplace);
            vehicleActivities.add(indexToReplace, situation);
        } else {
            vehicleActivities.add(situation);
        }
    }
}
