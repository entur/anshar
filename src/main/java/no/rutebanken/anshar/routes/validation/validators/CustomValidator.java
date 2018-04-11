package no.rutebanken.anshar.routes.validation.validators;

import org.w3c.dom.Node;

import javax.xml.bind.ValidationEvent;
import javax.xml.bind.helpers.ValidationEventImpl;
import javax.xml.bind.helpers.ValidationEventLocatorImpl;
import java.text.MessageFormat;

public abstract class CustomValidator {

    public abstract String getXpath();
    public abstract ValidationEvent isValid(Node node);

    /**
     * Returns the textual content of the provided node - null if it does not exist
     * @param node
     * @return
     */
    protected String getNodeValue(Node node) {
        if (node != null && node.getFirstChild() != null && node.getFirstChild().getNodeValue() != null) {
            return node.getFirstChild().getNodeValue();
        }
        return null;
    }

    /**
     *
     * @param node Node that is validated
     * @param fieldname Name of attribute that fails validation
     * @param expectedValues Expected value or description of expected value
     * @param actualValue Actual value of node
     * @return
     */
    protected ValidationEvent createEvent(Node node, String fieldname, Object expectedValues, String actualValue) {
        String message = MessageFormat.format("Value [{0}] is invalid for field [{1}], expected {2}", actualValue, fieldname, expectedValues);
        return new ValidationEventImpl(ValidationEvent.WARNING, message, new ValidationEventLocatorImpl(node));
    }
}
