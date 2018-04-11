package no.rutebanken.anshar.routes.validation.validators;

import no.rutebanken.anshar.subscription.SubscriptionSetup;
import org.springframework.stereotype.Component;
import org.w3c.dom.Node;

import javax.xml.bind.ValidationEvent;

import static no.rutebanken.anshar.routes.validation.validators.Constants.PT_SITUATION_ELEMENT;

@Validator(type = SubscriptionSetup.SubscriptionType.SITUATION_EXCHANGE)
@Component
public class SituationNumberValidator extends CustomValidator {

    private static final String FIELDNAME = "Summary";
    private static final String path = PT_SITUATION_ELEMENT + FIELDNAME;

    @Override
    public String getXpath() {
        return path;
    }

    @Override
    public ValidationEvent isValid(Node node) {
        String nodeValue = getNodeValue(node);

        if (nodeValue == null || nodeValue.isEmpty()) {
            return createEvent(node, FIELDNAME, " not empty", nodeValue);
        }

        return null;
    }
}
