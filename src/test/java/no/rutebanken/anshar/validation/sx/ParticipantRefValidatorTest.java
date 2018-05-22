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

import no.rutebanken.anshar.routes.validation.validators.sx.ParticipantRefValidator;
import no.rutebanken.anshar.validation.CustomValidatorTest;
import org.junit.BeforeClass;
import org.junit.Test;

import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertNull;

public class ParticipantRefValidatorTest extends CustomValidatorTest {

    private static ParticipantRefValidator validator;
    private final String fieldName = "ParticipantRef";

    @BeforeClass
    public static void init() {
        validator = new ParticipantRefValidator();
    }

    @Test
    public void testValidParticipant() throws Exception{
        String xml = createXml(fieldName, "ENT");

        assertNull("Valid "+fieldName+" flagged as invalid", validator.isValid(createXmlNode(xml)));
    }

    @Test
    public void testInvalidParticipant() throws Exception{
        String xml = createXml(fieldName, "N");

        assertNotNull("Invalid "+fieldName+" flagged as valid", validator.isValid(createXmlNode(xml)));
    }
}
