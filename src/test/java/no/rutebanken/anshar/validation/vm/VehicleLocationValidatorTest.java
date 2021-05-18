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

import no.rutebanken.anshar.routes.validation.validators.vm.VehicleLocationValidator;
import no.rutebanken.anshar.validation.CustomValidatorTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.xml.bind.ValidationEvent;

import static org.junit.jupiter.api.Assertions.*;

public class VehicleLocationValidatorTest extends CustomValidatorTest {

    private static VehicleLocationValidator validator;
    private String fieldName = "VehicleLocation";
    private final String srsAttributeFieldName = "srsName";
    private final String latFieldName = "Latitude";
    private final String lonFieldName = "Longitude";

    @BeforeAll
    public static void init() {
        validator = new VehicleLocationValidator();
    }

    @Test
    public void testValidLocation() throws Exception {

        assertNull(validator.isValid(createXmlNode(createLocationXml("EPSG:4326", 10.0, 60.0))));
        assertNull(validator.isValid(createXmlNode(createLocationXml("WGS84", 10.0, 60.0))));
        assertNull(validator.isValid(createXmlNode(createLocationXml("EPSG:4326", -180.0, -90.0))));
        assertNull(validator.isValid(createXmlNode(createLocationXml("EPSG:4326", 180.0, 90.0))));
        assertNull(validator.isValid(createXmlNode(createLocationXml(null, 10.0, 60.0))));
    }


    @Test
    public void testLongitudeOutOfRange() throws Exception {
        String xml = createLocationXml(null, 181.0, 10.0);
        ValidationEvent valid = validator.isValid(createXmlNode(xml));
        assertNotNull(valid);
        assertTrue(valid.getMessage().contains(lonFieldName));

        xml = createLocationXml(null, -181.0, 10.0);
        valid = validator.isValid(createXmlNode(xml));
        assertNotNull(valid);
        assertTrue(valid.getMessage().contains(lonFieldName));
    }

    @Test
    public void testLatitudeOutOfRange() throws Exception {
        String xml = createLocationXml(null, 10.0, 91.0);
        ValidationEvent valid = validator.isValid(createXmlNode(xml));
        assertNotNull(valid);
        assertTrue(valid.getMessage().contains(latFieldName));

        xml = createLocationXml(null, 10.0, -91.0);
        valid = validator.isValid(createXmlNode(xml));
        assertNotNull(valid);
        assertTrue(valid.getMessage().contains(latFieldName));
    }

    @Test
    public void testInvalidSrsName() throws Exception {
        String xml = createLocationXml("EPSG:32633", 10.0, 59.0);

        ValidationEvent valid = validator.isValid(createXmlNode(xml));

        assertNotNull(valid);
        assertNotNull(valid.getMessage());
        assertTrue(valid.getMessage().contains(srsAttributeFieldName));
    }

    private String createLocationXml(String srsName, Double longitude, Double latitude) {
        StringBuilder xml = new StringBuilder();
        xml.append("<Location ");
        if (srsName != null) {
            xml.append(srsAttributeFieldName).append("=\"").append(srsName).append("\"");
        }
        xml.append(">");

        if (longitude != null) {
            xml.append("    <Longitude>").append(longitude).append("</Longitude>");
        }

        if (latitude != null) {
            xml.append("    <Latitude>").append(latitude).append("</Latitude>");
        }

        xml.append("</Location>");

        return xml.toString();
    }

}
