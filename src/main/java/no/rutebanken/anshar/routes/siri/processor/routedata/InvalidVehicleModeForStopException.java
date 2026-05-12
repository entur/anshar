/*
 * Licensed under the EUPL, Version 1.2 or – as soon they will be approved by
 * the European Commission - subsequent versions of the EUPL (the "Licence");
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 *
 *   https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and
 * limitations under the Licence.
 */
package no.rutebanken.anshar.routes.siri.processor.routedata;

import uk.org.siri.siri21.EstimatedVehicleJourney;
import uk.org.siri.siri21.VehicleModesEnumeration;

import java.util.List;

import static no.rutebanken.anshar.util.SiriUtils.resolveServiceJourneyId;

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