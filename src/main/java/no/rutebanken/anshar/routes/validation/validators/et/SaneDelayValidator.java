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
import no.rutebanken.anshar.routes.validation.validators.CustomValidator;
import no.rutebanken.anshar.routes.validation.validators.Validator;
import no.rutebanken.anshar.subscription.SiriDataType;
import org.springframework.stereotype.Component;
import org.w3c.dom.Node;

import java.text.MessageFormat;
import java.util.List;

import static no.rutebanken.anshar.routes.validation.validators.Constants.ESTIMATED_VEHICLE_JOURNEY;

/**
 * Verifies that updated times are increasing
 */
@SuppressWarnings("unchecked")
@Validator(profileName = "norway", targetType = SiriDataType.ESTIMATED_TIMETABLE)
@Component
public class SaneDelayValidator extends CustomValidator {

    private String path = ESTIMATED_VEHICLE_JOURNEY;

    private String recordedCallsParentNodeName = "RecordedCalls";
    private String recordedCallNodeName = "RecordedCall";
    private String estimatedCallsParentNodeName = "EstimatedCalls";
    private String estimatedCallNodeName = "EstimatedCall";

    private String aimedArrivalNodeName = "AimedArrivalTime";
    private String aimedDepartureNodeName = "AimedDepartureTime";
    private String expectedArrivalNodeName = "ExpectedArrivalTime";
    private String expectedDepartureNodeName = "ExpectedDepartureTime";
    private String actualArrivalNodeName = "ActualArrivalTime";
    private String actualDepartureNodeName = "ActualDepartureTime";
    private String stopPointRefNodeName = "StopPointRef";

    private static final int SANE_DELAY_LIMIT_SECONDS = 24*60*60;

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

        long aimedArrivalTime = getEpochSeconds(getChildNodeValue(call, aimedArrivalNodeName));
        long expectedArrivalTime = getEpochSeconds(getChildNodeValue(call, expectedArrivalNodeName));
        long actualArrivalTime = getEpochSeconds(getChildNodeValue(call, actualArrivalNodeName));

        long aimedDepartureTime = getEpochSeconds(getChildNodeValue(call, aimedDepartureNodeName));
        long expectedDepartureTime = getEpochSeconds(getChildNodeValue(call, expectedDepartureNodeName));
        long actualDepartureTime = getEpochSeconds(getChildNodeValue(call, actualDepartureNodeName));

        long arrivalDelay = 0;
        long updatedArrival = -1;

        if ((expectedArrivalTime == 0 && actualArrivalTime == 0) ||
                (expectedDepartureTime == 0 && actualDepartureTime == 0)){
            return;
        }

        if (expectedArrivalTime > 0) {
            updatedArrival = expectedArrivalTime;
            if (expectedDepartureTime == 0) {
                return;
            }
        }

        if (actualArrivalTime > 0) {
            updatedArrival = actualArrivalTime;
            if (actualDepartureTime == 0) {
                return;
            }
        }

        if (aimedArrivalTime > 0) {
            arrivalDelay = updatedArrival - aimedArrivalTime;
            if (aimedDepartureTime == 0) {
                return;
            }
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

    private static class TooLongDelayException extends Exception {

        final long delay;

        TooLongDelayException(long delay) {
            this.delay = delay;
        }

        @Override
        public String getMessage() {
            return "Delay of " + formatSeconds(delay) + " is too long";
        }

        private String formatSeconds(long timeInSeconds){

            int secondsLeft = Math.abs((int) timeInSeconds % 3600 % 60);
            int minutes = Math.abs((int) (timeInSeconds % 3600 / 60));
            int hours = Math.abs((int) (timeInSeconds / 3600));

            String hoursString = ((hours       < 10) ? "0" : "") + hours;
            String minString = ((minutes     < 10) ? "0" : "") + minutes;
            String secString = ((secondsLeft < 10) ? "0" : "") + secondsLeft;

            return hoursString + " h, " + minString + " min, " + secString + ", sec";

        }
    }
}
