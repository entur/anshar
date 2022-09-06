/*
 * Licensed under the EUPL, Version 1.2 or – as soon they will be approved by
 * the European Commission - subsequent versions of the EUPL (the "Licence");
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 *
 *   https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and
 * limitations under the Licence.
 */

package no.rutebanken.anshar.routes.siri.adapters;

import no.rutebanken.anshar.routes.siri.processor.OperatorFilterPostProcessor;
import no.rutebanken.anshar.routes.siri.transformer.ValueAdapter;
import no.rutebanken.anshar.routes.siri.transformer.impl.UnalteredAdapter;
import no.rutebanken.anshar.subscription.SubscriptionSetup;
import uk.org.ifopt.siri21.StopPlaceRef;
import uk.org.siri.siri21.CourseOfJourneyRefStructure;
import uk.org.siri.siri21.DestinationRef;
import uk.org.siri.siri21.JourneyPlaceRefStructure;
import uk.org.siri.siri21.LineRef;
import uk.org.siri.siri21.StopPointRefStructure;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

@Mapping(id="entur-flt")
public class EnturFltValueAdapters extends MappingAdapter {

    @Override
    public List<ValueAdapter> getValueAdapters(SubscriptionSetup subscription) {

        List<ValueAdapter> valueAdapters = new ArrayList<>();

        valueAdapters.add(new UnalteredAdapter(StopPlaceRef.class));
        valueAdapters.add(new UnalteredAdapter(StopPointRefStructure.class));
        valueAdapters.add(new UnalteredAdapter(JourneyPlaceRefStructure.class));
        valueAdapters.add(new UnalteredAdapter(DestinationRef.class));
        valueAdapters.add(new UnalteredAdapter(LineRef.class));
        valueAdapters.add(new UnalteredAdapter(CourseOfJourneyRefStructure.class));

        List<String> operatorsToIgnore = Arrays.asList("FLY", "FLT");
        valueAdapters.add(new OperatorFilterPostProcessor(subscription.getDatasetId(), operatorsToIgnore, new HashMap()));

        return valueAdapters;
    }
}
