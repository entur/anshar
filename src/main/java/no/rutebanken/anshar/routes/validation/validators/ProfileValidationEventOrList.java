package no.rutebanken.anshar.routes.validation.validators;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.bind.ValidationEvent;
import javax.xml.bind.ValidationEventLocator;
import java.util.ArrayList;
import java.util.List;

/**
 * Holds one ore more ValidationEvents.
 */
public class ProfileValidationEventOrList implements ValidationEvent {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    List<ValidationEvent> events = new ArrayList<>();

    public void addEvent(ValidationEvent event) {
        events.add(event);
    }

    public List<ValidationEvent> getEvents() {
        return events;
    }

    @Override
    public int getSeverity() {
        verifySize();
        if (events.size() == 1) {
            return events.get(0).getSeverity();
        }
        return 0;
    }

    @Override
    public String getMessage() {
        verifySize();
        if (events.size() == 1) {
            return events.get(0).getMessage();
        }
        return null;
    }

    @Override
    public Throwable getLinkedException() {
        verifySize();
        if (events.size() == 1) {
            return events.get(0).getLinkedException();
        }
        return null;
    }

    @Override
    public ValidationEventLocator getLocator() {
        verifySize();
        if (events.size() == 1) {
            return events.get(0).getLocator();
        }
        return null;
    }

    private void verifySize() {
        if (events.size() > 1) {
            logger.error("Attempting to fetch single ValidationEvent when multiple events exist");
        }
    }
}
