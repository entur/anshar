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
import no.rutebanken.anshar.routes.siri.processor.BaneNorRemoveExpiredJourneysPostProcessor;
import no.rutebanken.anshar.routes.siri.processor.BaneNorRemoveFreightTrainPostProcessor;
import no.rutebanken.anshar.routes.siri.processor.BaneNorSiriEtRewriter;
import no.rutebanken.anshar.routes.siri.processor.BaneNorSiriStopAssignmentPopulater;
import no.rutebanken.anshar.routes.siri.processor.EnsureIncreasingTimesProcessor;
import no.rutebanken.anshar.routes.siri.processor.OperatorFilterPostProcessor;
import no.rutebanken.anshar.routes.siri.transformer.ValueAdapter;
import no.rutebanken.anshar.subscription.SubscriptionSetup;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Mapping(id="banenoret-flt")
public class BaneNorEtFltValueAdapters extends MappingAdapter {


    @Override
    public List<ValueAdapter> getValueAdapters(SubscriptionSetup subscriptionSetup) {

        List<ValueAdapter> valueAdapters = new ArrayList<>();

        valueAdapters.add(new BaneNorRemoveFreightTrainPostProcessor(subscriptionSetup.getDatasetId()));

        valueAdapters.add(new BaneNorIdPlatformPostProcessor(subscriptionSetup.getSubscriptionType(), subscriptionSetup.getDatasetId()));

        Map<String, String> operatorOverrideMapping = new HashMap<>();
        operatorOverrideMapping.put("NG", "GJB");

        operatorOverrideMapping.put("FLY", "FLT");
        operatorOverrideMapping.put("SJ", "SJV");
        operatorOverrideMapping.put("GAG", "GOA");
        operatorOverrideMapping.put("VY", "NSB");
        operatorOverrideMapping.put("VYG", "GJB");
        operatorOverrideMapping.put("VYT", "VYG");

        List<String> operatorsToIgnore = Arrays.asList("FLY", "FLT");


        valueAdapters.add(new OperatorFilterPostProcessor(subscriptionSetup.getDatasetId(), operatorsToIgnore, operatorOverrideMapping));

        /*
         Need to remove already expired VehicleJourneys before matching with NeTEx routedata since vehicleRef-
         values (privateCode/trainNumber) are reused for each departure-date.
         */
        valueAdapters.add(new BaneNorRemoveExpiredJourneysPostProcessor(subscriptionSetup.getDatasetId()));

        // Rewrites stop-pattern based on plan-data from NSB which not always matches
        valueAdapters.add(new BaneNorSiriEtRewriter(subscriptionSetup.getDatasetId()));

        //Populates EstimatedCalls with StopAssignments to indicate platform-changes
        valueAdapters.add(new BaneNorSiriStopAssignmentPopulater(subscriptionSetup.getDatasetId()));

        //Ensures that arrival-/departure-times are always increasing
        valueAdapters.add(new EnsureIncreasingTimesProcessor(subscriptionSetup.getDatasetId()));

        return valueAdapters;
    }
}
