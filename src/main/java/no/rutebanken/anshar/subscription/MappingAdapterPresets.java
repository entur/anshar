package no.rutebanken.anshar.subscription;

import no.rutebanken.anshar.routes.siri.transformer.ValueAdapter;
import no.rutebanken.anshar.routes.siri.transformer.impl.*;
import org.springframework.stereotype.Component;
import uk.org.ifopt.siri20.StopPlaceRef;
import uk.org.siri.siri20.*;

import java.util.ArrayList;
import java.util.List;

@Component
public class MappingAdapterPresets {

    public List<ValueAdapter> getOutboundAdapters(boolean mapped) {
        List<ValueAdapter> adapters = new ArrayList<>();
        adapters.add(new OutboundIdAdapter(LineRef.class, mapped));
        adapters.add(new OutboundIdAdapter(StopPointRef.class, mapped));
        adapters.add(new OutboundIdAdapter(StopPlaceRef.class, mapped));
        adapters.add(new OutboundIdAdapter(JourneyPlaceRefStructure.class, mapped));
        adapters.add(new OutboundIdAdapter(DestinationRef.class, mapped));
        adapters.add(new OutboundIdAdapter(CourseOfJourneyRefStructure.class, mapped));
        return adapters;
    }

    public List<ValueAdapter> get(SubscriptionPreset preset) {

        List<ValueAdapter> adapters = new ArrayList<>();
        switch (preset) {
            case RUTER:
                adapters.add(new LeftPaddingAdapter(LineRef.class, 4, '0'));
                adapters.add(new RuterSubstringAdapter(StopPointRef.class, ':', '0', 2));
                adapters.add(new LeftPaddingAdapter(StopPointRef.class, 10, '0'));
                adapters.add(new LeftPaddingAdapter(StopPlaceRef.class, 8, '0'));
                adapters.add(new RightPaddingStopPlaceAdapter(StopPlaceRef.class, 8, "01"));
                adapters.add(new RuterSubstringAdapter(JourneyPlaceRefStructure.class, ':', '0', 2));
                adapters.add(new RuterSubstringAdapter(DestinationRef.class, ':', '0', 2));
                break;
            case ATB:
            case KOLUMBUS:
            case AKT:
                adapters.add(new LeftPaddingAdapter(LineRef.class, 4, '0'));

                adapters.add(new LeftPaddingAdapter(StopPointRef.class, 8, '0'));
                adapters.add(new RightPaddingStopPlaceAdapter(StopPointRef.class, 8, "01"));

                //OriginRef
                adapters.add(new LeftPaddingAdapter(JourneyPlaceRefStructure.class, 8, '0'));
                adapters.add(new RightPaddingStopPlaceAdapter(JourneyPlaceRefStructure.class, 8, "01"));

                //DestinationRef
                adapters.add(new LeftPaddingAdapter(DestinationRef.class, 8, '0'));
                adapters.add(new RightPaddingStopPlaceAdapter(DestinationRef.class, 8, "01"));
                break;
            case BRAKAR:
                adapters.add(new LeftPaddingAdapter(StopPointRef.class, 10, '0'));
                break;
            case SKYSS:
        }

        return adapters;
    }

    public List<ValueAdapter> createNsrIdMappingAdapters(List<String> idMappingPrefixes) {
        List<ValueAdapter> nsr = new ArrayList<>();
        nsr.add(new StopPlaceRegisterMapper(StopPlaceRef.class, idMappingPrefixes));
        nsr.add(new StopPlaceRegisterMapper(StopPointRef.class, idMappingPrefixes));
        nsr.add(new StopPlaceRegisterMapper(JourneyPlaceRefStructure.class, idMappingPrefixes));
        nsr.add(new StopPlaceRegisterMapper(DestinationRef.class, idMappingPrefixes));
        return nsr;
    }


    public List<ValueAdapter> createIdPrefixAdapters(String datasetId) {
         List<ValueAdapter> adapters = new ArrayList<>();
        adapters.add(new PrefixAdapter(LineRef.class, datasetId + ".Line."));
        adapters.add(new PrefixAdapter(CourseOfJourneyRefStructure.class, datasetId + ".VehicleJourney."));
        return adapters;
    }

}
