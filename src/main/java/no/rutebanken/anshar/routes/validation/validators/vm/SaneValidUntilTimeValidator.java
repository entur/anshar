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

import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;

import static no.rutebanken.anshar.routes.validation.validators.Constants.VEHICLE_ACTIVITY;


/**
 * Verifies that the value for ValidUntilTime appears to be a real value
 *
 */
@Validator(profileName = "norway", targetType = SiriDataType.VEHICLE_MONITORING)
@Component
public class SaneValidUntilTimeValidator extends CustomValidator {


    private static final String VALID_UNTIL_TIME_NAME = "ValidUntilTime";
    private static final String RECORDED_AT_TIME_NAME = "RecordedAtTime";
    private String path = VEHICLE_ACTIVITY;
    private static final int MAX_VM_VALIDITY = 24*3600; // 24h


    @Override
    public String getXpath() {
        return path;
    }

    @Override
    public String getCategoryName() {
        return VALID_UNTIL_TIME_NAME;
    }

    @Override
    public ValidationEvent isValid(Node node) {
        String validUntilTime = getChildNodeValue(node, VALID_UNTIL_TIME_NAME);
        String recordedAtTime = getChildNodeValue(node, RECORDED_AT_TIME_NAME);
        if (validUntilTime != null && recordedAtTime != null) {
            try {
                final long recordedAtEpochSec = getEpochSeconds(recordedAtTime);
                final long validToEpochSec = getEpochSeconds(validUntilTime);
                final long validityPeriod = validToEpochSec - recordedAtEpochSec;

                if (validityPeriod > MAX_VM_VALIDITY) {
                    return createCustomFieldEvent(node, "ValidUntilTime states too long validity (" + validUntilTime + ")" , ValidationEvent.WARNING);
                }
                final long tenMinutesAgo = ZonedDateTime.now().minusMinutes(10).toEpochSecond();
                if (validToEpochSec < tenMinutesAgo){
                    return createCustomFieldEvent(node, "ValidUntilTime IS EXPIRED (" + validUntilTime + ")" , ValidationEvent.WARNING);
                }

            } catch (DateTimeParseException e){
                return createEvent(node,
                    VALID_UNTIL_TIME_NAME, "valid Date", validUntilTime, ValidationEvent.ERROR);
            }
        }

        return null;
    }
}
