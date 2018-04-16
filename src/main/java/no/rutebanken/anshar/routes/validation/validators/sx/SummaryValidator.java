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

package no.rutebanken.anshar.routes.validation.validators.sx;

import no.rutebanken.anshar.routes.validation.validators.CustomValidator;
import no.rutebanken.anshar.routes.validation.validators.Validator;
import no.rutebanken.anshar.subscription.SiriDataType;
import org.springframework.stereotype.Component;
import org.w3c.dom.Node;

import javax.xml.bind.ValidationEvent;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static no.rutebanken.anshar.routes.validation.validators.Constants.PT_SITUATION_ELEMENT;

@Validator(profileName = "norway", targetType = SiriDataType.SITUATION_EXCHANGE)
@Component
public class SummaryValidator extends CustomValidator {

    private static final String FIELDNAME = "Summary";
    private static final String ATTRIBUTE = "lang";
    private static final String path = PT_SITUATION_ELEMENT;

    @Override
    public String getXpath() {
        return path;
    }

    @Override
    public ValidationEvent isValid(Node node) {

        final List<Node> childNodesByName = getChildNodesByName(node, FIELDNAME);

        boolean requireLangAttribute = (childNodesByName.size() > 1);

        if (childNodesByName.isEmpty()) {
            return createEvent(node, FIELDNAME, "not empty", null, ValidationEvent.FATAL_ERROR);
        }

        Set<String> foundLangAttributes = new HashSet<>();
        for (Node summaryNode : childNodesByName) {

            String nodeValue = getNodeValue(summaryNode);

            if (nodeValue == null || nodeValue.isEmpty()) {
                return createEvent(summaryNode, FIELDNAME, "not empty", nodeValue, ValidationEvent.FATAL_ERROR);
            }

            if (requireLangAttribute) {
                final String lang = getNodeAttributeValue(summaryNode, ATTRIBUTE);
                if (lang == null || lang.isEmpty()) {
                    return createEvent(summaryNode, FIELDNAME, "lang-attribute when more than one Summary", lang, ValidationEvent.FATAL_ERROR);
                } else if (foundLangAttributes.contains(lang)) {
                    return createEvent(summaryNode, FIELDNAME, "unique lang-attribute", lang, ValidationEvent.FATAL_ERROR);
                } else {
                    foundLangAttributes.add(lang);
                }
            }
        }


        return null;
    }
}
