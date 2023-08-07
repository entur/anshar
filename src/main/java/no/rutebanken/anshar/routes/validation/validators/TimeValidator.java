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

import jakarta.xml.bind.ValidationEvent;
import org.w3c.dom.Node;

import java.time.ZonedDateTime;

public abstract class TimeValidator extends CustomValidator {

    protected enum Mode {BEFORE, AFTER}

    /**
     * Checks validity of field named @fieldName from @node
     *
     * if @comparisonFieldName-sibling is found, values are compared according to @mode
     *
     * E.g.: Could be used to verify that arrival is valid, and is before departure
     *
     * @param node
     * @param fieldname
     * @param comparisonFieldName
     * @param mode
     * @return
     */
    protected ValidationEvent checkTimeValidity(Node node, String fieldname, String comparisonFieldName, TimeValidator.Mode mode) {

        if (containsCancellation(node)) {
            // Do not validate increasing time as arrival, departure or both has been cancelled
            return null;
        }


        String field =  getSiblingNodeValue(node, fieldname);

        if (field != null) {
            final ZonedDateTime time = parseDate(field);
            if (time == null) {
                //Check valid date
                return createEvent(node, fieldname, "valid date", field, ValidationEvent.FATAL_ERROR);
            }

            String comparisonField = getSiblingNodeValue(node, comparisonFieldName);
            if (comparisonField != null && !comparisonField.isEmpty()) {
                //Check that arrival is before or equal to departure
                final ZonedDateTime comparisonTime = parseDate(comparisonField);
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

    private boolean containsCancellation(Node node) {
        return isCancelled(node) || isArrivalCancelled(node) || isDepartureCancelled(node);
    }

    private boolean isCancelled(Node node) {
        String cancellation = getSiblingNodeValue(node, "Cancellation");
        if (cancellation != null) {
            return cancellation.toLowerCase().equals("true");
        }

        return false;
    }

    private boolean isArrivalCancelled(Node node) {
        String arrivalStatus = getSiblingNodeValue(node, "ArrivalStatus");
        if (arrivalStatus != null && !arrivalStatus.isEmpty()) {
            return arrivalStatus.toLowerCase().equals("cancelled");
        }
        return false;
    }

    private boolean isDepartureCancelled(Node node) {
        String departureStatus = getSiblingNodeValue(node, "DepartureStatus");
        if (departureStatus != null && !departureStatus.isEmpty()) {
            return departureStatus.toLowerCase().equals("cancelled");
        }
        return false;
    }
}
