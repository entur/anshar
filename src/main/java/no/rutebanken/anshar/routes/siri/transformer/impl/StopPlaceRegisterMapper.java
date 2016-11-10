package no.rutebanken.anshar.routes.siri.transformer.impl;


import no.rutebanken.anshar.routes.siri.transformer.ValueAdapter;

import java.util.HashMap;
import java.util.Map;

public class StopPlaceRegisterMapper extends ValueAdapter {


    Map<String, String> stopPlaceMappings = new HashMap<>();

    public StopPlaceRegisterMapper(Class clazz) {
        super(clazz);
    }
    public StopPlaceRegisterMapper(Class clazz, Map<String, String> stopPlaceMappings) {
        super(clazz);
        this.stopPlaceMappings = stopPlaceMappings;
    }

    public String apply(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        return stopPlaceMappings.get(text);
    }
}
