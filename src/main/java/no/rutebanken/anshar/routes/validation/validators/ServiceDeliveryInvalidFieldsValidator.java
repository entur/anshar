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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static no.rutebanken.anshar.routes.validation.validators.Constants.SERVICE_DELIVERY;

public abstract class ServiceDeliveryInvalidFieldsValidator extends CustomValidator {

    private static final String FIELDNAME = "ServiceDelivery";
    private String path = SERVICE_DELIVERY;

    protected static final String SX_DELIVERY = "SituationExchangeDelivery";
    protected static final String ET_DELIVERY = "EstimatedTimetableDelivery";
    protected static final String VM_DELIVERY = "VehicleMonitoringDelivery";

    private static final List<String> allServiceDeliveryTypes = Arrays.asList(SX_DELIVERY, ET_DELIVERY, VM_DELIVERY);

    private final String[] invalidDeliveryTypes;

    protected ServiceDeliveryInvalidFieldsValidator(String validDeliveryType) {
        List<String> invalidDeliveryTypesList = new ArrayList<>();
        for (String type : allServiceDeliveryTypes) {
            if (!type.equals(validDeliveryType)) {
                invalidDeliveryTypesList.add(type);
            }
        }
        invalidDeliveryTypes = invalidDeliveryTypesList.toArray(new String[0]);
    }

    @Override
    public String getCategoryName() {
        return FIELDNAME + " for this SubscriptionType";
    }

    /**
     * Verifies that the string-value of the provided node is built up using the pattern defined
     * @param node
     * @return
     */
    @Override
    public ValidationEvent isValid(Node node) {
        return verifyNonExistingFields(node, FIELDNAME, invalidDeliveryTypes);
    }

    @Override
    public String getXpath() {
        return path;
    }
}
