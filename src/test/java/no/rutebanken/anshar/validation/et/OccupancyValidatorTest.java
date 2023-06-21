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

import no.rutebanken.anshar.routes.validation.validators.et.OccupancyValidator;
import no.rutebanken.anshar.validation.CustomValidatorTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;

public class OccupancyValidatorTest extends CustomValidatorTest {

    private static OccupancyValidator validator;
    private static final String fieldName = "Occupancy";

    @BeforeAll
    public static void init() {
        validator = new OccupancyValidator();
    }

    @Test
    public void testOccupancyFull() throws Exception{
        String xml = createXml(fieldName, "full");

        assertNull(validator.isValid(createXmlNode(xml)), "Valid " + fieldName + " flagged as invalid");
    }
    @Test
    public void testOccupancySeatsAvailable() throws Exception{
        String xml = createXml(fieldName, "seatsAvailable");

        assertNull(validator.isValid(createXmlNode(xml)), "Valid " + fieldName + " flagged as invalid");
    }
    @Test
    public void testOccupancyStandingAvailable() throws Exception{
        String xml = createXml(fieldName, "standingAvailable");

        assertNull(validator.isValid(createXmlNode(xml)), "Valid " + fieldName + " flagged as invalid");
    }
    @Test
    public void testOccupancyManySeatsAvailable() throws Exception{
        String xml = createXml(fieldName, "manySeatsAvailable");

        assertNull(validator.isValid(createXmlNode(xml)), "Valid " + fieldName + " flagged as invalid");
    }
    @Test
    public void testOccupancyNotAcceptingPassengers() throws Exception{
        String xml = createXml(fieldName, "notAcceptingPassengers");

        assertNull(validator.isValid(createXmlNode(xml)), "Valid " + fieldName + " flagged as invalid");
    }

    @Test
    public void testOccupancyUnknown() throws Exception{
        String xml = createXml(fieldName, "unknown");

        assertNull(validator.isValid(createXmlNode(xml)), "Valid " + fieldName + " flagged as invalid");
    }


}
