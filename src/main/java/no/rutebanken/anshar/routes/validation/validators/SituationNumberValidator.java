package no.rutebanken.anshar.routes.validation.validators;

import no.rutebanken.anshar.subscription.SiriDataType;
import org.springframework.stereotype.Component;
import org.w3c.dom.Node;

import javax.xml.bind.ValidationEvent;

import static no.rutebanken.anshar.routes.validation.validators.Constants.PT_SITUATION_ELEMENT;

@Validator(profileName = "norway", targetType = SiriDataType.SITUATION_EXCHANGE)
@Component
public class SituationNumberValidator extends CustomValidator {

    private static final String FIELDNAME = "SituationNumber";
    private static final String path = PT_SITUATION_ELEMENT + FIELDNAME;

    @Override
    public String getXpath() {
        return path;
    }

    @Override
    public ValidationEvent isValid(Node node) {
        String nodeValue = getNodeValue(node);

        if (nodeValue != null && !nodeValue.contains(":SituationNumber:")) {
            return createEvent(node, FIELDNAME, "CODESPACE:SituationNumber:ID", nodeValue);
        }

        return null;
    }
}
