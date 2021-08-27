package no.rutebanken.anshar.routes.siri.processor.routedata;

import uk.org.siri.siri20.VehicleModesEnumeration;

import java.util.List;

public class InvalidVehicleModeForStopException extends Throwable {

        private final String msg;

        public InvalidVehicleModeForStopException(
            List<VehicleModesEnumeration> vehicleModes, String stop
        ) {
            this.msg = "Trip with mode " + vehicleModes + " stops at stop (" + stop + ")  with other mode";
        }

        @Override
        public String getMessage() {
            return msg;
        }
    }