package no.rutebanken.anshar.routes.validation.validators;

import no.rutebanken.anshar.subscription.SubscriptionSetup;
import org.springframework.stereotype.Component;
import org.w3c.dom.Node;

import javax.xml.bind.ValidationEvent;

import static no.rutebanken.anshar.routes.validation.validators.Constants.ESTIMATED_CALL;

@Validator(type = SubscriptionSetup.SubscriptionType.ESTIMATED_TIMETABLE)
@Component
public class StopPointRefValidator extends CustomValidator {


    private static final String FIELDNAME = "StopPointRef";
    private static final String path = ESTIMATED_CALL + FIELDNAME;

    @Override
    public String getXpath() {
        return path;
    }

    @Override
    public ValidationEvent isValid(Node node) {
        String nodeValue = getNodeValue(node);

        if (!nodeValue.startsWith("NSR:Quay:")) {
            return  createEvent(node, FIELDNAME, "NSR:Quay:ID", nodeValue);
        }

        return null;
    }

}
