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
import uk.org.siri.siri21.CallStatusEnumeration;

import static no.rutebanken.anshar.routes.validation.validators.Constants.ESTIMATED_CALL;

/**
 * Verifies that the value for field ArrivalStatus is one of the allowed types
 *
 */
@Validator(profileName = "norway", targetType = SiriDataType.ESTIMATED_TIMETABLE)
@Component
public class EstimatedArrivalStatusValidator extends LimitedSubsetValidator {

    private String path;

    public EstimatedArrivalStatusValidator() {
        FIELDNAME = "ArrivalStatus";
        path = ESTIMATED_CALL + FIELD_DELIMITER + FIELDNAME;

        expectedValues = Sets.newHashSet(
                CallStatusEnumeration.ARRIVED.value(),
                CallStatusEnumeration.CANCELLED.value(),
                CallStatusEnumeration.MISSED.value(),
                CallStatusEnumeration.EARLY.value(),
                CallStatusEnumeration.ON_TIME.value(),
                CallStatusEnumeration.DELAYED.value());
    }

    @Override
    public String getXpath() {
        return path;
    }

}
