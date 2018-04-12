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
            return  createEvent(node, FIELDNAME, "not null", null);
        }

        boolean progressClosed = false;
        Node progressNode = getSiblingNodeByName(node, "Progress");

        final String progressNodeValue = getNodeValue(progressNode);
        if (progressNodeValue != null && progressNodeValue.equals("closed")) {
            progressClosed = true;
        }


        final Node startTimeNode = getChildNodeByName(node, "StartTime");
        final ZonedDateTime startTime;

        if (startTimeNode != null) {
            final String timeValue = getNodeValue(startTimeNode);
            if (timeValue != null && !timeValue.isEmpty()) {
                startTime = ZonedDateTime.parse(timeValue);
                if (startTime == null) {
                    return createEvent(node, FIELDNAME, "valid date", timeValue);
                }
            } else {
                return createEvent(node, FIELDNAME, "StartTime not null", timeValue);
            }
        } else {
            return createEvent(node, FIELDNAME, "StartTime not null", null);
        }

        final Node endTimeNode = getChildNodeByName(node, "EndTime");
        if (progressClosed) {
            final String timeValue = getNodeValue(endTimeNode);

            if (timeValue == null || timeValue.isEmpty()) {
                return createEvent(node, FIELDNAME, "EndTime when Progress is 'closed'", timeValue);
            }

            final ZonedDateTime endTime = ZonedDateTime.parse(timeValue);
            if (endTime == null) {
                return createEvent(node, FIELDNAME, "valid date", timeValue);
            } else if (endTime.minus(5, ChronoUnit.HOURS).isBefore(startTime)){
                return createEvent(node, FIELDNAME, "EndTime to be at least 5 hours after Startime when Progress is closed", timeValue);

            }

        } else if (endTimeNode != null) {
            final String timeValue = getNodeValue(endTimeNode);
            if (timeValue != null && !timeValue.isEmpty()) {
                final ZonedDateTime parsedValue = ZonedDateTime.parse(timeValue);
                if (parsedValue != null && parsedValue.isBefore(ZonedDateTime.now())) {
                    return createEvent(node, FIELDNAME, "date after 'now'", timeValue);
                }
            }
        }


        return null;
    }

}
