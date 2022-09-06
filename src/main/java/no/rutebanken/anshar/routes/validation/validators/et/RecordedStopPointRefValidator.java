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

import no.rutebanken.anshar.routes.validation.validators.NsrQuayValidator;
import no.rutebanken.anshar.routes.validation.validators.Validator;
import no.rutebanken.anshar.subscription.SiriDataType;
import org.springframework.stereotype.Component;

import static no.rutebanken.anshar.routes.validation.validators.Constants.RECORDED_CALL;

/**
 * Verifies that the value for field StopPointRefStructure is built up correctly
 *
 */
@Validator(profileName = "norway", targetType = SiriDataType.ESTIMATED_TIMETABLE)
@Component
public class RecordedStopPointRefValidator extends NsrQuayValidator {


    private String path;

    public RecordedStopPointRefValidator() {
        FIELDNAME = "StopPointRef";
        path = RECORDED_CALL + FIELD_DELIMITER + FIELDNAME;
    }

    @Override
    public String getXpath() {
        return path;
    }

}
