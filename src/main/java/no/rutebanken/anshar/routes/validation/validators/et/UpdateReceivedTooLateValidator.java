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
import java.util.List;

import static no.rutebanken.anshar.routes.validation.validators.Constants.ESTIMATED_CALLS;

/**
 * Verifies that the value for field AimedArrivalTime is a valid timestamp, and that it is before or equal to AimedDepartureTime
 *
 */
@Validator(profileName = "norway", targetType = SiriDataType.ESTIMATED_TIMETABLE)
@Component
public class UpdateReceivedTooLateValidator extends TimeValidator {


    private static final String FIELDNAME = "EstimatedCall";
    private static final long MAX_MINUTES_AFTER_ARRIVAL = 20;
    private String path = ESTIMATED_CALLS;
    private String expectedArrivalFieldName = "ExpectedArrivalTime";
    private String expectedDepartureFieldName = "ExpectedDepartureTime";
    private String aimedArrivalFieldName = "AimedArrivalTime";
    private String aimedDepartureFieldName = "AimedDepartureTime";

    @Override
    public String getXpath() {
        return path;
    }

    @Override
    public ValidationEvent isValid(Node node) {

        final List<Node> estimatedCalls = getChildNodesByName(node, FIELDNAME);

        String journeyRef = resolveJourneyRef(node.getParentNode());

        if (estimatedCalls.isEmpty()) {
            return null;
        }
        String latestTimeStamp;

        Node lastEstimatedCall = estimatedCalls.get(estimatedCalls.size()-1);

        if (getChildNodeValue(lastEstimatedCall, expectedDepartureFieldName) != null) {
            latestTimeStamp = getChildNodeValue(lastEstimatedCall, expectedDepartureFieldName);
        } else if (getChildNodeValue(lastEstimatedCall, aimedDepartureFieldName) != null) {
            latestTimeStamp = getChildNodeValue(lastEstimatedCall, aimedDepartureFieldName);
        } else if (getChildNodeValue(lastEstimatedCall, expectedArrivalFieldName) != null) {
            latestTimeStamp = getChildNodeValue(lastEstimatedCall, expectedArrivalFieldName);
        } else {
            latestTimeStamp = getChildNodeValue(lastEstimatedCall, aimedArrivalFieldName);
        }

        if (latestTimeStamp != null) {
            ZonedDateTime aimed = parseDate(latestTimeStamp);
            ZonedDateTime currentTime = ZonedDateTime.now();

            long timeSinceArrival = ChronoUnit.MINUTES.between(currentTime, aimed);

            if (timeSinceArrival + MAX_MINUTES_AFTER_ARRIVAL < 0) {
                return createCustomFieldEvent(node,
                        "Realtime data received more than " + MAX_MINUTES_AFTER_ARRIVAL + " minutes after trip is complete - latest time in update [" + aimed + "], current time ["+ currentTime+"]. <br /> [" + journeyRef +"]" ,
                        ValidationEvent.WARNING);
            }
        }
        return null;
    }

}
