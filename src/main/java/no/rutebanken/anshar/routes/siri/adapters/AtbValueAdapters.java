package no.rutebanken.anshar.routes.siri.adapters;

import no.rutebanken.anshar.routes.siri.transformer.ValueAdapter;
import no.rutebanken.anshar.subscription.SubscriptionSetup;

import java.util.List;

@Mapping(id="atb")
public class AtbValueAdapters extends AktValueAdapters {


    @Override
    public List<ValueAdapter> getValueAdapters(SubscriptionSetup subscriptionSetup) {
        return super.getValueAdapters(subscriptionSetup);
    }
}