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

import jakarta.xml.bind.ValidationEvent;
import no.rutebanken.anshar.routes.validation.validators.CustomValidator;
import no.rutebanken.anshar.routes.validation.validators.Validator;
import no.rutebanken.anshar.subscription.SiriDataType;
import org.springframework.stereotype.Component;
import org.w3c.dom.Node;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;

import static no.rutebanken.anshar.routes.validation.validators.Constants.PT_SITUATION_ELEMENT;

/**
 * Validates ValidityPeriod
 *  - if Progress == open
 *  - Verify that StartTime is present and a valid timestamp
 *  - If EndTime is present, verify valid timestamp AND that EndTime is after StartTime
 */
@Validator(profileName = "norway", targetType = SiriDataType.SITUATION_EXCHANGE)
@Component
public class ValidityPeriodValidator extends CustomValidator {


    private static final String FIELDNAME = "ValidityPeriod";
    private static String path = PT_SITUATION_ELEMENT + FIELD_DELIMITER + FIELDNAME;

    private static final String START_TIME_FIELD_NAME = "StartTime";
    private static final String END_TIME_FIELD_NAME = "EndTime";
    private static final int MAX_SX_VALIDITY_SECONDS = 24*3600*365; // One year


    @Override
    public String getXpath() {
        return path;
    }

    @Override
    public ValidationEvent isValid(Node node) {

        if (node == null || node.getChildNodes().getLength() == 0) {
            return  createEvent(node, FIELDNAME, "not null", null, ValidationEvent.WARNING);
        }

        boolean progressClosed = false;
        String progress = getSiblingNodeValue(node, "Progress");

        if (progress != null && progress.equals("closed")) {
            progressClosed = true;
        }


        final String startTimeValue = getChildNodeValue(node, START_TIME_FIELD_NAME);
        final ZonedDateTime startTime;

        if (startTimeValue != null && !startTimeValue.isEmpty()) {
            startTime = parseDate(startTimeValue);
            if (startTime == null) {
                return createEvent(node, START_TIME_FIELD_NAME, "valid date", startTimeValue, ValidationEvent.FATAL_ERROR);
            }
        } else {
            return createEvent(node, START_TIME_FIELD_NAME, "StartTime not null", null, ValidationEvent.FATAL_ERROR);
        }

        final String endTimeValue = getChildNodeValue(node, END_TIME_FIELD_NAME);
        if (progressClosed) {

            if (endTimeValue == null || endTimeValue.isEmpty()) {
                return createEvent(node, END_TIME_FIELD_NAME, "EndTime when Progress is 'closed'", endTimeValue, ValidationEvent.FATAL_ERROR);
            }

            final ZonedDateTime endTime = parseDate(endTimeValue);
            if (endTime == null) {
                return createEvent(node, END_TIME_FIELD_NAME, "valid date", endTimeValue, ValidationEvent.FATAL_ERROR);
            } else if (endTime.minus(5, ChronoUnit.HOURS).isBefore(startTime)){
                return createEvent(node, END_TIME_FIELD_NAME, "EndTime should be at least 5 hours after StartTime when Progress is closed", endTimeValue, ValidationEvent.ERROR);

            }

        } else if (endTimeValue != null && !endTimeValue.isEmpty()) {
            final ZonedDateTime endTime = parseDate(endTimeValue);
            if (endTime != null) {
                if (endTime.isBefore(ZonedDateTime.now())) {
                    return createEvent(node, END_TIME_FIELD_NAME, "date after 'now'", endTimeValue, ValidationEvent.WARNING);
                }
                if (startTime != null) {
                    final long startTimeSec = startTime.toEpochSecond();
                    final long endTimeSec = endTime.toEpochSecond();
                    final long validityPeriod = endTimeSec - startTimeSec;

                    if (validityPeriod > MAX_SX_VALIDITY_SECONDS) {
                        return createCustomFieldEvent(node, "EndTime states too long validity (" + startTime + " => " + endTime + ")" , ValidationEvent.WARNING);
                    }
                }
            }

        }


        return null;
    }

}
