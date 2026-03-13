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

package no.rutebanken.anshar.validation.fm;

import jakarta.xml.bind.ValidationEvent;
import no.rutebanken.anshar.routes.validation.validators.fm.FmValidityPeriodValidator;
import no.rutebanken.anshar.validation.CustomValidatorTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Node;

import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

public class FmValidityPeriodValidatorTest extends CustomValidatorTest {


    private static FmValidityPeriodValidator validator;

    @BeforeAll
    public static void init() {
        validator = new FmValidityPeriodValidator();
    }

    @Test
    public void testOpenEndedValidityPeriod() throws Exception {
        String xml = "<ValidityPeriod><StartTime>" + ZonedDateTime.now() + "</StartTime></ValidityPeriod>";

        assertNull(
            validator.isValid(createXmlNode(xml)),
            "Open ended ValidityPeriod reported as invalid"
        );
    }

    @Test
    public void testEmptyStartTime() throws Exception {
        String xml = "<ValidityPeriod><StartTime></StartTime></ValidityPeriod>";

        assertNotNull(
            validator.isValid(createXmlNode(xml)),
            "Open ended ValidityPeriod reported as valid"
        );
    }
    @Test
    public void testExpiredValidity() throws Exception {
        String xml = "<ValidityPeriod><StartTime>"+ ZonedDateTime.now().minusHours(3) + "</StartTime><EndTime>" + ZonedDateTime.now().minusHours(2) + "</EndTime></ValidityPeriod>";

        final Node siriNode = createXmlNode(xml);

        final ValidationEvent valid = validator.isValid(siriNode);
        assertNotNull(
            valid,
            "Expired ValidityPeriod reported as valid"
        );
    }

}
