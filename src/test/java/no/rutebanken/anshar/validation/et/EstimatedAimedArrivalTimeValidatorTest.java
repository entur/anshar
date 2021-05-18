/*
 * Licensed under the EUPL, Version 1.2 or – as soon they will be approved by
 * the European Commission - subsequent versions of the EUPL (the "Licence");
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 *
 *   https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and
 * limitations under the Licence.
 */

package no.rutebanken.anshar.validation.et;

import no.rutebanken.anshar.routes.validation.validators.et.EstimatedAimedArrivalTimeValidator;
import no.rutebanken.anshar.validation.CustomValidatorTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.xml.bind.ValidationEvent;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

public class EstimatedAimedArrivalTimeValidatorTest extends CustomValidatorTest {

    private static EstimatedAimedArrivalTimeValidator validator;
    private final String fieldName = "AimedArrivalTime";
    private final String comparisonField = "AimedDepartureTime";

    @BeforeAll
    public static void init() {
        validator = new EstimatedAimedArrivalTimeValidator();
    }

    @Test
    public void testAimedArrivalOnly() throws Exception{
        String xml = createXml(fieldName, "2018-04-16T10:00:00+02:00");

        assertNull(validator.isValid(createXmlNode(xml).getFirstChild()), "Valid "+fieldName+" flagged as invalid");
    }


    @Test
    public void testAimedArrivalAndAimedDepartureEqual() throws Exception{
        String arrival = createXml(fieldName, "2018-04-16T10:00:00+02:00");
        String departure = createXml(comparisonField, "2018-04-16T10:00:00+02:00");

        String xml = "<PLACEHOLDER>" + arrival + departure + "</PLACEHOLDER>";

        assertNull(validator.isValid(createXmlNode(xml).getFirstChild()), "Valid "+fieldName+" flagged as invalid");
    }

    @Test
    public void testAimedArrivalBeforeAimedDeparture() throws Exception{
        String arrival = createXml(fieldName, "2018-04-16T10:00:00+02:00");
        String departure = createXml(comparisonField, "2018-04-16T10:02:00+02:00");

        String xml = "<PLACEHOLDER>" + arrival + departure + "</PLACEHOLDER>";

        assertNull(validator.isValid(createXmlNode(xml).getFirstChild()), "Valid "+fieldName+" flagged as invalid");
    }

    @Test
    public void testAimedArrivalAfterAimedDeparture() throws Exception{
        String arrival = createXml(fieldName, "2018-04-16T10:02:00+02:00");
        String departure = createXml(comparisonField, "2018-04-16T10:00:00+02:00");

        String xml = "<dummy>" + arrival + departure + "</dummy>";

        final ValidationEvent valid = validator.isValid(createXmlNode(xml).getFirstChild());
        assertNotNull(valid, "Invalid "+fieldName+" flagged as valid");
    }

    @Test
    public void testAimedArrivalAfterAimedDepartureWithCancellation() throws Exception{
        String arrival = createXml(fieldName, "2018-04-16T10:02:00+02:00");
        String departure = createXml(comparisonField, "2018-04-16T10:00:00+02:00");
        String departureStatus = createXml("DepartureStatus", "cancelled");

        String xml = "<dummy>" + arrival + departure + departureStatus + "</dummy>";

        final ValidationEvent valid = validator.isValid(createXmlNode(xml).getFirstChild());
        assertNull(valid, "Cancelled departure flagged as invalid");
    }
}
