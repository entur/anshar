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
import java.util.List;

import static no.rutebanken.anshar.routes.validation.validators.Constants.ESTIMATED_VEHICLE_JOURNEY;

/**
 * Verifies that Order is present and increasing for Recorded- and Estimated calls
 */
@SuppressWarnings("unchecked")
@Validator(profileName = "norway", targetType = SiriDataType.ESTIMATED_TIMETABLE)
@Component
public class OrderValidator extends CustomValidator {

    private static final String FIELDNAME = "Order";
    private static final String path = ESTIMATED_VEHICLE_JOURNEY;

    private static final String recordedCallsParentNodeName = "RecordedCalls";
    private static final String recordedCallNodeName = "RecordedCall";
    private static final String estimatedCallsParentNodeName = "EstimatedCalls";
    private static final String estimatedCallNodeName = "EstimatedCall";
    private static final String isCompleteStopSequence = "IsCompleteStopSequence";


    public OrderValidator() {
    }

    @Override
    public String getCategoryName() {
        return FIELDNAME;
    }

    @Override
    public String getXpath() {
        return path;
    }

    @Override
    public ValidationEvent isValid(Node node) {


        String isComplete = getChildNodeValue(node, isCompleteStopSequence);
        if (isComplete == null || !Boolean.parseBoolean(isComplete)) {
            return  createEvent(node, isCompleteStopSequence, isCompleteStopSequence + "=true is required, cannot validate Order ", isComplete, ValidationEvent.FATAL_ERROR);
        }

        int expectedOrderValue = 1;

        Node recordedCallsNode = getChildNodeByName(node, recordedCallsParentNodeName);
        if (recordedCallsNode != null) {
            List<Node> recordedCallNodes = getChildNodesByName(recordedCallsNode, recordedCallNodeName);
            if (recordedCallNodes != null) {
                for (Node recordedCall : recordedCallNodes) {
                    String order = getChildNodeValue(recordedCall, FIELDNAME);
                    if (order == null || order.isEmpty()) {
                        return  createEvent(node, recordedCallNodeName + "/" + FIELDNAME, "not empty", order, ValidationEvent.ERROR);
                    }
                    if (!order.equals(""+expectedOrderValue)) {
                        return  createEvent(node, recordedCallNodeName + "/" + FIELDNAME, "increasing positiveInteger (expected " + expectedOrderValue + ")", order, ValidationEvent.FATAL_ERROR);
                    }
                    expectedOrderValue++;
                }
            }
        }

        Node estimatedCallsNode = getChildNodeByName(node, estimatedCallsParentNodeName);
        if (estimatedCallsNode != null) {
            List<Node> estimatedCallNodes = getChildNodesByName(estimatedCallsNode, estimatedCallNodeName);
            if (estimatedCallNodes != null) {
                for (Node estimatedCall : estimatedCallNodes) {
                    String order = getChildNodeValue(estimatedCall, FIELDNAME);
                    if (order == null || order.isEmpty()) {
                        return  createEvent(node, estimatedCallNodeName + "/" + FIELDNAME, "not empty", order, ValidationEvent.ERROR);
                    }
                    if (!order.equals(""+expectedOrderValue)) {
                        return  createEvent(node, estimatedCallNodeName + "/" + FIELDNAME, "increasing positiveInteger (expected " + expectedOrderValue + ")", order, ValidationEvent.FATAL_ERROR);
                    }
                    expectedOrderValue++;
                }
            }
        }


        return null;
    }
}
