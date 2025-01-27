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

import jakarta.xml.bind.ValidationEvent;
import no.rutebanken.anshar.routes.validation.validators.TimeValidator;
import no.rutebanken.anshar.routes.validation.validators.Validator;
import no.rutebanken.anshar.subscription.SiriDataType;
import org.springframework.stereotype.Component;
import org.w3c.dom.Node;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;

import static no.rutebanken.anshar.routes.validation.validators.Constants.ESTIMATED_CALLS;

/**
 * Verifies that the value for field AimedArrivalTime is a valid timestamp, and that it is before or equal to AimedDepartureTime
 *
 */
@Validator(profileName = "norway", targetType = SiriDataType.ESTIMATED_TIMETABLE)
@Component
public class UpdateReceivedTooSoonValidator extends TimeValidator {


    private static final String FIELDNAME = "EstimatedCall";
    private static final int DAYS = 7;
    private static final long MAX_HOURS_UNTIL_FIRST_DEPARTURE = 24*DAYS;
    private String path = ESTIMATED_CALLS;
    private String arrivalFieldName = "AimedArrivalTime";
    private String departureFieldName = "AimedDepartureTime";

    @Override
    public String getXpath() {
        return path;
    }

    @Override
    public ValidationEvent isValid(Node node) {

        final Node firstEstimatedCall = getChildNodeByName(node, FIELDNAME);

        String firstAimedTime;

        if (getChildNodeValue(firstEstimatedCall, arrivalFieldName) != null) {
            firstAimedTime = getChildNodeValue(firstEstimatedCall, arrivalFieldName);
        } else {
            firstAimedTime = getChildNodeValue(firstEstimatedCall, departureFieldName);
        }
        if (firstAimedTime != null) {
            ZonedDateTime aimed = parseDate(firstAimedTime);
            ZonedDateTime currentTime = ZonedDateTime.now();

            long timeUntilDeparture = ChronoUnit.HOURS.between(currentTime, aimed);

            if (timeUntilDeparture > MAX_HOURS_UNTIL_FIRST_DEPARTURE) {
                return createCustomFieldEvent(node, "Realtime data received more than " + DAYS + " days ahead - aimed start [" + aimed + "], current time ["+ currentTime+"]" , ValidationEvent.WARNING);
            }
        }
        return null;
    }

}
