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

package no.rutebanken.anshar.routes.validation.validators.vm;

import com.google.common.collect.Sets;
import no.rutebanken.anshar.routes.validation.validators.LimitedSubsetValidator;
import no.rutebanken.anshar.routes.validation.validators.Validator;
import no.rutebanken.anshar.subscription.SiriDataType;
import org.springframework.stereotype.Component;
import uk.org.siri.siri21.VehicleModesEnumeration;

import static no.rutebanken.anshar.routes.validation.validators.Constants.MONITORED_VEHICLE_JOURNEY;


/**
 * Verifies that the value for field VehicleMode is one of the allowed types
 *
 */
@Validator(profileName = "norway", targetType = SiriDataType.VEHICLE_MONITORING)
@Component
public class MonitoredVehicleModeValidator extends LimitedSubsetValidator {

    private final String path;

    public MonitoredVehicleModeValidator() {
        FIELDNAME = "VehicleMode";
        path = MONITORED_VEHICLE_JOURNEY + FIELD_DELIMITER + FIELDNAME;
        expectedValues = Sets.newHashSet(
                VehicleModesEnumeration.AIR.value(),
                VehicleModesEnumeration.BUS.value(),
                VehicleModesEnumeration.COACH.value(),
                VehicleModesEnumeration.FERRY.value(),
                VehicleModesEnumeration.METRO.value(),
                VehicleModesEnumeration.RAIL.value(),
                VehicleModesEnumeration.TRAM.value()
        );
    }

    @Override
    public String getXpath() {
        return path;
    }
}
