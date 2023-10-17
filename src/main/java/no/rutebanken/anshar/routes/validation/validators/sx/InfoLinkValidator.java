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
import no.rutebanken.anshar.routes.validation.validators.CustomValidator;
import no.rutebanken.anshar.routes.validation.validators.Validator;
import no.rutebanken.anshar.subscription.SiriDataType;
import org.springframework.stereotype.Component;
import org.w3c.dom.Node;

import java.net.MalformedURLException;
import java.net.URL;

import static no.rutebanken.anshar.routes.validation.validators.Constants.PT_SITUATION_ELEMENT;

/**
 * Validates the field Infolink. If present, it is verified that the value is a valid URI
 */
@Validator(profileName = "norway", targetType = SiriDataType.SITUATION_EXCHANGE)
@Component
public class InfoLinkValidator extends CustomValidator {


    private static final String FIELDNAME = "InfoLink";
    private static final String URI_FIELD = "Uri";
    private String path = PT_SITUATION_ELEMENT + "/InfoLinks/" + FIELDNAME;


    @Override
    public String getXpath() {
        return path;
    }

    @Override
    public ValidationEvent isValid(Node node) {
        final Node uriNode = getChildNodeByName(node, URI_FIELD);

        if (uriNode != null) {
            final String nodeValue = getNodeValue(uriNode);
            if (nodeValue != null) {
                try {
                    new URL(nodeValue);
                } catch (MalformedURLException t) {
                    return createEvent(node, FIELDNAME, "valid URL", nodeValue, ValidationEvent.WARNING);
                }
            }
        }

        return null;
    }
}
