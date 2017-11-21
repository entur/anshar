package no.rutebanken.anshar.routes.siri.adapters;

import no.rutebanken.anshar.routes.siri.processor.OstfoldIdPlatformPostProcessor;
import no.rutebanken.anshar.routes.siri.transformer.ValueAdapter;
import no.rutebanken.anshar.routes.siri.transformer.impl.StopPlaceRegisterMapper;
import no.rutebanken.anshar.subscription.SubscriptionSetup;
import uk.org.siri.siri20.DestinationRef;
import uk.org.siri.siri20.JourneyPlaceRefStructure;

import java.util.ArrayList;
import java.util.List;

@Mapping(id="ostfoldet")
public class OstfoldEtValueAdapters extends MappingAdapter {


    @Override
    public List<ValueAdapter> getValueAdapters(SubscriptionSetup subscriptionSetup) {

        List<ValueAdapter> valueAdapters = new ArrayList<>();
        valueAdapters.add(new OstfoldIdPlatformPostProcessor(subscriptionSetup));

        valueAdapters.add(new StopPlaceRegisterMapper(
                subscriptionSetup.getSubscriptionType(),
                subscriptionSetup.getDatasetId(),
                JourneyPlaceRefStructure.class,
                subscriptionSetup.getIdMappingPrefixes(),
                "StopPlace"));

        valueAdapters.add(new StopPlaceRegisterMapper(
                subscriptionSetup.getSubscriptionType(),
                subscriptionSetup.getDatasetId(),
                DestinationRef.class,
                subscriptionSetup.getIdMappingPrefixes(),
                "StopPlace"));

        if (subscriptionSetup.getDatasetId() != null && !subscriptionSetup.getDatasetId().isEmpty()) {
            List<ValueAdapter> datasetPrefix = createIdPrefixAdapters(subscriptionSetup.getDatasetId());
            if (!subscriptionSetup.getMappingAdapters().containsAll(datasetPrefix)) {
                subscriptionSetup.getMappingAdapters().addAll(datasetPrefix);
            }
        }

        return valueAdapters;
    }
}
