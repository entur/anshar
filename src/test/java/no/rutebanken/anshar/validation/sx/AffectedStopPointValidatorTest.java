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

import no.rutebanken.anshar.routes.validation.validators.sx.AffectedStopPointValidator;
import no.rutebanken.anshar.validation.CustomValidatorTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import javax.xml.bind.ValidationEvent;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

public class AffectedStopPointValidatorTest extends CustomValidatorTest {

    @Autowired
    private AffectedStopPointValidator validator;
    private final String fieldName = "StopPointRef";

    @BeforeEach
    public void init() {
        validator.prepareTestData("NSR:Quay:1234");
        validator.prepareTestData("NSR:StopPlace:1234");
    }

    @Test
    public void testEmptyStopPointRef() throws Exception{
        String xml = createXml(fieldName, "");

        assertNotNull(
            validator.isValid(createXmlNode(xml)),
            "Empty "+fieldName+ " flagged as valid"
        );
    }

    @Test
    public void testQuayStopPointRef() throws Exception{
        String xml = createXml(fieldName, "NSR:Quay:1234");

        final ValidationEvent valid = validator.isValid(createXmlNode(xml));
        assertNull(valid, "Valid "+fieldName+" flagged as invalid");
    }

    @Test
    public void testStopPlaceStopPointRef() throws Exception{
        String xml = createXml(fieldName, "NSR:StopPlace:1234");

        final ValidationEvent valid = validator.isValid(createXmlNode(xml));
        assertNull(valid, fieldName+" with StopPlace flagged as invalid");
    }

    @Test
    public void testRandomStopPointRef() throws Exception{
        String xml = createXml(fieldName, "001234111");

        final ValidationEvent valid = validator.isValid(createXmlNode(xml));
        assertNotNull(valid, fieldName+" with StopPlace flagged as invalid");
    }
}
