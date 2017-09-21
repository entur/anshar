package no.rutebanken.anshar.routes.siri.adapters;

import no.rutebanken.anshar.routes.siri.processor.BaneNorIdPlatformPostProcessor;
import no.rutebanken.anshar.routes.siri.transformer.ValueAdapter;
import no.rutebanken.anshar.subscription.SubscriptionSetup;

import java.util.ArrayList;
import java.util.List;

@Mapping(id="banenor")
public class BaneNorValueAdapters extends MappingAdapter {


    @Override
    public List<ValueAdapter> getValueAdapters(SubscriptionSetup subscriptionSetup) {

        List<ValueAdapter> valueAdapters = new ArrayList<>();
        valueAdapters.add(new BaneNorIdPlatformPostProcessor());

//        if (subscriptionSetup.getDatasetId() != null && !subscriptionSetup.getDatasetId().isEmpty()) {
//            List<ValueAdapter> datasetPrefix = createIdPrefixAdapters(subscriptionSetup.getDatasetId());
//            if (!subscriptionSetup.getMappingAdapters().containsAll(datasetPrefix)) {
//                subscriptionSetup.getMappingAdapters().addAll(datasetPrefix);
//            }
//        }

        return valueAdapters;
    }
}
