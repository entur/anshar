package no.rutebanken.anshar.routes.siri.processor.routedata;

import uk.org.siri.siri20.EstimatedVehicleJourney;
import uk.org.siri.siri20.VehicleModesEnumeration;

import java.util.List;

import static no.rutebanken.anshar.routes.siri.processor.routedata.ExceptionUtils.resolveServiceJourneyId;

public class InvalidVehicleModeForStopException extends Throwable {

        private final String msg;

        public InvalidVehicleModeForStopException(
                EstimatedVehicleJourney estimatedVehicleJourney, List<VehicleModesEnumeration> vehicleModes, String stop
        ) {
            this.msg = "Trip with mode " + vehicleModes + " stops at stop (" + stop + ")  with other mode [" + resolveServiceJourneyId(estimatedVehicleJourney) + "].";
        }

        @Override
        public String getMessage() {
            return msg;
        }
    }