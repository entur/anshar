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

import no.rutebanken.anshar.routes.siri.transformer.ValueAdapter;
import no.rutebanken.anshar.routes.siri.transformer.impl.LeftPaddingAdapter;
import no.rutebanken.anshar.routes.siri.transformer.impl.RuterSubstringAdapter;
import no.rutebanken.anshar.subscription.SubscriptionSetup;
import uk.org.ifopt.siri20.StopPlaceRef;
import uk.org.siri.siri20.DestinationRef;
import uk.org.siri.siri20.JourneyPlaceRefStructure;
import uk.org.siri.siri20.StopPointRef;

import java.util.ArrayList;
import java.util.List;

@Mapping(id="ruter")
public class RuterValueAdapters extends MappingAdapter {


    @Override
    public List<ValueAdapter> getValueAdapters(SubscriptionSetup subscriptionSetup) {

        List<ValueAdapter> valueAdapters = new ArrayList<>();
                valueAdapters.add(new LeftPaddingAdapter(StopPlaceRef.class, 8, '0'));
                valueAdapters.add(new RuterSubstringAdapter(StopPointRef.class, ':', '0', 2));
                valueAdapters.add(new RuterSubstringAdapter(JourneyPlaceRefStructure.class, ':', '0', 2));
                valueAdapters.add(new RuterSubstringAdapter(DestinationRef.class, ':', '0', 2));
//                valueAdapters.add(new RuterDatedVehicleRefPostProcessor());

        valueAdapters.addAll(createNsrIdMappingAdapters(subscriptionSetup.getSubscriptionType(), subscriptionSetup.getDatasetId(), subscriptionSetup.getIdMappingPrefixes()));

        List<ValueAdapter> datasetPrefix = createIdPrefixAdapters("RUT");
        if (!subscriptionSetup.getMappingAdapters().containsAll(datasetPrefix)) {
            subscriptionSetup.getMappingAdapters().addAll(datasetPrefix);
        }

        return valueAdapters;
    }
}
