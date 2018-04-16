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

import no.rutebanken.anshar.routes.validation.validators.ValidityPeriodValidator;
import org.junit.BeforeClass;
import org.junit.Test;
import org.w3c.dom.Node;

import javax.xml.bind.ValidationEvent;
import java.time.ZonedDateTime;

import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertNull;

public class ValidityPeriodValidatorTest extends CustomValidatorTest{


    static ValidityPeriodValidator validator;

    @BeforeClass
    public static void init() {
        validator = new ValidityPeriodValidator();
    }

    @Test
    public void testOpenEndedValidityPeriod() throws Exception {
        String xml = "<ValidityPeriod><StartTime>" + ZonedDateTime.now() + "</StartTime></ValidityPeriod>";

        assertNull("Open ended ValidityPeriod reported as invalid", validator.isValid(createXmlNode(xml)));
    }

    @Test
    public void testEmptyStartTime() throws Exception {
        String xml = "<ValidityPeriod><StartTime></StartTime></ValidityPeriod>";

        assertNotNull("Open ended ValidityPeriod reported as valid", validator.isValid(createXmlNode(xml)));
    }

    @Test
    public void testProgressNoEndTime() throws Exception {
        String xml = "<PLACEHOLDER><Progress>closed</Progress><ValidityPeriod><StartTime>"+ ZonedDateTime.now() + "</StartTime></ValidityPeriod></PLACEHOLDER>";

        final Node siriNode = createXmlNode(xml);

        assertNotNull("Open ended ValidityPeriod reported as valid when Progress is 'closed'", validator.isValid(siriNode.getLastChild().getLastChild()));
    }

    @Test
    public void testProgressWithTooShortEndTime() throws Exception {
        String xml = "<PLACEHOLDER><Progress>closed</Progress><ValidityPeriod><StartTime>"+ ZonedDateTime.now() + "</StartTime><EndTime>" + ZonedDateTime.now().plusHours(4) + "</EndTime></ValidityPeriod></PLACEHOLDER>";

        final Node siriNode = createXmlNode(xml);

        final ValidationEvent valid = validator.isValid(siriNode.getLastChild());
        assertNotNull("Open ended ValidityPeriod reported as valid when Progress is 'closed'", valid);
    }

    @Test
    public void testProgressWithEndTime() throws Exception {
        String xml = "<PLACEHOLDER><Progress>closed</Progress><ValidityPeriod><StartTime>"+ ZonedDateTime.now() + "</StartTime><EndTime>" + ZonedDateTime.now().plusHours(6) + "</EndTime></ValidityPeriod></PLACEHOLDER>";

        final Node siriNode = createXmlNode(xml);

        assertNull("Open ended ValidityPeriod reported as valid when Progress is 'closed'", validator.isValid(siriNode.getLastChild()));
    }

    @Test
    public void testExpiredValidity() throws Exception {
        String xml = "<ValidityPeriod><StartTime>"+ ZonedDateTime.now().minusHours(3) + "</StartTime><EndTime>" + ZonedDateTime.now().minusHours(2) + "</EndTime></ValidityPeriod>";

        final Node siriNode = createXmlNode(xml);

        final ValidationEvent valid = validator.isValid(siriNode);
        assertNotNull("Open ended ValidityPeriod reported as valid when Progress is 'closed'", valid);
    }

}
