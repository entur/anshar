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

public abstract class NsrGenericIdValidator extends CustomValidator {


    protected String FIELDNAME;

    protected String ID_PATTERN;

    @Override
    public String getCategoryName() {
        return FIELDNAME;
    }

    /**
     * Verifies that the string-value of the provided node is built up using the pattern defined
     * @param node
     * @return
     */
    @Override
    public ValidationEvent isValid(Node node) {
        String nodeValue = getNodeValue(node);

        if (!isValidGenericId(ID_PATTERN, nodeValue)) {
            return  createEvent(node, FIELDNAME, "valid NeTEx-id formatted like CODESPACE:" + ID_PATTERN + ":ID", nodeValue, ValidationEvent.FATAL_ERROR);
        }

        return null;
    }
}
