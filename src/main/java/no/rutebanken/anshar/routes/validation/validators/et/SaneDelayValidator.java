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

import no.rutebanken.anshar.routes.validation.validators.CustomValidator;
import no.rutebanken.anshar.routes.validation.validators.Validator;
import no.rutebanken.anshar.subscription.SiriDataType;
import org.springframework.stereotype.Component;
import org.w3c.dom.Node;

import javax.xml.bind.ValidationEvent;
import java.text.MessageFormat;
import java.time.ZonedDateTime;
import java.util.List;

import static no.rutebanken.anshar.routes.validation.validators.Constants.ESTIMATED_VEHICLE_JOURNEY;

/**
 * Verifies that updated times are increasing
 */
@SuppressWarnings("unchecked")
@Validator(profileName = "norway", targetType = SiriDataType.ESTIMATED_TIMETABLE)
@Component
public class SaneDelayValidator extends CustomValidator {

    private static final String path = ESTIMATED_VEHICLE_JOURNEY;

    private static final String recordedCallsParentNodeName = "RecordedCalls";
    private static final String recordedCallNodeName = "RecordedCall";
    private static final String estimatedCallsParentNodeName = "EstimatedCalls";
    private static final String estimatedCallNodeName = "EstimatedCall";

    private static final String aimedArrivalNodeName = "AimedArrivalTime";
    private static final String aimedDepartureNodeName = "AimedDepartureTime";
    private static final String expectedArrivalNodeName = "ExpectedArrivalTime";
    private static final String expectedDepartureNodeName = "ExpectedDepartureTime";
    private static final String actualArrivalNodeName = "ActualArrivalTime";
    private static final String actualDepartureNodeName = "ActualDepartureTime";
    private static final String stopPointRefNodeName = "StopPointRef";

    private static final long SANE_DELAY_LIMIT_SECONDS = 24*60*60;

    public SaneDelayValidator() {
    }

    @Override
    public String getCategoryName() {
        return "Sane delays";
    }

    @Override
    public String getXpath() {
        return path;
    }

    @Override
    public ValidationEvent isValid(Node node) {

        String lineRef = getChildNodeValue(node, "LineRef");
        String vehicleRef = getChildNodeValue(node, "VehicleRef");

        // Checking delays for RecordedCall
        Node recordedCallsNode = getChildNodeByName(node, recordedCallsParentNodeName);
        if (recordedCallsNode != null) {
            List<Node> recordedCallNodes = getChildNodesByName(recordedCallsNode, recordedCallNodeName);
            if (recordedCallNodes != null) {
                for (Node call : recordedCallNodes) {

                    String stopPointRef = getChildNodeValue(call, stopPointRefNodeName);

                    try {
                        validateSaneDelays(call);
                    } catch (TooLongDelayException e) {
                        return createCustomFieldEvent(node, e.getMessage() + " - at " + getIdentifierString(stopPointRef, lineRef, vehicleRef), ValidationEvent.WARNING);
                    }
                }
            }
        }

        // Checking delays for EstimatedCall
        Node estimatededCallsNode = getChildNodeByName(node, estimatedCallsParentNodeName);
        if (estimatededCallsNode != null) {
            List<Node> estimatededCallNodes = getChildNodesByName(estimatededCallsNode, estimatedCallNodeName);
            if (estimatededCallNodes != null) {
                for (Node call : estimatededCallNodes) {

                    String stopPointRef = getChildNodeValue(call, stopPointRefNodeName);

                    try {
                        validateSaneDelays(call);
                    } catch (TooLongDelayException e) {
                        return createCustomFieldEvent(node, e.getMessage() + " - at " + getIdentifierString(stopPointRef, lineRef, vehicleRef), ValidationEvent.WARNING);
                    }
                }
            }
        }


        return null;
    }

    private String getIdentifierString(String stopPointRef, String lineRef, String vehicleRef) {
        return MessageFormat.format("Stop [{0}], Line [{1}], VehicleRef [{2}]", stopPointRef, lineRef, vehicleRef);
    }

    private void validateSaneDelays(Node call) throws TooLongDelayException {

        long aimedArrivalTime = parse(getChildNodeValue(call, aimedArrivalNodeName));
        long expectedArrivalTime = parse(getChildNodeValue(call, expectedArrivalNodeName));
        long actualArrivalTime = parse(getChildNodeValue(call, actualArrivalNodeName));

        long aimedDepartureTime = parse(getChildNodeValue(call, aimedDepartureNodeName));
        long expectedDepartureTime = parse(getChildNodeValue(call, expectedDepartureNodeName));
        long actualDepartureTime = parse(getChildNodeValue(call, actualDepartureNodeName));

        long arrivalDelay = 0;
        long updatedArrival = -1;

        if (expectedArrivalTime > 0) {
            updatedArrival = expectedArrivalTime;
        }
        if (actualArrivalTime > 0) {
            updatedArrival = actualArrivalTime;
        }

        if (aimedArrivalTime > 0) {
            arrivalDelay = updatedArrival - aimedArrivalTime;
        }

        long departureDelay = 0;
        long updatedDeparture = -1;

        if (expectedDepartureTime > 0) {
            updatedDeparture = expectedDepartureTime;
        }
        if (actualDepartureTime > 0) {
            updatedDeparture = actualDepartureTime;
        }

        if (aimedDepartureTime > 0) {
            departureDelay = updatedDeparture - aimedDepartureTime;
        }



        if (Math.abs(arrivalDelay) >= SANE_DELAY_LIMIT_SECONDS) {
            throw new TooLongDelayException(arrivalDelay);
        }

        if (Math.abs(departureDelay) >= SANE_DELAY_LIMIT_SECONDS) {
            throw new TooLongDelayException(departureDelay);
        }
    }

    /*
        Returns epoch-time for timestamp, or 0 if time is <code>null</code>
     */
    private long parse(String time) {
        if (time != null) {
            return ZonedDateTime.parse(time).toEpochSecond();
        }
        return 0;
    }

    private class TooLongDelayException extends Exception {

        long delay;

        TooLongDelayException(long delay) {
            this.delay = delay;
        }

        @Override
        public String getMessage() {
            return "Delay of " + formatSeconds(delay) + " is too long";
        }

        private String formatSeconds(long timeInSeconds){

            int secondsLeft = (int) timeInSeconds % 3600 % 60;
            int minutes = (int) Math.floor(timeInSeconds % 3600 / 60);
            int hours = (int) Math.floor(timeInSeconds / 3600);

            String HH = ((hours       < 10) ? "0" : "") + hours;
            String MM = ((minutes     < 10) ? "0" : "") + minutes;
            String SS = ((secondsLeft < 10) ? "0" : "") + secondsLeft;

            return HH + " h, " + MM + " min, " + SS + ", sec";

        }
    }
}
