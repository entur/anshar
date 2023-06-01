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

package no.rutebanken.anshar.validation.vm;

import no.rutebanken.anshar.routes.validation.validators.vm.MonitoredOccupancyValidator;
import no.rutebanken.anshar.validation.CustomValidatorTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import uk.org.siri.siri21.OccupancyEnumeration;

import javax.xml.bind.ValidationEvent;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MonitoredOccupancyValidatorTest extends CustomValidatorTest {

    private static MonitoredOccupancyValidator validator;
    private final String fieldName = "Occupancy";

    @BeforeAll
    public static void init() {
        validator = new MonitoredOccupancyValidator();
    }

    @Test
    public void testValidOccupancy() throws Exception {
        assertNull(validator.isValid(createXmlNode(fieldName, OccupancyEnumeration.UNKNOWN.value())));
        assertNull(validator.isValid(createXmlNode(fieldName, OccupancyEnumeration.MANY_SEATS_AVAILABLE.value())));
        assertNull(validator.isValid(createXmlNode(fieldName, OccupancyEnumeration.SEATS_AVAILABLE.value())));
        assertNull(validator.isValid(createXmlNode(fieldName, OccupancyEnumeration.STANDING_AVAILABLE.value())));
        assertNull(validator.isValid(createXmlNode(fieldName, OccupancyEnumeration.FULL.value())));
        assertNull(validator.isValid(createXmlNode(fieldName, OccupancyEnumeration.NOT_ACCEPTING_PASSENGERS.value())));
    }

    @Test
    public void testRandomValueOccupancy() throws Exception {
        ValidationEvent valid = validator.isValid(createXmlNode(fieldName, "testing"));
        assertNotNull(valid);
        assertTrue(valid.getMessage().contains(fieldName));
    }
}
