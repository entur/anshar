package no.rutebanken.anshar.validation.et;

import no.rutebanken.anshar.routes.validation.validators.et.UpdateReceivedTooSoonValidator;
import no.rutebanken.anshar.validation.CustomValidatorTest;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

public class UpdateReceivedTooSoonValidatorTest extends CustomValidatorTest {

    UpdateReceivedTooSoonValidator validator = new UpdateReceivedTooSoonValidator();

    @Test
    public void testUpdateReceivedTooSoon() {
        assertNotNull(validator.isValid(createXmlNode(getUpdate(8))));
        assertNotNull(validator.isValid(createXmlNode(getUpdate(30))));
        assertNull(validator.isValid(createXmlNode(getUpdate(0))));
        assertNull(validator.isValid(createXmlNode(getUpdate(7))));
    }

    private String getUpdate(int daysAhead) {
        String xml =  "<EstimatedCalls><EstimatedCall>" +
            "            <AimedArrivalTime>" + ZonedDateTime.now().plusDays(daysAhead) + "</AimedArrivalTime>" +
            "        </EstimatedCall></EstimatedCalls>";
            return xml;
    }
}
