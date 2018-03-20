package no.rutebanken.anshar.subscription.helpers;

import uk.org.siri.siri20.DirectionRefStructure;
import uk.org.siri.siri20.LineDirectionStructure;
import uk.org.siri.siri20.LineRef;
import uk.org.siri.siri20.OperatorRefStructure;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class FilterMapPresets {

    public Map<Class, Set<Object>> get(SubscriptionPreset preset) {

        Map<Class, Set<Object>> filters = new HashMap<>();
        switch (preset) {
            case BYBANEN:
                filters.put(LineDirectionStructure.class, getSkyssLineDirectionFilters());
                break;
            case HED:
                filters.put(OperatorRefStructure.class, getOperatorFilter("40")); // 40 - Fara's internal OperatorRef for Hedmark
                break;
            case OPP:
                filters.put(OperatorRefStructure.class, getOperatorFilter("51")); // 51 - Fara's internal OperatorRef for Oppland
                break;
        }

        return filters;
    }

    private Set<Object> getOperatorFilter(String operator) {
        OperatorRefStructure operatorRefStructure = new OperatorRefStructure();
        operatorRefStructure.setValue(operator);
        Set<Object> operators = new HashSet<>();
        operators.add(operatorRefStructure);
        return operators;
    }

    private Set<Object> getSkyssLineDirectionFilters() {

        LineRef lineRef = new LineRef();
        lineRef.setValue("1");

        DirectionRefStructure directionRef = new DirectionRefStructure();
        directionRef.setValue("10");

        LineDirectionStructure lineDir = new LineDirectionStructure();
        lineDir.setLineRef(lineRef);
        lineDir.setDirectionRef(directionRef);

        LineRef lineRef2 = new LineRef();
        lineRef2.setValue("1");

        DirectionRefStructure directionRef2 = new DirectionRefStructure();
        directionRef2.setValue("11");

        LineDirectionStructure lineDir2 = new LineDirectionStructure();
        lineDir2.setLineRef(lineRef2);
        lineDir2.setDirectionRef(directionRef2);

        Set<Object> lines = new HashSet<>();
        lines.add(lineDir);
        lines.add(lineDir2);

        return lines;
    }
}
