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
import jakarta.xml.bind.ValidationEventLocator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
