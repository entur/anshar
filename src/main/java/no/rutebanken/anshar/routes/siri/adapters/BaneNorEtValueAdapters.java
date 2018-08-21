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

import no.rutebanken.anshar.routes.siri.processor.BaneNorIdPlatformPostProcessor;
import no.rutebanken.anshar.routes.siri.processor.BaneNorSiriEtRewriter;
import no.rutebanken.anshar.routes.siri.processor.BaneNorSiriStopAssignmentPopulater;
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

//        valueAdapters.add(new BaneNorArrivalDepartureCancellationProcessor());
        valueAdapters.add(new BaneNorSiriEtRewriter());
        valueAdapters.add(new BaneNorSiriStopAssignmentPopulater());

        return valueAdapters;
    }
}
