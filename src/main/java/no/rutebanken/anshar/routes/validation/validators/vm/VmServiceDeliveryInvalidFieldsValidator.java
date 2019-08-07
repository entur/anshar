package no.rutebanken.anshar.routes.validation.validators.vm;

import no.rutebanken.anshar.routes.validation.validators.ServiceDeliveryInvalidFieldsValidator;
import no.rutebanken.anshar.routes.validation.validators.Validator;
import no.rutebanken.anshar.subscription.SiriDataType;
import org.springframework.stereotype.Component;

/**
 * Verifies that forbidden fields do not exist
 */
@SuppressWarnings("unchecked")
@Validator(profileName = "norway", targetType = SiriDataType.VEHICLE_MONITORING)
@Component
public class VmServiceDeliveryInvalidFieldsValidator extends ServiceDeliveryInvalidFieldsValidator {

    public VmServiceDeliveryInvalidFieldsValidator() {
        super(VM_DELIVERY);
    }
}

