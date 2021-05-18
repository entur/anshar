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

import no.rutebanken.anshar.routes.validation.validators.vm.MonitoredOperatorRefValidator;
import no.rutebanken.anshar.validation.CustomValidatorTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

public class MonitoredOperatorRefValidatorTest extends CustomValidatorTest {

    private static MonitoredOperatorRefValidator validator;
    private final String fieldName = "OperatorRef";

    @BeforeAll
    public static void init() {
        validator = new MonitoredOperatorRefValidator();
    }

    @Test
    public void testValidOperator() {
        assertNull(validator.isValid(createXmlNode(fieldName, "ENT:Operator:123")), "Valid "+fieldName+" flagged as invalid");
    }

    @Test
    public void testInvalidOperatorRef() {
        assertNotNull(validator.isValid(createXmlNode(fieldName, "ENT")), "Invalid "+fieldName+" flagged as valid");
    }
}