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

import jakarta.xml.bind.ValidationEvent;
import no.rutebanken.anshar.routes.validation.validators.vm.VehicleStatusValidator;
import no.rutebanken.anshar.validation.CustomValidatorTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import uk.org.siri.siri21.VehicleStatusEnumeration;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class VehicleStatusValidatorTest extends CustomValidatorTest {

    private static VehicleStatusValidator validator;
    private final String fieldName = "VehicleStatus";

    @BeforeAll
    public static void init() {
        validator = new VehicleStatusValidator();
    }


    @Test
    public void testValidVehicleStatus() throws Exception {
        assertNull(validator.isValid(createXmlNode(fieldName, VehicleStatusEnumeration.ASSIGNED.value())));
        assertNull(validator.isValid(createXmlNode(fieldName, VehicleStatusEnumeration.AT_ORIGIN.value())));
        assertNull(validator.isValid(createXmlNode(fieldName, VehicleStatusEnumeration.CANCELLED.value())));
        assertNull(validator.isValid(createXmlNode(fieldName, VehicleStatusEnumeration.COMPLETED.value())));
        assertNull(validator.isValid(createXmlNode(fieldName, VehicleStatusEnumeration.IN_PROGRESS.value())));
        assertNull(validator.isValid(createXmlNode(fieldName, VehicleStatusEnumeration.OFF_ROUTE.value())));
    }

    @Test
    public void testInvalidVehicleStatus() throws Exception {
        ValidationEvent valid = validator.isValid(createXmlNode(fieldName, VehicleStatusEnumeration.ABORTED.value()));
        assertNotNull(valid);
        assertTrue(valid.getMessage().contains(fieldName));

        valid = validator.isValid(createXmlNode(fieldName, VehicleStatusEnumeration.ASSUMED_COMPLETED.value()));
        assertNotNull(valid);
        assertTrue(valid.getMessage().contains(fieldName));
    }
}
