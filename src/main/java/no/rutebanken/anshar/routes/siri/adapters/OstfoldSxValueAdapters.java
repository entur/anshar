package no.rutebanken.anshar.routes.siri.adapters;

import no.rutebanken.anshar.routes.siri.transformer.ValueAdapter;
import no.rutebanken.anshar.routes.siri.transformer.impl.StopPlaceRegisterMapper;
import no.rutebanken.anshar.subscription.SubscriptionSetup;
import uk.org.ifopt.siri20.StopPlaceRef;
import uk.org.siri.siri20.StopPointRef;

import java.util.ArrayList;
import java.util.List;

@Mapping(id="ostfoldsx")
public class OstfoldSxValueAdapters extends MappingAdapter {


    @Override
    public List<ValueAdapter> getValueAdapters(SubscriptionSetup subscriptionSetup) {

        List<ValueAdapter> valueAdapters = new ArrayList<>();

        valueAdapters.add(new StopPlaceRegisterMapper(subscriptionSetup.getSubscriptionType(), subscriptionSetup.getDatasetId(), StopPlaceRef.class, subscriptionSetup.getIdMappingPrefixes(), "StopPlace"));
        valueAdapters.add(new StopPlaceRegisterMapper(subscriptionSetup.getSubscriptionType(), subscriptionSetup.getDatasetId(), StopPointRef.class, subscriptionSetup.getIdMappingPrefixes(), "StopPlace"));


        if (subscriptionSetup.getDatasetId() != null && !subscriptionSetup.getDatasetId().isEmpty()) {
            List<ValueAdapter> datasetPrefix = createIdPrefixAdapters(subscriptionSetup.getDatasetId());
            if (!subscriptionSetup.getMappingAdapters().containsAll(datasetPrefix)) {
                subscriptionSetup.getMappingAdapters().addAll(datasetPrefix);
            }
        }

        return valueAdapters;
    }
}
