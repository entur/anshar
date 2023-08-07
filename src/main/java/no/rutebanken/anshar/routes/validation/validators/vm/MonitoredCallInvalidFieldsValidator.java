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

import jakarta.xml.bind.ValidationEvent;
import no.rutebanken.anshar.routes.validation.validators.NsrQuayValidator;
import no.rutebanken.anshar.routes.validation.validators.Validator;
import no.rutebanken.anshar.subscription.SiriDataType;
import org.springframework.stereotype.Component;
import org.w3c.dom.Node;

import static no.rutebanken.anshar.routes.validation.validators.Constants.MONITORED_CALL_STRUCTURE;

/**
 * Verifies that forbidden fields do not exist
 */
@Validator(profileName = "norway", targetType = SiriDataType.VEHICLE_MONITORING)
@Component
public class MonitoredCallInvalidFieldsValidator extends NsrQuayValidator {

    private static final String FIELDNAME = "MonitoredCall";
    private String path = MONITORED_CALL_STRUCTURE;

    @Override
    public String getXpath() {
        return path;
    }

    @Override
    public String getCategoryName() {
        return FIELDNAME;
    }

    @Override
    public ValidationEvent isValid(Node node) {
        return verifyNonExistingFields(node, FIELDNAME,
                "LatestExpectedArrivalTime",
                "EarliestExpectedDepartureTime",
                "DepartureStopAssignment");

    }
}
