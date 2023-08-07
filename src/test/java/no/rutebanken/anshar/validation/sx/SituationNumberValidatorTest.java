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

package no.rutebanken.anshar.validation.sx;

import jakarta.xml.bind.ValidationEvent;
import no.rutebanken.anshar.routes.validation.validators.sx.SituationNumberValidator;
import no.rutebanken.anshar.validation.CustomValidatorTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

public class SituationNumberValidatorTest extends CustomValidatorTest {

    private static SituationNumberValidator validator;
    private final String fieldName = "SituationNumber";

    @BeforeAll
    public static void init() {
        validator = new SituationNumberValidator();
    }

    @Test
    public void testNumberedSituationNumber() throws Exception{
        String xml = createXml(fieldName, "1234");

        assertNotNull(
            validator.isValid(createXmlNode(xml)),
            "Valid "+fieldName+" flagged as invalid"
        );
    }

    @Test
    public void testCombinedSituationNumber() throws Exception{
        String xml = createXml(fieldName, "status-12344321");

        assertNotNull(
            validator.isValid(createXmlNode(xml)),
            "Valid "+fieldName+" flagged as invalid"
        );
    }

    @Test
    public void testValieSituationNumber() throws Exception{
        String xml = createXml(fieldName, "ENT:SituationNumber:1234");

        final ValidationEvent valid = validator.isValid(createXmlNode(xml));
        assertNull(
            valid,
            "Invalid "+fieldName+" flagged as valid"
        );
    }
}
