package no.rutebanken.anshar.routes.validation.validators;

import no.rutebanken.anshar.subscription.SiriDataType;
import org.springframework.stereotype.Component;
import org.w3c.dom.Node;

import javax.xml.bind.ValidationEvent;
import java.util.List;

import static no.rutebanken.anshar.routes.validation.validators.Constants.PT_SITUATION_ELEMENT;

@Validator(profileName = "norway", targetType = SiriDataType.SITUATION_EXCHANGE)
@Component
public class SummaryValidator extends CustomValidator {

    private static final String FIELDNAME = "Summary";
    private static final String path = PT_SITUATION_ELEMENT;

    @Override
    public String getXpath() {
        return path;
    }

    @Override
    public ValidationEvent isValid(Node node) {

        final List<Node> childNodesByName = getChildNodesByName(node, FIELDNAME);

        boolean requireLangAttribute = (childNodesByName.size() > 1);

        if (childNodesByName.isEmpty()) {
            return createEvent(node, FIELDNAME, "not empty", null, ValidationEvent.FATAL_ERROR);
        }

        for (Node summaryNode : childNodesByName) {

            String nodeValue = getNodeValue(summaryNode);

            if (nodeValue == null || nodeValue.isEmpty()) {
                return createEvent(summaryNode, FIELDNAME, "not empty", nodeValue, ValidationEvent.FATAL_ERROR);
            }

            if (requireLangAttribute) {
                final String lang = getNodeAttributeValue(summaryNode, "lang");
                if (lang == null || lang.isEmpty()) {
                    return createEvent(summaryNode, FIELDNAME, "lang-attribute when more than one Summary", nodeValue, ValidationEvent.FATAL_ERROR);
                }
            }
        }


        return null;
    }
}
