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
import java.util.ArrayList;
import java.util.List;

import static no.rutebanken.anshar.routes.validation.validators.Constants.PT_SITUATION_ELEMENT;


@Validator(profileName = "norway", targetType = SiriDataType.SITUATION_EXCHANGE)
@Component
public class PtSituationElementValidator extends CustomValidator {

    private static final String FIELDNAME = "PtSituationElement";
    private static final String path = PT_SITUATION_ELEMENT;

    public PtSituationElementValidator() {
    }

    @Override
    public String getCategoryName() {
        return FIELDNAME;
    }

    @Override
    public String getXpath() {
        return path;
    }

    @Override
    public ValidationEvent isValid(Node node) {

        List<String> missingFields = new ArrayList();


        String nodeName = "CreationTime";
        if (getChildNodeByName(node, nodeName) == null) {
            missingFields.add(nodeName);
        }

        nodeName = "ParticipantRef";
        if (getChildNodeByName(node, nodeName) == null) {
            missingFields.add(nodeName);
        }

        nodeName = "SituationNumber";
        if (getChildNodeByName(node, nodeName) == null) {
            missingFields.add(nodeName);
        }

        nodeName = "Source";
        if (getChildNodeByName(node, nodeName) == null) {
            missingFields.add(nodeName);
        }

        nodeName = "Progress";
        if (getChildNodeByName(node, nodeName) == null) {
            missingFields.add(nodeName);
        }

        nodeName = "ValidityPeriod";
        if (getChildNodeByName(node, nodeName) == null) {
            missingFields.add(nodeName);
        }

        nodeName = "ReportType";
        if (getChildNodeByName(node, nodeName) == null) {
            missingFields.add(nodeName);
        }

        nodeName = "Summary";
        if (getChildNodeByName(node, nodeName) == null) {
            missingFields.add(nodeName);
        }

        nodeName = "Affects";
        if (getChildNodeByName(node, nodeName) == null) {
            missingFields.add(nodeName);
        }

        if (!missingFields.isEmpty()) {
            return createMissingFieldEvent(node, FIELDNAME, missingFields, ValidationEvent.FATAL_ERROR);
        }

        return null;
    }
}
