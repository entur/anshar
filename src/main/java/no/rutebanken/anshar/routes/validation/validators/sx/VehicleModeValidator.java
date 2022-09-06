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

package no.rutebanken.anshar.routes.validation.validators.sx;

import com.google.common.collect.Sets;
import no.rutebanken.anshar.routes.validation.validators.LimitedSubsetValidator;
import no.rutebanken.anshar.routes.validation.validators.Validator;
import no.rutebanken.anshar.subscription.SiriDataType;
import org.springframework.stereotype.Component;
import uk.org.siri.siri21.VehicleModesOfTransportEnumeration;

import static no.rutebanken.anshar.routes.validation.validators.Constants.AFFECTED_NETWORK;


/**
 * Verifies that the value for field VehicleMode is one of the allowed types
 *
 */
@Validator(profileName = "norway", targetType = SiriDataType.SITUATION_EXCHANGE)
@Component
public class VehicleModeValidator extends LimitedSubsetValidator {


    private String path;

    public VehicleModeValidator() {
        FIELDNAME = "VehicleMode";
        path = AFFECTED_NETWORK + FIELD_DELIMITER + FIELDNAME;
        expectedValues = Sets.newHashSet(
                VehicleModesOfTransportEnumeration.ALL.value(),
                VehicleModesOfTransportEnumeration.AIR.value(),
                VehicleModesOfTransportEnumeration.BUS.value(),
                VehicleModesOfTransportEnumeration.COACH.value(),
                VehicleModesOfTransportEnumeration.FUNICULAR.value(),
                VehicleModesOfTransportEnumeration.METRO.value(),
                VehicleModesOfTransportEnumeration.RAIL.value(),
                VehicleModesOfTransportEnumeration.TAXI.value(),
                VehicleModesOfTransportEnumeration.TELECABIN.value(),
                VehicleModesOfTransportEnumeration.TRAM.value(),
                VehicleModesOfTransportEnumeration.WATER.value(),
                VehicleModesOfTransportEnumeration.SELF_DRIVE.value()
        );
    }

    @Override
    public String getXpath() {
        return path;
    }

}
