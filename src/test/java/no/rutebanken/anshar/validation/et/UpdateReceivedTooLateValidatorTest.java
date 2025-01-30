package no.rutebanken.anshar.validation.et;

import no.rutebanken.anshar.routes.validation.validators.et.UpdateReceivedTooLateValidator;
import no.rutebanken.anshar.validation.CustomValidatorTest;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

public class UpdateReceivedTooLateValidatorTest extends CustomValidatorTest {

    UpdateReceivedTooLateValidator validator = new UpdateReceivedTooLateValidator();

    @Test
    public void testUpdateReceivedTooLate() {
        assertNotNull(validator.isValid(createXmlNode(getUpdate(21))));
        assertNotNull(validator.isValid(createXmlNode(getUpdate(45))));
        assertNull(validator.isValid(createXmlNode(getUpdate(-10))));
        assertNull(validator.isValid(createXmlNode(getUpdate(0))));
        assertNull(validator.isValid(createXmlNode(getUpdate(19))));
    }

    private String getUpdate(int minutesBefore) {
        String xml =  "<EstimatedCalls><EstimatedCall>" +
            "            <AimedArrivalTime>" + ZonedDateTime.now() + "</AimedArrivalTime>" +
            "            <ExpectedArrivalTime>" + ZonedDateTime.now().minusMinutes(minutesBefore) + "</ExpectedArrivalTime>" +
            "            <AimedDepartureTime>" + ZonedDateTime.now() + "</AimedDepartureTime>" +
            "            <ExpectedDepartureTime>" + ZonedDateTime.now().minusMinutes(minutesBefore) + "</ExpectedDepartureTime>" +
            "        </EstimatedCall></EstimatedCalls>";
            return xml;
    }
}
