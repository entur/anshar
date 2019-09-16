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
public class IncreasingTimesValidator extends CustomValidator {

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

    public IncreasingTimesValidator() {
    }

    @Override
    public String getCategoryName() {
        return "Negative dwell/run times";
    }

    @Override
    public String getXpath() {
        return path;
    }

    @Override
    public ValidationEvent isValid(Node node) {

        long previousDeparture = -1;

        String lineRef = getChildNodeValue(node, "LineRef");
        String vehicleRef = getChildNodeValue(node, "VehicleRef");

        // Comparing aimed- and actual-times for RecordedCall
        Node recordedCallsNode = getChildNodeByName(node, recordedCallsParentNodeName);
        if (recordedCallsNode != null) {
            List<Node> recordedCallNodes = getChildNodesByName(recordedCallsNode, recordedCallNodeName);
            if (recordedCallNodes != null) {
                for (Node call : recordedCallNodes) {

                    String stopPointRef = getChildNodeValue(call, stopPointRefNodeName);

                    try {
                        previousDeparture = validateIncreasingTimes(previousDeparture, call);
                    } catch (NegativeDwelltimeException e) {
                        if (!isDepartureCancelled(call)) {
                            return createCustomFieldEvent(node, "Departure before arrival - at " + getIdentifierString(stopPointRef, lineRef, vehicleRef), ValidationEvent.FATAL_ERROR);
                        }
                    } catch (NegativeRuntimeException e) {
                        if (!isArrivalCancelled(call)) {
                            return createCustomFieldEvent(node, "Arrival before departure from previous stop - at " + getIdentifierString(stopPointRef, lineRef, vehicleRef), ValidationEvent.FATAL_ERROR);
                        }
                    }
                }
            }
        }
        // Comparing aimed-, actual-, and expected times for EstimatedCall
        Node estimatededCallsNode = getChildNodeByName(node, estimatedCallsParentNodeName);
        if (estimatededCallsNode != null) {
            List<Node> estimatededCallNodes = getChildNodesByName(estimatededCallsNode, estimatedCallNodeName);
            if (estimatededCallNodes != null) {
                for (Node call : estimatededCallNodes) {

                    String stopPointRef = getChildNodeValue(call, stopPointRefNodeName);
                    try {
                        previousDeparture = validateIncreasingTimes(previousDeparture, call);
                    } catch (NegativeDwelltimeException e) {
                        if (!isDepartureCancelled(call)) { // Do not flag negative dwell-time as an error when departure is cancelled.
                            return createCustomFieldEvent(node, "Departure before arrival - at " + getIdentifierString(stopPointRef, lineRef, vehicleRef), ValidationEvent.FATAL_ERROR);
                        }
                    } catch (NegativeRuntimeException e) {
                        if (!isArrivalCancelled(call)) { // Do not flag negative run-time as an error when arrival is cancelled.
                            return createCustomFieldEvent(node, "Arrival before departure from previous stop - at " + getIdentifierString(stopPointRef, lineRef, vehicleRef), ValidationEvent.FATAL_ERROR);
                        }
                    }
                }
            }
        }


        return null;
    }

    private boolean isArrivalCancelled(Node call) {

        String arrivalStatus = getChildNodeValue(call, "ArrivalStatus");

        String cancellation = getChildNodeValue(call, "Cancellation");

        boolean isArrivalCancelled = (arrivalStatus != null) && arrivalStatus.toLowerCase().equals("cancelled");
        boolean isCancelled = (cancellation != null) && cancellation.toLowerCase().equals("true");

        return isArrivalCancelled | isCancelled;
    }

    private boolean isDepartureCancelled(Node call) {


        String departureStatus = getChildNodeValue(call, "DepartureStatus");
        String cancellation = getChildNodeValue(call, "Cancellation");

        boolean isDepartureCancelled = (departureStatus != null) && departureStatus.toLowerCase().equals("cancelled");
        boolean isCancelled = (cancellation != null) && cancellation.toLowerCase().equals("true");

        return isDepartureCancelled | isCancelled;
    }

    private String getIdentifierString(String stopPointRef, String lineRef, String vehicleRef) {
        return MessageFormat.format("Stop [{0}], Line [{1}], VehicleRef [{2}]", stopPointRef, lineRef, vehicleRef);
    }

    private long validateIncreasingTimes(long previousDeparture, Node call) throws NegativeDwelltimeException, NegativeRuntimeException{
        long aimedArrivalTime = parse(getChildNodeValue(call, aimedArrivalNodeName));
        long expectedArrivalTime = parse(getChildNodeValue(call, expectedArrivalNodeName));
        long actualArrivalTime = parse(getChildNodeValue(call, actualArrivalNodeName));

        long aimedDepartureTime = parse(getChildNodeValue(call, aimedDepartureNodeName));
        long expectedDepartureTime = parse(getChildNodeValue(call, expectedDepartureNodeName));
        long actualDepartureTime = parse(getChildNodeValue(call, actualDepartureNodeName));

        long arrival = -1;

        if (aimedArrivalTime > 0) {
            arrival = aimedArrivalTime;
        }
        if (expectedArrivalTime > 0) {
            arrival = expectedArrivalTime;
        }
        if (actualArrivalTime > 0) {
            arrival = actualArrivalTime;
        }

        long departure = -1;

        if (aimedDepartureTime > 0) {
            departure = aimedDepartureTime;
        }
        if (expectedDepartureTime > 0) {
            departure = expectedDepartureTime;
        }
        if (actualDepartureTime > 0) {
            departure = actualDepartureTime;
        }

        if (arrival < previousDeparture) {
            // Negative run-time
            throw new NegativeRuntimeException();
        }

        if (departure > 0 && departure < arrival) {
            // Negative dwell-time
            throw new NegativeDwelltimeException();
        }

        return departure;
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

    private class NegativeRuntimeException extends Exception {
    }

    private class NegativeDwelltimeException extends Exception {
    }
}
