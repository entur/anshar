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

import org.w3c.dom.Node;

import javax.xml.bind.ValidationEvent;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public abstract class StringStructureValidator extends CustomValidator {


    protected static String FIELDNAME;

    private static final String ATTRIBUTE = "lang";


    @Override
    public String getCategoryName() {
        return FIELDNAME;
    }

    @Override
    public ValidationEvent isValid(Node node) {
        final List<Node> childNodesByName = getChildNodesByName(node, FIELDNAME);

        boolean requireLangAttribute = (childNodesByName.size() > 1);

        if (childNodesByName.isEmpty()) {
            return createEvent(node, FIELDNAME, "not empty", null, ValidationEvent.FATAL_ERROR);
        }

        Set<String> foundLangAttributes = new HashSet<>();
        for (Node textNode : childNodesByName) {

            String nodeValue = getNodeValue(textNode);

            if (nodeValue == null || nodeValue.isEmpty()) {
                return createEvent(textNode, FIELDNAME, "not empty", nodeValue, ValidationEvent.FATAL_ERROR);
            }

            if (requireLangAttribute) {
                final String lang = getNodeAttributeValue(textNode, ATTRIBUTE);
                if (lang == null || lang.isEmpty()) {
                    return createEvent(textNode, FIELDNAME, "lang-attribute when more than one " + FIELDNAME, lang, ValidationEvent.FATAL_ERROR);
                } else if (foundLangAttributes.contains(lang)) {
                    return createEvent(textNode, FIELDNAME, "unique lang-attribute", lang, ValidationEvent.FATAL_ERROR);
                } else {
                    foundLangAttributes.add(lang);
                }
            }
        }
        return null;
    }
}
