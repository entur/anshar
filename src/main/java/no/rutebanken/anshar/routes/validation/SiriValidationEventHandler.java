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
import java.util.concurrent.atomic.AtomicInteger;

class SiriValidationEventHandler implements ValidationEventHandler {

    final Map<String, Map<String, ValidationEvent>> categorizedEvents = new HashMap<>();
    final Map<String, Integer> equalsEventCounter = new HashMap<>();
    private final long timestamp = System.currentTimeMillis();

    public boolean handleEvent(ValidationEvent event) {
        handleCategorizedEvent(this.getClass().getSimpleName(), event);
        return true;
    }

    public void handleCategorizedEvent(String categoryName, ValidationEvent event) {


        String message = event.getMessage();

        final Map<String, ValidationEvent> category = categorizedEvents.getOrDefault(categoryName, new HashMap<>());

        if (!category.containsKey(message)) {
            category.put(message, event);
        }

        int counter = equalsEventCounter.getOrDefault(message, 0);
        counter++;
        equalsEventCounter.put(message, counter);

        categorizedEvents.put(categoryName, category);
    }

    public JSONObject toJSON() {
        JSONObject obj = new JSONObject();
        obj.put("timestamp", timestamp);

        AtomicInteger counter = new AtomicInteger();

        if (categorizedEvents.size() == 1 && categorizedEvents.containsKey(this.getClass().getSimpleName())) {
            //Simple json without category
            JSONArray eventList = new JSONArray();

            categorizedEvents.get(this.getClass().getSimpleName())
                    .values().forEach(e -> {
                JSONObject event = createJsonValidationEvent(e);
                counter.addAndGet(getOccurrenceCount(e));
                eventList.add(event);
            });

            obj.put("events", eventList);
        } else {
            JSONArray categories = new JSONArray();

            categorizedEvents.keySet().forEach(cat -> {
                final Map<String, ValidationEvent> eventMap = categorizedEvents.get(cat);
                JSONObject category = new JSONObject();
                category.put("category", cat);

                JSONArray eventList = new JSONArray();

                eventMap.values().forEach(e -> {
                    JSONObject event = createJsonValidationEvent(e);
                    counter.addAndGet(getOccurrenceCount(e));
                    eventList.add(event);
                });
                category.put("events", eventList);

                categories.add(category);
            });

            obj.put("categories", categories);
        }
        obj.put("errorCount", counter.intValue());
        return obj;
    }

    private Integer getOccurrenceCount(ValidationEvent e) {
        final String message = e.getMessage();
        if (message != null) {
            final Integer count = equalsEventCounter.get(message);
            if (count != null) {
                return count;
            }
        }
        return 0;
    }

    private JSONObject createJsonValidationEvent(ValidationEvent e) {
        JSONObject event = new JSONObject();

        event.put("severity", resolveSeverity(e.getSeverity()));
        event.put("message", wrapAsString(e.getMessage()));
        event.put("numberOfOccurrences", wrapAsString(getOccurrenceCount(e)));

        JSONObject locator = new JSONObject();
        locator.put("lineNumber", wrapAsString(e.getLocator().getLineNumber()));
        locator.put("columnNumber", wrapAsString(e.getLocator().getColumnNumber()));
        event.put("locator", locator);
        return event;
    }

    private String resolveSeverity(int severity) {
        switch (severity) {
            case ValidationEvent.FATAL_ERROR:
                return "FATAL_ERROR";
            case ValidationEvent.ERROR:
                return "ERROR";
            default:
                return "WARNING";
        }
    }

    private Object wrapAsString(Object o) {
        if (o != null) {
            if (! (o instanceof Integer)) {
                return "" + o;
            }
            return o;
        }
        return null;
    }
}
