package no.rutebanken.anshar.routes.siri.adapters;

import no.rutebanken.anshar.routes.siri.transformer.ValueAdapter;
import no.rutebanken.anshar.routes.siri.transformer.impl.UnalteredAdapter;
import no.rutebanken.anshar.subscription.SubscriptionSetup;
import uk.org.ifopt.siri20.StopPlaceRef;
import uk.org.siri.siri20.*;

import java.util.ArrayList;
import java.util.List;

@Mapping(id="entur")
public class EnturValueAdapters extends MappingAdapter {

    @Override
    public List<ValueAdapter> getValueAdapters(SubscriptionSetup subscription) {

        List<ValueAdapter> valueAdapters = new ArrayList<>();

        valueAdapters.add(new UnalteredAdapter(StopPlaceRef.class));
        valueAdapters.add(new UnalteredAdapter(StopPointRef.class));
        valueAdapters.add(new UnalteredAdapter(JourneyPlaceRefStructure.class));
        valueAdapters.add(new UnalteredAdapter(DestinationRef.class));
        valueAdapters.add(new UnalteredAdapter(LineRef.class));
        valueAdapters.add(new UnalteredAdapter(CourseOfJourneyRefStructure.class));

        return valueAdapters;
    }
}
