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
