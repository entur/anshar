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
import uk.org.siri.siri21.CallStatusEnumeration;

import java.text.MessageFormat;
import java.util.List;

import static no.rutebanken.anshar.routes.validation.validators.Constants.ESTIMATED_VEHICLE_JOURNEY;

/**
 * Verifies that updated times are increasing
 */
@SuppressWarnings("unchecked")
@Validator(profileName = "norway", targetType = SiriDataType.ESTIMATED_TIMETABLE)
@Component
public class IncreasingTimesValidator extends CustomValidator {

    private String path = ESTIMATED_VEHICLE_JOURNEY;

    private static final String RECORDED_CALLS_PARENT_NODE_NAME = "RecordedCalls";
    private static final String RECORDED_CALL_NODE_NAME = "RecordedCall";
    private static final String ESTIMATED_CALLS_PARENT_NODE_NAME = "EstimatedCalls";
    private static final String ESTIMATED_CALL_NODE_NAME = "EstimatedCall";

    private static final String AIMED_ARRIVAL_NODE_NAME = "AimedArrivalTime";
    private static final String AIMED_DEPARTURE_NODE_NAME = "AimedDepartureTime";
    private static final String EXPECTED_ARRIVAL_NODE_NAME = "ExpectedArrivalTime";
    private static final String EXPECTED_DEPARTURE_NODE_NAME = "ExpectedDepartureTime";
    private static final String ACTUAL_ARRIVAL_NODE_NAME = "ActualArrivalTime";
    private static final String ACTUAL_DEPARTURE_NODE_NAME = "ActualDepartureTime";
    private static final String STOP_POINT_REF_NODE_NAME = "StopPointRef";

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
        Node recordedCallsNode = getChildNodeByName(node, RECORDED_CALLS_PARENT_NODE_NAME);
        if (recordedCallsNode != null) {
            List<Node> recordedCallNodes = getChildNodesByName(recordedCallsNode, RECORDED_CALL_NODE_NAME);
            if (recordedCallNodes != null) {
                for (Node call : recordedCallNodes) {

                    String stopPointRef = getChildNodeValue(call, STOP_POINT_REF_NODE_NAME);

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
        Node estimatededCallsNode = getChildNodeByName(node, ESTIMATED_CALLS_PARENT_NODE_NAME);
        if (estimatededCallsNode != null) {
            List<Node> estimatededCallNodes = getChildNodesByName(estimatededCallsNode, ESTIMATED_CALL_NODE_NAME);
            if (estimatededCallNodes != null) {
                for (Node call : estimatededCallNodes) {

                    String stopPointRef = getChildNodeValue(call, STOP_POINT_REF_NODE_NAME);
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

        boolean isArrivalCancelled = (arrivalStatus != null) && isNotVisitedStatus(arrivalStatus);
        boolean isCancelled = (cancellation != null) && cancellation.equalsIgnoreCase("true");

        return isArrivalCancelled || isCancelled;
    }


    private boolean isDepartureCancelled(Node call) {


        String departureStatus = getChildNodeValue(call, "DepartureStatus");
        String cancellation = getChildNodeValue(call, "Cancellation");

        boolean isDepartureCancelled = (departureStatus != null) && isNotVisitedStatus(departureStatus);
        boolean isCancelled = (cancellation != null) && cancellation.equalsIgnoreCase("true");

        return isDepartureCancelled || isCancelled;
    }

    private static boolean isNotVisitedStatus(String visitingStatus) {
        return visitingStatus.equalsIgnoreCase(CallStatusEnumeration.CANCELLED.value()) ||
                visitingStatus.equalsIgnoreCase(CallStatusEnumeration.MISSED.value());
    }

    private String getIdentifierString(String stopPointRef, String lineRef, String vehicleRef) {
        return MessageFormat.format("Stop [{0}], Line [{1}], VehicleRef [{2}]", stopPointRef, lineRef, vehicleRef);
    }

    private long validateIncreasingTimes(long previousDeparture, Node call) throws NegativeDwelltimeException, NegativeRuntimeException{
        long aimedArrivalTime = getEpochSeconds(getChildNodeValue(call, AIMED_ARRIVAL_NODE_NAME));
        long expectedArrivalTime = getEpochSeconds(getChildNodeValue(call, EXPECTED_ARRIVAL_NODE_NAME));
        long actualArrivalTime = getEpochSeconds(getChildNodeValue(call, ACTUAL_ARRIVAL_NODE_NAME));

        long aimedDepartureTime = getEpochSeconds(getChildNodeValue(call, AIMED_DEPARTURE_NODE_NAME));
        long expectedDepartureTime = getEpochSeconds(getChildNodeValue(call, EXPECTED_DEPARTURE_NODE_NAME));
        long actualDepartureTime = getEpochSeconds(getChildNodeValue(call, ACTUAL_DEPARTURE_NODE_NAME));

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

    private class NegativeRuntimeException extends Exception {
    }

    private class NegativeDwelltimeException extends Exception {
    }
}
