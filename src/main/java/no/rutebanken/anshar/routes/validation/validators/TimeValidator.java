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

package no.rutebanken.anshar.routes.validation.validators;

import org.w3c.dom.Node;

import javax.xml.bind.ValidationEvent;
import java.time.ZonedDateTime;

public abstract class TimeValidator extends CustomValidator {

    protected enum Mode {BEFORE, AFTER};

    /**
     * Checks validity of field named @fieldName from @node
     *
     * if @comparisonFieldName-sibling is found, values are compared according to @mode
     *
     * @param node
     * @param fieldname
     * @param comparisonFieldName
     * @param mode
     * @return
     */
    protected ValidationEvent checkTimeValidity(Node node, String fieldname, String comparisonFieldName, TimeValidator.Mode mode) {
        String field =  getSiblingNodeValue(node, fieldname);

        if (field != null) {
            final ZonedDateTime time = ZonedDateTime.parse(field);
            if (time == null) {
                //Check valid date
                return createEvent(node, fieldname, "valid date", field, ValidationEvent.FATAL_ERROR);
            }

            String comparisonField = getSiblingNodeValue(node, comparisonFieldName);
            if (comparisonField != null && !comparisonField.isEmpty()) {
                //Check that arrival is before or equal to departure
                final ZonedDateTime comparisonTime = ZonedDateTime.parse(comparisonField);
                if (!isValid(time, comparisonTime, mode)) {
                    return createEvent(node, fieldname,  "" + mode  + " " + comparisonFieldName + " [" + comparisonTime + "]", field, ValidationEvent.FATAL_ERROR);
                }
            }

        }

        return null;
    }

    /**
     * Checks if time-objects are equal or before/after according to mode
     * @param time_1
     * @param time_2
     * @param mode
     * @return
     */
    private boolean isValid(ZonedDateTime time_1, ZonedDateTime time_2, TimeValidator.Mode mode) {
        if (time_1 != null && time_2 != null) {
            if (time_1.equals(time_2)) {
                return true;
            }
            switch (mode) {
                case BEFORE:
                    return time_1.isBefore(time_2);
                case AFTER:
                    return time_1.isAfter(time_2);
            }
        }
        return false;
    }
}
