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
import uk.org.siri.siri21.WorkflowStatusEnumeration;

import javax.xml.bind.ValidationEvent;

import static no.rutebanken.anshar.routes.validation.validators.Constants.PT_SITUATION_ELEMENT;

/**
 * Verifies that required fields are present
 */
@SuppressWarnings("unchecked")
@Validator(profileName = "norway", targetType = SiriDataType.SITUATION_EXCHANGE)
@Component
public class PtSituationElementValidator extends CustomValidator {

    private static final String FIELDNAME = "PtSituationElement";
    private String path = PT_SITUATION_ELEMENT;

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

        final String progress = getChildNodeValue(node, "Progress");

        if (progress != null && progress.equalsIgnoreCase(WorkflowStatusEnumeration.CLOSED.value())) {
            // When Progress==closed, Affects is not required
            return verifyRequiredFields(node, FIELDNAME,
                    "CreationTime",
                    "ParticipantRef",
                    "SituationNumber",
                    "Source",
                    "Progress",
                    "ValidityPeriod",
                    "ReportType"
            );
        } else {
            return verifyRequiredFields(node, FIELDNAME,
                    "CreationTime",
                    "ParticipantRef",
                    "SituationNumber",
                    "Source",
                    "Progress",
                    "ValidityPeriod",
                    "ReportType",
                    "Summary",
                    "Affects"
            );
        }


    }
}
