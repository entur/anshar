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

package no.rutebanken.anshar.routes.validation.validators.sx;

import jakarta.xml.bind.ValidationEvent;
import no.rutebanken.anshar.routes.validation.validators.NsrGenericIdValidator;
import no.rutebanken.anshar.routes.validation.validators.ProfileValidationEventOrList;
import no.rutebanken.anshar.routes.validation.validators.Validator;
import no.rutebanken.anshar.subscription.SiriDataType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.w3c.dom.Node;

import java.util.List;

import static no.rutebanken.anshar.routes.validation.validators.Constants.AFFECTED_ROUTE;


/**
 * Verifies that the value for field AffectedRoute is built up correctly
 *
 */
@Validator(profileName = "norway", targetType = SiriDataType.SITUATION_EXCHANGE)
@Component
public class AffectedRouteValidator extends NsrGenericIdValidator {

    private String path;

    @Autowired
    private AffectedStopPointValidator stopPointValidator;

    public AffectedRouteValidator() {
        FIELDNAME = "RouteRef";
        ID_PATTERN = "Route";
        path = AFFECTED_ROUTE;
    }

    @Override
    public String getCategoryName() {
        return "AffectedRoute";
    }

    @Override
    public ValidationEvent isValid(Node node) {
        String routeRef = getChildNodeValue(node, FIELDNAME);
        if (routeRef != null) {
            if (!super.isValidGenericId(ID_PATTERN, routeRef))  {
                return createEvent(node, FIELDNAME, "Valid reference to route", routeRef, ValidationEvent.FATAL_ERROR);
            }
        }
        ProfileValidationEventOrList eventList = new ProfileValidationEventOrList();
        Node stopPoints = getChildNodeByName(node, "StopPoints");
        if (stopPoints != null) {
            List<Node> affectedStopPoints = getChildNodesByName(stopPoints, "AffectedStopPoint");
            for (Node affectedStopPoint : affectedStopPoints) {
                ValidationEvent validationEvent = stopPointValidator.isValid(getChildNodeByName(affectedStopPoint,"StopPointRef"));
                if (validationEvent != null) {
                    eventList.addEvent(validationEvent);
                }
            }
        }
        if (eventList.getEvents().isEmpty()) {
            return null;
        }
        return eventList;
    }

    @Override
    public String getXpath() {
        return path;
    }
}
