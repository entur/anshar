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

package no.rutebanken.anshar.routes.validation.validators.et;

import com.google.common.collect.Sets;
import no.rutebanken.anshar.routes.validation.validators.LimitedSubsetValidator;
import no.rutebanken.anshar.routes.validation.validators.Validator;
import no.rutebanken.anshar.subscription.SiriDataType;
import org.springframework.stereotype.Component;
import uk.org.siri.siri21.OccupancyEnumeration;

import static no.rutebanken.anshar.routes.validation.validators.Constants.ESTIMATED_VEHICLE_JOURNEY;

/**
 * Verifies that the value for field Occupancy is one of the allowed types
 *
 */
@Validator(profileName = "norway", targetType = SiriDataType.ESTIMATED_TIMETABLE)
@Component
public class OccupancyValidator extends LimitedSubsetValidator {



    private String path;

    public OccupancyValidator() {
        FIELDNAME = "Occupancy";
        path = ESTIMATED_VEHICLE_JOURNEY + FIELD_DELIMITER + FIELDNAME;
        expectedValues = Sets.newHashSet(
                OccupancyEnumeration.FULL.value(),
                OccupancyEnumeration.STANDING_AVAILABLE.value(),
                OccupancyEnumeration.SEATS_AVAILABLE.value(),
                OccupancyEnumeration.MANY_SEATS_AVAILABLE.value(),
                OccupancyEnumeration.NOT_ACCEPTING_PASSENGERS.value(),
                OccupancyEnumeration.UNKNOWN.value()
        );
    }

    @Override
    public String getXpath() {
        return path;
    }
}
