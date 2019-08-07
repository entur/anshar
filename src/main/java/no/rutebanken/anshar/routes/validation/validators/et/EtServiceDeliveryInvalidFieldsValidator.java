package no.rutebanken.anshar.routes.validation.validators.et;

import no.rutebanken.anshar.routes.validation.validators.ServiceDeliveryInvalidFieldsValidator;
import no.rutebanken.anshar.routes.validation.validators.Validator;
import no.rutebanken.anshar.subscription.SiriDataType;
import org.springframework.stereotype.Component;

/**
 * Verifies that forbidden fields do not exist
 */
@SuppressWarnings("unchecked")
@Validator(profileName = "norway", targetType = SiriDataType.ESTIMATED_TIMETABLE)
@Component
public class EtServiceDeliveryInvalidFieldsValidator extends ServiceDeliveryInvalidFieldsValidator {

    public EtServiceDeliveryInvalidFieldsValidator() {
        super(ET_DELIVERY);
    }
}

