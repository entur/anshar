package no.rutebanken.anshar.routes.siri.adapters;

import no.rutebanken.anshar.routes.siri.transformer.ValueAdapter;
import no.rutebanken.anshar.routes.siri.transformer.impl.LeftPaddingAdapter;
import no.rutebanken.anshar.subscription.SubscriptionSetup;
import uk.org.siri.siri20.LineRef;

import java.util.ArrayList;
import java.util.List;

@Mapping(id="kolumbus")
public class KolumbusValueAdapters extends AktValueAdapters {


    @Override
    public List<ValueAdapter> getValueAdapters(SubscriptionSetup subscriptionSetup) {
        List<ValueAdapter> valueAdapters = new ArrayList<>();
        valueAdapters.add(new LeftPaddingAdapter(LineRef.class, 4, '0'));

        valueAdapters.addAll(createNsrIdMappingAdapters(subscriptionSetup.getSubscriptionType(), subscriptionSetup.getDatasetId(), subscriptionSetup.getIdMappingPrefixes()));

        if (subscriptionSetup.getDatasetId() != null && !subscriptionSetup.getDatasetId().isEmpty()) {
            List<ValueAdapter> datasetPrefix = createIdPrefixAdapters(subscriptionSetup.getDatasetId());
            if (!subscriptionSetup.getMappingAdapters().containsAll(datasetPrefix)) {
                subscriptionSetup.getMappingAdapters().addAll(datasetPrefix);
            }
        }

        return valueAdapters;
    }
}
