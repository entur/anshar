package no.rutebanken.anshar.routes.validation.validators;

import com.google.common.collect.Sets;
import no.rutebanken.anshar.subscription.SiriDataType;
import org.springframework.stereotype.Component;
import org.w3c.dom.Node;
import uk.org.siri.siri20.WorkflowStatusEnumeration;

import javax.xml.bind.ValidationEvent;
import java.util.Set;

import static no.rutebanken.anshar.routes.validation.validators.Constants.PT_SITUATION_ELEMENT;

@Validator(profileName = "norway", targetType = SiriDataType.SITUATION_EXCHANGE)
@Component
public class ProgressValidator extends CustomValidator {


    private static final String FIELDNAME = "Progress";
    private static final String path = PT_SITUATION_ELEMENT + "/" + FIELDNAME;

    static Set<String> expectedValues = Sets.newHashSet(WorkflowStatusEnumeration.OPEN.value(), WorkflowStatusEnumeration.CLOSED.value());

    @Override
    public String getXpath() {
        return path;
    }

    @Override
    public ValidationEvent isValid(Node node) {
        String nodeValue = getNodeValue(node);

        if (nodeValue != null && !expectedValues.contains(nodeValue)) {
            return  createEvent(node, FIELDNAME, expectedValues, nodeValue);
        }

        return null;
    }

}
