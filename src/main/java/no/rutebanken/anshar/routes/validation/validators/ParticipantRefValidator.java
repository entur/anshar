package no.rutebanken.anshar.routes.validation.validators;

import no.rutebanken.anshar.subscription.SiriDataType;
import org.springframework.stereotype.Component;
import org.w3c.dom.Node;

import javax.xml.bind.ValidationEvent;

import static no.rutebanken.anshar.routes.validation.validators.Constants.PT_SITUATION_ELEMENT;

@Validator(profileName = "norway", targetType = SiriDataType.SITUATION_EXCHANGE)
@Component
public class ParticipantRefValidator extends CustomValidator {

    private static final String FIELDNAME = "ParticipantRef";
    private static final String path = PT_SITUATION_ELEMENT + FIELDNAME;

    @Override
    public String getXpath() {
        return path;
    }

    @Override
    public ValidationEvent isValid(Node node) {
        String nodeValue = getNodeValue(node);

        if (nodeValue != null && nodeValue.length() == 3) {
            //TODO: Check for valid CodeSpace
            return createEvent(node, FIELDNAME, "CODESPACE", nodeValue);
        }

        return null;
    }
}
