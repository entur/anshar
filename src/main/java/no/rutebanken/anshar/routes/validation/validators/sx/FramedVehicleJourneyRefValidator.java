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

package no.rutebanken.anshar.routes.validation.validators.sx;

import no.rutebanken.anshar.routes.validation.validators.CustomValidator;
import no.rutebanken.anshar.routes.validation.validators.Validator;
import no.rutebanken.anshar.subscription.SiriDataType;
import org.springframework.stereotype.Component;
import org.w3c.dom.Node;

import javax.xml.bind.ValidationEvent;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;

import static no.rutebanken.anshar.routes.validation.validators.Constants.AFFECTED_VEHICLE_JOURNEY;

/**
 * Verifies the values included in FramedVehicleJourneyRef
 *  - DataFrameRef is a valid date
 *  - DatedVehicleJourneyRef is build up correctly
 *
 */
@Validator(profileName = "norway", targetType = SiriDataType.SITUATION_EXCHANGE)
@Component
public class FramedVehicleJourneyRefValidator extends CustomValidator {

    private static final String FIELDNAME = "FramedVehicleJourneyRef";
    private static final String path = AFFECTED_VEHICLE_JOURNEY + "/" + FIELDNAME;
    private final DateFormat format;
    private final String pattern = "yyyy-MM-dd";

    public FramedVehicleJourneyRefValidator() {
        format = new SimpleDateFormat(pattern);
        format.setLenient(false);
    }

    @Override
    public String getXpath() {
        return path;
    }


    /*
        <FramedVehicleJourneyRef>
            <DataFrameRef>2018-12-31</DataFrameRef>
            <DatedVehicleJourneyRef>CODESPACE:ServiceJourney:ID</DatedVehicleJourneyRef>
        </FramedVehicleJourneyRef>
     */

    @Override
    public ValidationEvent isValid(Node node) {

        String dataFrameRef = getChildNodeValue(node, "DataFrameRef");
        if (dataFrameRef == null) {
            return createEvent(node, "DataFrameRef", "valid date", dataFrameRef, ValidationEvent.FATAL_ERROR);
        } else {
            if (!isValidDate(dataFrameRef)) {
                return createEvent(node, "DataFrameRef", "valid date with pattern " + pattern, dataFrameRef, ValidationEvent.FATAL_ERROR);

            }
        }

        String datedVehicleJourneyRef = getChildNodeValue(node, "DatedVehicleJourneyRef");
        if (!isValidGenericId("ServiceJourney", datedVehicleJourneyRef)) {
            return createEvent(node, "DatedVehicleJourneyRef", "valid ServiceJourney-ID", datedVehicleJourneyRef, ValidationEvent.ERROR);
        }

        return null;
    }

    private boolean isValidDate(String date) {
        try {
            format.parse(date);
        } catch (ParseException e) {
            return false;
        }
        return true;
    }
}
