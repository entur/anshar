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

import jakarta.xml.bind.ValidationEvent;
import no.rutebanken.anshar.routes.validation.validators.StringStructureValidator;
import no.rutebanken.anshar.routes.validation.validators.Validator;
import no.rutebanken.anshar.subscription.SiriDataType;
import org.springframework.stereotype.Component;
import org.w3c.dom.Node;

import java.util.List;

import static no.rutebanken.anshar.routes.validation.validators.Constants.PT_SITUATION_ELEMENT;

/**
 * Verifies that the value for field Description is valid
 *  - has text
 *  - if more than one is defined, language-attribute is required
 *
 */
@Validator(profileName = "norway", targetType = SiriDataType.SITUATION_EXCHANGE)
@Component
public class SummaryValidator extends StringStructureValidator {

    private String path;

    public SummaryValidator() {
        FIELDNAME = "Summary";
        path = PT_SITUATION_ELEMENT;
    }

    @Override
    public String getXpath() {
        return path;
    }

    @Override
    public ValidationEvent isValid(Node node) {
        final ValidationEvent validationEvent = super.isValid(node);
        if (validationEvent != null) {
            return validationEvent;
        }

        /*
         Check max-length for Summary
         */
        final List<Node> childNodesByName = getChildNodesByName(node, FIELDNAME);

        for (Node textNode : childNodesByName) {
            String nodeValue = getNodeValue(textNode);
            if (nodeValue != null && nodeValue.length() > 160) {
                return createEvent(node, FIELDNAME, "shorter than max-length", ""+nodeValue.length() + " chars", ValidationEvent.WARNING);
            }
        }
        return null;
    }
}
