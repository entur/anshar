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
import org.w3c.dom.Node;

import java.util.Set;

public abstract class LimitedSubsetValidator extends CustomValidator {


    protected String FIELDNAME;

    protected Set<String> expectedValues;

    @Override
    public String getCategoryName() {
        return FIELDNAME;
    }


    /**
     * Validates that the string-value of the provided node is present, and defined in the expectedValues-set
     * @param node
     * @return
     */
    @Override
    public ValidationEvent isValid(Node node) {
        String nodeValue = getNodeValue(node);

        if (nodeValue == null || !expectedValues.contains(nodeValue)) {
            return  createEvent(node, FIELDNAME, "one of " + expectedValues, nodeValue, ValidationEvent.ERROR);
        }
        return null;
    }
}
