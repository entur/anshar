package no.rutebanken.anshar.routes.validation.validators;

import no.rutebanken.anshar.subscription.SiriDataType;
import org.springframework.stereotype.Component;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.bind.ValidationEvent;
import java.time.ZonedDateTime;

import static no.rutebanken.anshar.routes.validation.validators.Constants.PT_SITUATION_ELEMENT;

@Validator(profileName = "norway", targetType = SiriDataType.SITUATION_EXCHANGE)
@Component
public class ValidityPeriodValidator extends CustomValidator {


    private static final String FIELDNAME = "ValidityPeriod";
    private static final String path = PT_SITUATION_ELEMENT + FIELDNAME;


    @Override
    public String getXpath() {
        return path;
    }

    @Override
    public ValidationEvent isValid(Node node) {

        if (node == null || node.getChildNodes().getLength() == 0) {
            return  createEvent(node, FIELDNAME, "not null", null);
        }

        boolean toDateRequired = false;
        final Node parentNode = node.getParentNode();
        if (parentNode != null) {
            final NodeList childNodes = parentNode.getChildNodes();
            for (int i = 0; i < childNodes.getLength(); i++) {
                final Node n = childNodes.item(i);
                if (n.getNodeName().equals("Progress")) {
                    final String progressNodeValue = getNodeValue(n);
                    if (progressNodeValue != null && progressNodeValue.equals("closed")) {
                        toDateRequired = true;
                    }
                }
            }
        }

        NodeList childNodes = node.getChildNodes();

        for (int i = 0; i < childNodes.getLength(); i++) {
            final Node item = childNodes.item(i);
            if (item != null && item.getNodeName() != null) {
                if (item.getNodeName().equals("StartTime")) {
                    final String timeValue = getNodeValue(item);
                    if (timeValue != null || !timeValue.isEmpty()) {
                        final ZonedDateTime parsedValue = ZonedDateTime.parse(timeValue);
                        if (parsedValue == null) {
                            return createEvent(node, FIELDNAME, "valid date", timeValue);
                        }
                    }
                } else if (toDateRequired && item.getNodeName().equals("EndTime")) {
                    final String timeValue = getNodeValue(item);
                    if (timeValue != null || !timeValue.isEmpty()) {
                        final ZonedDateTime parsedValue = ZonedDateTime.parse(timeValue);
                        if (parsedValue == null) {
                            return createEvent(node, FIELDNAME, "valid date", timeValue);
                        }
                    }
                } else if (item.getNodeName().equals("EndTime")) {
                    final String timeValue = getNodeValue(item);
                    if (timeValue != null || !timeValue.isEmpty()) {
                        final ZonedDateTime parsedValue = ZonedDateTime.parse(timeValue);
                        if (parsedValue != null && parsedValue.isBefore(ZonedDateTime.now())) {
                            return createEvent(node, FIELDNAME, "date after 'now'", timeValue);
                        }
                    }
                }
            }
        }

        return null;
    }

}
