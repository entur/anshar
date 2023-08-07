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

import jakarta.xml.bind.ValidationEvent;
import no.rutebanken.anshar.routes.validation.validators.CustomValidator;
import no.rutebanken.anshar.routes.validation.validators.ProfileValidationEventOrList;
import no.rutebanken.anshar.routes.validation.validators.Validator;
import no.rutebanken.anshar.subscription.SiriDataType;
import org.springframework.stereotype.Component;
import org.w3c.dom.Node;

import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;

import static no.rutebanken.anshar.routes.validation.validators.Constants.MONITORED_VEHICLE_JOURNEY;

/**
 * Verifies the values included in FramedVehicleJourneyRef
 *  - DataFrameRef is a valid date
 *  - DatedVehicleJourneyRef is build up correctly
 *
 */
@Validator(profileName = "norway", targetType = SiriDataType.VEHICLE_MONITORING)
@Component
public class VmFramedVehicleJourneyRefValidator extends CustomValidator {

    private static final String FIELDNAME = "FramedVehicleJourneyRef";
    private static final String DATA_FRAMEREF_FIELDNAME = "DataFrameRef";
    private String path = MONITORED_VEHICLE_JOURNEY + FIELD_DELIMITER + FIELDNAME;
    private final DateTimeFormatter formatter;
    private static final String PATTERN = "yyyy-MM-dd";

    public VmFramedVehicleJourneyRefValidator() {
        formatter = new DateTimeFormatterBuilder().appendPattern(PATTERN)
                .toFormatter().withResolverStyle(ResolverStyle.STRICT);
    }

    @Override
    public String getXpath() {
        return path;
    }

    @Override
    public String getCategoryName() {
        return FIELDNAME;
    }

    @Override
    public ValidationEvent isValid(Node node) {
        ProfileValidationEventOrList validationEvents = new ProfileValidationEventOrList();

        String dataFrameRef = getChildNodeValue(node, DATA_FRAMEREF_FIELDNAME);
        if (dataFrameRef == null) {
            validationEvents.addEvent(createEvent(node, DATA_FRAMEREF_FIELDNAME, "valid date", dataFrameRef, ValidationEvent.FATAL_ERROR));
        } else {
            if (!isValidDate(dataFrameRef)) {
                validationEvents.addEvent(createEvent(node, DATA_FRAMEREF_FIELDNAME, "valid date with PATTERN " + PATTERN, dataFrameRef, ValidationEvent.FATAL_ERROR));
            }
        }

        String datedVehicleJourneyRef = getChildNodeValue(node, "DatedVehicleJourneyRef");
        if (!isValidGenericId("ServiceJourney", datedVehicleJourneyRef)) {
            validationEvents.addEvent(createEvent(node, "DatedVehicleJourneyRef", "valid ServiceJourney-ID", datedVehicleJourneyRef, ValidationEvent.FATAL_ERROR));
        }

        return validationEvents;
    }

    private boolean isValidDate(String date) {
        try {
            formatter.parse(date);
            return true;
        } catch (DateTimeParseException e) {
            return false;
        }
    }
}
