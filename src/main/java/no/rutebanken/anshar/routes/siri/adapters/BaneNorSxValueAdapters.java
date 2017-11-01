package no.rutebanken.anshar.routes.siri.adapters;

import no.rutebanken.anshar.routes.siri.transformer.ValueAdapter;
import no.rutebanken.anshar.routes.siri.transformer.impl.JbvCodeMapper;
import no.rutebanken.anshar.subscription.SubscriptionSetup;
import uk.org.siri.siri20.StopPointRef;

import java.util.ArrayList;
import java.util.List;

@Mapping(id="banenorsx")
public class BaneNorSxValueAdapters extends MappingAdapter {


    @Override
    public List<ValueAdapter> getValueAdapters(SubscriptionSetup subscriptionSetup) {

        List<ValueAdapter> valueAdapters = new ArrayList<>();
        valueAdapters.add(new JbvCodeMapper(subscriptionSetup.getDatasetId(), StopPointRef.class));

//        if (subscriptionSetup.getDatasetId() != null && !subscriptionSetup.getDatasetId().isEmpty()) {
//            List<ValueAdapter> datasetPrefix = createIdPrefixAdapters(subscriptionSetup.getDatasetId());
//            if (!subscriptionSetup.getMappingAdapters().containsAll(datasetPrefix)) {
//                subscriptionSetup.getMappingAdapters().addAll(datasetPrefix);
//            }
//        }

        return valueAdapters;
    }
}
