package no.rutebanken.anshar.routes.siri.adapters;

import no.rutebanken.anshar.routes.siri.handlers.OutboundIdMappingPolicy;
import no.rutebanken.anshar.routes.siri.transformer.ValueAdapter;
import no.rutebanken.anshar.routes.siri.transformer.impl.PrefixAdapter;
import no.rutebanken.anshar.routes.siri.transformer.impl.StopPlaceRegisterMapper;
import no.rutebanken.anshar.subscription.SubscriptionSetup;
import no.rutebanken.anshar.subscription.helpers.MappingAdapterPresets;
import uk.org.ifopt.siri20.StopPlaceRef;
import uk.org.siri.siri20.*;

import java.util.ArrayList;
import java.util.List;

public abstract class MappingAdapter {

    public abstract List<ValueAdapter> getValueAdapters(SubscriptionSetup subscriptionSetup);

    public List<ValueAdapter> getOutboundValueAdapters(OutboundIdMappingPolicy mappingPolicy) {
        return new MappingAdapterPresets().getOutboundAdapters(mappingPolicy);
    }
    List<ValueAdapter> createNsrIdMappingAdapters(SubscriptionSetup.SubscriptionType type, String datasetId, List<String> idMappingPrefixes) {
        List<ValueAdapter> nsr = new ArrayList<>();
        nsr.add(new StopPlaceRegisterMapper(type, datasetId, StopPlaceRef.class, idMappingPrefixes, "StopPlace"));
        nsr.add(new StopPlaceRegisterMapper(type, datasetId, StopPointRef.class, idMappingPrefixes));
        nsr.add(new StopPlaceRegisterMapper(type, datasetId, JourneyPlaceRefStructure.class, idMappingPrefixes));
        nsr.add(new StopPlaceRegisterMapper(type, datasetId, DestinationRef.class, idMappingPrefixes));
        return nsr;
    }


    public List<ValueAdapter> createIdPrefixAdapters(String datasetId) {
        List<ValueAdapter> adapters = new ArrayList<>();
        adapters.add(new PrefixAdapter(LineRef.class, datasetId + ":Line:"));
        adapters.add(new PrefixAdapter(CourseOfJourneyRefStructure.class, datasetId + ":VehicleJourney:"));
        return adapters;
    }
}
