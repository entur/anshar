package no.rutebanken.anshar.subscription;

import no.rutebanken.anshar.routes.siri.transformer.ValueAdapter;
import no.rutebanken.anshar.routes.siri.transformer.impl.LeftPaddingAdapter;
import no.rutebanken.anshar.routes.siri.transformer.impl.RightPaddingStopPlaceAdapter;
import no.rutebanken.anshar.routes.siri.transformer.impl.RuterSubstringAdapter;
import uk.org.ifopt.siri20.StopPlaceRef;
import uk.org.siri.siri20.DestinationRef;
import uk.org.siri.siri20.JourneyPlaceRefStructure;
import uk.org.siri.siri20.LineRef;
import uk.org.siri.siri20.StopPointRef;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MappingAdapterPresets {

    public static final Map<Preset, List<ValueAdapter>> adapterPresets = new HashMap<>();

    static {
        List<ValueAdapter> ruter;
        List<ValueAdapter> atb_kol;

        ruter = new ArrayList<>();
        ruter.add(new LeftPaddingAdapter(LineRef.class, 4, '0'));

        ruter.add(new RuterSubstringAdapter(StopPointRef.class, ':', '0', 2));
        ruter.add(new LeftPaddingAdapter(StopPointRef.class, 10, '0'));

        ruter.add(new LeftPaddingAdapter(StopPlaceRef.class, 8, '0'));
        ruter.add(new RightPaddingStopPlaceAdapter(StopPlaceRef.class, 8, "01"));


        atb_kol = new ArrayList<>();
        //StopPointRef
        atb_kol.add(new LeftPaddingAdapter(StopPointRef.class, 8, '0'));
        atb_kol.add(new RightPaddingStopPlaceAdapter(StopPointRef.class, 8, "01"));

        //OriginRef
        atb_kol.add(new LeftPaddingAdapter(JourneyPlaceRefStructure.class, 8, '0'));
        atb_kol.add(new RightPaddingStopPlaceAdapter(JourneyPlaceRefStructure.class, 8, "01"));

        //DestinationRef
        atb_kol.add(new LeftPaddingAdapter(DestinationRef.class, 8, '0'));
        atb_kol.add(new RightPaddingStopPlaceAdapter(DestinationRef.class, 8, "01"));

        adapterPresets.put(Preset.RUTER, ruter);
        adapterPresets.put(Preset.ATB, atb_kol);
        adapterPresets.put(Preset.KOLUMBUS, atb_kol);
    }

    public enum Preset {
        RUTER, ATB, KOLUMBUS
    }
}
