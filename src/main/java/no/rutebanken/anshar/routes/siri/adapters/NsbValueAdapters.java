package no.rutebanken.anshar.routes.siri.adapters;

import no.rutebanken.anshar.routes.siri.transformer.ValueAdapter;
import no.rutebanken.anshar.routes.siri.transformer.impl.LeftPaddingAdapter;
import no.rutebanken.anshar.subscription.SubscriptionSetup;
import uk.org.ifopt.siri20.StopPlaceRef;
import uk.org.siri.siri20.StopPointRef;

import java.util.ArrayList;
import java.util.List;

@Mapping(id="nsb")
public class NsbValueAdapters extends MappingAdapter {


    @Override
    public List<ValueAdapter> getValueAdapters(SubscriptionSetup subscriptionSetup) {

        List<ValueAdapter> valueAdapters = new ArrayList<>();

        valueAdapters.add(new LeftPaddingAdapter(StopPointRef.class, 9, '0'));
        valueAdapters.add(new LeftPaddingAdapter(StopPlaceRef.class, 9, '0'));

        valueAdapters.addAll(createNsrIdMappingAdapters(subscriptionSetup.getIdMappingPrefixes()));

        if (subscriptionSetup.getDatasetId() != null && !subscriptionSetup.getDatasetId().isEmpty()) {
            List<ValueAdapter> datasetPrefix = createIdPrefixAdapters(subscriptionSetup.getDatasetId());
            if (!subscriptionSetup.getMappingAdapters().containsAll(datasetPrefix)) {
                subscriptionSetup.getMappingAdapters().addAll(datasetPrefix);
            }
        }

        return valueAdapters;
    }
}
