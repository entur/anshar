package no.rutebanken.anshar.routes.validation.validators.sx;

import no.rutebanken.anshar.routes.validation.validators.ServiceDeliveryInvalidFieldsValidator;
import no.rutebanken.anshar.routes.validation.validators.Validator;
import no.rutebanken.anshar.subscription.SiriDataType;
import org.springframework.stereotype.Component;

/**
 * Verifies that forbidden fields do not exist
 */
@SuppressWarnings("unchecked")
@Validator(profileName = "norway", targetType = SiriDataType.SITUATION_EXCHANGE)
@Component
public class SxServiceDeliveryInvalidFieldsValidator extends ServiceDeliveryInvalidFieldsValidator {

    public SxServiceDeliveryInvalidFieldsValidator() {
        super(SX_DELIVERY);
    }
}

