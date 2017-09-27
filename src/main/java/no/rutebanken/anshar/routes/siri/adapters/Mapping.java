package no.rutebanken.anshar.routes.siri.adapters;


import org.springframework.stereotype.Component;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
@Component
public @interface Mapping {
    String id();
}
