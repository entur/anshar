package no.rutebanken.anshar.routes.siri.adapters;

import no.rutebanken.anshar.routes.siri.processor.BaneNorArrivalDepartureCancellationProcessor;
import no.rutebanken.anshar.routes.siri.processor.BaneNorIdPlatformPostProcessor;
import no.rutebanken.anshar.routes.siri.processor.OperatorFilterPostProcessor;
import no.rutebanken.anshar.routes.siri.transformer.ValueAdapter;
import no.rutebanken.anshar.subscription.SubscriptionSetup;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Mapping(id="banenoret")
public class BaneNorEtValueAdapters extends MappingAdapter {


    @Override
    public List<ValueAdapter> getValueAdapters(SubscriptionSetup subscriptionSetup) {

        List<ValueAdapter> valueAdapters = new ArrayList<>();
        valueAdapters.add(new BaneNorIdPlatformPostProcessor(subscriptionSetup.getSubscriptionType(), subscriptionSetup.getDatasetId()));

        Map<String, String> operatorOverrideMapping = new HashMap<>();
        operatorOverrideMapping.put("NG", "GJB");
        operatorOverrideMapping.put("FLY", "FLT");
        operatorOverrideMapping.put("SJ", "SJV");

        List<String> operatorsToIgnore = new ArrayList<>();//Arrays.asList("BN", "");
        valueAdapters.add(new OperatorFilterPostProcessor(operatorsToIgnore, operatorOverrideMapping));

        valueAdapters.add(new BaneNorArrivalDepartureCancellationProcessor());

        return valueAdapters;
    }
}
