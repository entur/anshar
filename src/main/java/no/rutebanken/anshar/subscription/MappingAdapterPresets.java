package no.rutebanken.anshar.subscription;

import no.rutebanken.anshar.routes.Constants;
import no.rutebanken.anshar.routes.siri.handlers.OutboundIdMappingPolicy;
import no.rutebanken.anshar.routes.siri.processor.RuterOutboundDatedVehicleRefAdapter;
import no.rutebanken.anshar.routes.siri.transformer.ValueAdapter;
import no.rutebanken.anshar.routes.siri.transformer.impl.OutboundIdAdapter;
import org.springframework.stereotype.Component;
import uk.org.ifopt.siri20.StopPlaceRef;
import uk.org.siri.siri20.*;

import java.util.ArrayList;
import java.util.List;

@Component
public class MappingAdapterPresets {

    public List<ValueAdapter> getOutboundAdapters(OutboundIdMappingPolicy outboundIdMappingPolicy) {
        List<ValueAdapter> adapters = new ArrayList<>();
        adapters.add(new OutboundIdAdapter(LineRef.class, outboundIdMappingPolicy));
        adapters.add(new OutboundIdAdapter(StopPointRef.class, outboundIdMappingPolicy));
        adapters.add(new OutboundIdAdapter(StopPlaceRef.class, outboundIdMappingPolicy));
        adapters.add(new OutboundIdAdapter(JourneyPlaceRefStructure.class, outboundIdMappingPolicy));
        adapters.add(new OutboundIdAdapter(DestinationRef.class, outboundIdMappingPolicy));
        adapters.add(new OutboundIdAdapter(CourseOfJourneyRefStructure.class, outboundIdMappingPolicy));

        //Adding postprocessor for Ruter DatedVehicleRef
        adapters.add(new RuterOutboundDatedVehicleRefAdapter(Constants.class, outboundIdMappingPolicy));
        return adapters;
    }
}
