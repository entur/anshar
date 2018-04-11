package no.rutebanken.anshar.routes.validation.validators;

import no.rutebanken.anshar.subscription.SubscriptionSetup;
import org.springframework.stereotype.Component;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;


@Retention(RetentionPolicy.RUNTIME)
@Component
public @interface Validator {
    SubscriptionSetup.SubscriptionType type();
}
