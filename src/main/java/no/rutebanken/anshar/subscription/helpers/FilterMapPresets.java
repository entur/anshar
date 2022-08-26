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

package no.rutebanken.anshar.subscription.helpers;

import uk.org.siri.siri21.DirectionRefStructure;
import uk.org.siri.siri21.LineDirectionStructure;
import uk.org.siri.siri21.LineRef;
import uk.org.siri.siri21.OperatorRefStructure;

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
            case INN:
                filters.put(OperatorRefStructure.class, getOperatorFilter("51")); // 51 - Fara's internal OperatorRef for Innlandet
                break;
            case VKT:
                filters.put(OperatorRefStructure.class, getOperatorFilter("70")); // 70 - Fara's internal OperatorRef for Vestfold
                break;
            case TEL:
                filters.put(OperatorRefStructure.class, getOperatorFilter("80")); // 80 - Fara's internal OperatorRef for Telemark
                break;
            default:
                // ignore
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
