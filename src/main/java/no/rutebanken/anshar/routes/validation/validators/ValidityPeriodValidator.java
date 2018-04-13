package no.rutebanken.anshar.routes.validation.validators;

import no.rutebanken.anshar.subscription.SiriDataType;
import org.springframework.stereotype.Component;
import org.w3c.dom.Node;

import javax.xml.bind.ValidationEvent;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;

import static no.rutebanken.anshar.routes.validation.validators.Constants.PT_SITUATION_ELEMENT;

@Validator(profileName = "norway", targetType = SiriDataType.SITUATION_EXCHANGE)
@Component
public class ValidityPeriodValidator extends CustomValidator {


    private static final String FIELDNAME = "ValidityPeriod";
    private static final String path = PT_SITUATION_ELEMENT + "/" + FIELDNAME;


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


        final String startTimeValue = getChildNodeValue(node, "StartTime");
        final ZonedDateTime startTime;

        if (startTimeValue != null && !startTimeValue.isEmpty()) {
            startTime = ZonedDateTime.parse(startTimeValue);
            if (startTime == null) {
                return createEvent(node, FIELDNAME, "valid date", startTimeValue, ValidationEvent.FATAL_ERROR);
            }
        } else {
            return createEvent(node, FIELDNAME, "StartTime not null", null, ValidationEvent.FATAL_ERROR);
        }

        final String endTimeValue = getChildNodeValue(node, "EndTime");
        if (progressClosed) {

            if (endTimeValue == null || endTimeValue.isEmpty()) {
                return createEvent(node, FIELDNAME, "EndTime when Progress is 'closed'", endTimeValue, ValidationEvent.FATAL_ERROR);
            }

            final ZonedDateTime endTime = ZonedDateTime.parse(endTimeValue);
            if (endTime == null) {
                return createEvent(node, FIELDNAME, "valid date", endTimeValue, ValidationEvent.FATAL_ERROR);
            } else if (endTime.minus(5, ChronoUnit.HOURS).isBefore(startTime)){
                return createEvent(node, FIELDNAME, "EndTime to be at least 5 hours after Startime when Progress is closed", endTimeValue, ValidationEvent.ERROR);

            }

        } else if (endTimeValue != null && !endTimeValue.isEmpty()) {
            final ZonedDateTime parsedValue = ZonedDateTime.parse(endTimeValue);
            if (parsedValue != null && parsedValue.isBefore(ZonedDateTime.now())) {
                return createEvent(node, FIELDNAME, "date after 'now'", endTimeValue, ValidationEvent.WARNING);
            }
        }


        return null;
    }

}
