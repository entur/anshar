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

package no.rutebanken.anshar.routes.validation;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import javax.xml.bind.ValidationEvent;
import javax.xml.bind.ValidationEventHandler;
import java.util.HashMap;
import java.util.Map;

class SiriValidationEventHandler implements ValidationEventHandler {

    Map<String, ValidationEvent> events = new HashMap<>();
    Map<String, Integer> equalsEventCounter = new HashMap<>();

    public boolean handleEvent(ValidationEvent event) {
        String message = event.getMessage();

        events.put(message, event);

        int counter = equalsEventCounter.getOrDefault(message, 0);
        counter++;
        equalsEventCounter.put(message, counter);

        return true;
    }

    public JSONObject toJSON() {
        JSONObject obj = new JSONObject();
        JSONArray eventList = new JSONArray();

        events.keySet().forEach(key -> {
            ValidationEvent e = events.get(key);

            JSONObject event = new JSONObject();

            event.put("severity", wrapAsString(e.getSeverity()));
            event.put("message", wrapAsString(e.getMessage()));
            event.put("numberOfOcurrences", wrapAsString(equalsEventCounter.get(key)));

            JSONObject locator = new JSONObject();
            locator.put("lineNumber", wrapAsString(e.getLocator().getLineNumber()));
            locator.put("columnNumber", wrapAsString(e.getLocator().getColumnNumber()));
            locator.put("offset", wrapAsString(e.getLocator().getOffset()));
            locator.put("object", wrapAsString(e.getLocator().getObject()));
            locator.put("node", wrapAsString(e.getLocator().getNode()));
            locator.put("url", wrapAsString(e.getLocator().getURL()));
            event.put("locator", locator);

            eventList.add(event);
        });
        obj.put("events", eventList);
        return obj;
    }

    private String wrapAsString(Object o) {
        return ""+o;
    }
}
