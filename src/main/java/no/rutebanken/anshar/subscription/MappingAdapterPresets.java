package no.rutebanken.anshar.subscription;

import no.rutebanken.anshar.routes.siri.handlers.OutboundIdMappingPolicy;
import no.rutebanken.anshar.routes.siri.processor.BaneNorIdPlatformPostProcessor;
import no.rutebanken.anshar.routes.siri.transformer.ValueAdapter;
import no.rutebanken.anshar.routes.siri.transformer.impl.*;
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
        return adapters;
    }

    public List<ValueAdapter> get(SubscriptionPreset preset) {

        List<ValueAdapter> adapters = new ArrayList<>();
        switch (preset) {
            case RUTER:
                adapters.add(new LeftPaddingAdapter(StopPlaceRef.class, 8, '0'));
                adapters.add(new RuterSubstringAdapter(StopPointRef.class, ':', '0', 2));
                adapters.add(new RuterSubstringAdapter(JourneyPlaceRefStructure.class, ':', '0', 2));
                adapters.add(new RuterSubstringAdapter(DestinationRef.class, ':', '0', 2));
                break;
            case ATB:
            case KOLUMBUS:
            case AKT:
                adapters.add(new LeftPaddingAdapter(LineRef.class, 4, '0'));
                break;
            case BRAKAR:
                break;
            case TROMS:
                break;
            case SKYSS:
                break;
            case BANENOR:
                adapters.add(new BaneNorIdPlatformPostProcessor());
                break;
            case NSB://
                adapters.add(new LeftPaddingAdapter(StopPointRef.class, 9, '0'));
                adapters.add(new LeftPaddingAdapter(StopPlaceRef.class, 9, '0'));
                break;
            case MOR:
                break;
        }

        return adapters;
    }

    public List<ValueAdapter> createNsrIdMappingAdapters(List<String> idMappingPrefixes) {
        List<ValueAdapter> nsr = new ArrayList<>();
        nsr.add(new StopPlaceRegisterMapper(StopPlaceRef.class, idMappingPrefixes, "StopPlace"));
        nsr.add(new StopPlaceRegisterMapper(StopPointRef.class, idMappingPrefixes));
        nsr.add(new StopPlaceRegisterMapper(JourneyPlaceRefStructure.class, idMappingPrefixes));
        nsr.add(new StopPlaceRegisterMapper(DestinationRef.class, idMappingPrefixes));
        return nsr;
    }


    public List<ValueAdapter> createIdPrefixAdapters(String datasetId) {
         List<ValueAdapter> adapters = new ArrayList<>();
        adapters.add(new PrefixAdapter(LineRef.class, datasetId + ":Line:"));
        adapters.add(new PrefixAdapter(CourseOfJourneyRefStructure.class, datasetId + ":VehicleJourney:"));
        return adapters;
    }

}
