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
import no.rutebanken.anshar.routes.validation.validators.CustomValidator;
import no.rutebanken.anshar.routes.validation.validators.Validator;
import no.rutebanken.anshar.subscription.SiriDataType;
import org.springframework.stereotype.Component;
import org.w3c.dom.Node;

import java.time.Duration;
import java.time.format.DateTimeParseException;

import static no.rutebanken.anshar.routes.validation.validators.Constants.MONITORED_VEHICLE_JOURNEY;


/**
 * Verifies that the value for field Delay is a valid Duration
 *
 */
@Validator(profileName = "norway", targetType = SiriDataType.VEHICLE_MONITORING)
@Component
public class DelayValidator extends CustomValidator {


    private static final String FIELDNAME = "Delay";
    private String path = MONITORED_VEHICLE_JOURNEY + FIELD_DELIMITER + FIELDNAME;


    @Override
    public String getXpath() {
        return path;
    }

    @Override
    public ValidationEvent isValid(Node node) {
        String delay = getNodeValue(node);
        if (delay != null) {
            try {
                Duration.parse(delay);
            } catch (DateTimeParseException e){
                return createEvent(node, FIELDNAME, "valid Duration", delay, ValidationEvent.ERROR);
            }
        }

        return null;
    }
}
