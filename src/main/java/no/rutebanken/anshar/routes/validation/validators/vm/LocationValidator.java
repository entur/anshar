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

package no.rutebanken.anshar.routes.validation.validators.vm;

import com.google.common.collect.Sets;
import no.rutebanken.anshar.routes.validation.validators.CustomValidator;
import org.w3c.dom.Node;

import javax.xml.bind.ValidationEvent;
import java.util.Set;

import static no.rutebanken.anshar.routes.validation.validators.Constants.MONITORED_VEHICLE_JOURNEY;

public class LocationValidator extends CustomValidator {

    private static final String FIELDNAME = "Location";
    private static final String srsNameAttributeNAme = "srsName";

    private static final String LAT_FIELDNAME = "Latitude";
    private static final String LON_FIELDNAME = "Longitude";

    private static final String COORDINATES_FIELDNAME = "Coordinates";
    private static final String path =  MONITORED_VEHICLE_JOURNEY + "/" + FIELDNAME;

    private static final Set<String> expectedValues = Sets.newHashSet("WGS84", "EPSG:4326");

    @Override
    public String getXpath() {
        return path;
    }

    @Override
    public ValidationEvent isValid(Node node) {
        String srsNameAttribute = getNodeAttributeValue(node, srsNameAttributeNAme);

        if (srsNameAttribute != null && !srsNameAttribute.isEmpty()) {
            if (!expectedValues.contains(srsNameAttribute)) {
                return  createEvent(node, srsNameAttributeNAme, "one of " + expectedValues, srsNameAttribute, ValidationEvent.ERROR);
            }
        }

        String longitude = getChildNodeValue(node, LON_FIELDNAME);
        String latitude = getChildNodeValue(node, LAT_FIELDNAME);
        String coordinates = getChildNodeValue(node, COORDINATES_FIELDNAME);

        if (longitude != null && latitude != null) {
            Double lon = Double.parseDouble(longitude);
            if (lon > 180 | lon < -180) {
                return  createEvent(node, LON_FIELDNAME, "Valid longitude", longitude, ValidationEvent.FATAL_ERROR);
            }
            Double lat = Double.parseDouble(latitude);
            if (lat > 90 | lat < -90) {
                return  createEvent(node, LAT_FIELDNAME, "Valid latitude", latitude, ValidationEvent.FATAL_ERROR);
            }

        } else if (coordinates != null) {
            //TODO: Validate GML
        } else {
            return  createEvent(node, FIELDNAME, "Valid location", null, ValidationEvent.FATAL_ERROR);
        }



        return null;
    }
}
