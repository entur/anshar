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
import org.w3c.dom.Node;

import java.util.ArrayList;
import java.util.List;

public abstract class CallRequiredFieldsValidator extends CustomValidator {
    List<String> validateCommonFields(Node node) {

        List<String> missingFields = new ArrayList<>();

        if (getChildNodeByName(node, "StopPointRef") == null) {
            missingFields.add("StopPointRef");
        }

        if (getChildNodeByName(node, "Order") == null) {
            missingFields.add("Order");
        }

        if (getChildNodeByName(node, "AimedArrivalTime") == null &&getChildNodeByName(node, "AimedDepartureTime") == null) {
            missingFields.add("AimedArrivalTime/AimedDepartureTime");
        }
        return missingFields;
    }
}
