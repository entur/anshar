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

package no.rutebanken.anshar.validation;

import no.rutebanken.anshar.routes.validation.validators.SituationNumberValidator;
import org.junit.BeforeClass;
import org.junit.Test;

import javax.xml.bind.ValidationEvent;

import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertNull;

public class SituationNumberValidatorTest extends CustomValidatorTest {

    static SituationNumberValidator validator;

    @BeforeClass
    public static void init() {
        validator = new SituationNumberValidator();
    }

    @Test
    public void testNumberedSituationNumber() throws Exception{
        String xml = createXml("SituationNumber", "1234");

        assertNotNull("Valid SituationNumber flagged as invalid", validator.isValid(createXmlNode(xml)));
    }

    @Test
    public void testCombinedSituationNumber() throws Exception{
        String xml = createXml("SituationNumber", "status-12344321");

        assertNotNull("Valid SituationNumber flagged as invalid", validator.isValid(createXmlNode(xml)));
    }

    @Test
    public void testValieSituationNumber() throws Exception{
        String xml = createXml("SituationNumber", "ENT:SituationNumber:1234");

        final ValidationEvent valid = validator.isValid(createXmlNode(xml));
        assertNull("Invalid SituationNumber flagged as valid", valid);
    }
}
