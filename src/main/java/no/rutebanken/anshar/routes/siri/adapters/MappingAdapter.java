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
import no.rutebanken.anshar.routes.siri.transformer.impl.PrefixAdapter;
import no.rutebanken.anshar.routes.siri.transformer.impl.StopPlaceRegisterMapper;
import no.rutebanken.anshar.subscription.SiriDataType;
import no.rutebanken.anshar.subscription.SubscriptionSetup;
import uk.org.ifopt.siri21.StopPlaceRef;
import uk.org.siri.siri21.CourseOfJourneyRefStructure;
import uk.org.siri.siri21.DestinationRef;
import uk.org.siri.siri21.JourneyPlaceRefStructure;
import uk.org.siri.siri21.LineRef;
import uk.org.siri.siri21.StopPointRefStructure;

import java.util.ArrayList;
import java.util.List;

public abstract class MappingAdapter {

    public abstract List<ValueAdapter> getValueAdapters(SubscriptionSetup subscriptionSetup);

//    public List<ValueAdapter> getOutboundValueAdapters(OutboundIdMappingPolicy mappingPolicy) {
//        return MappingAdapterPresets.getOutboundAdapters(mappingPolicy);
//    }
    List<ValueAdapter> createNsrIdMappingAdapters(SiriDataType type, String datasetId, List<String> idMappingPrefixes) {
        List<ValueAdapter> nsr = new ArrayList<>();
        nsr.add(new StopPlaceRegisterMapper(type, datasetId, StopPlaceRef.class, idMappingPrefixes, "StopPlace"));
        nsr.add(new StopPlaceRegisterMapper(type, datasetId, StopPointRefStructure.class, idMappingPrefixes));
        nsr.add(new StopPlaceRegisterMapper(type, datasetId, JourneyPlaceRefStructure.class, idMappingPrefixes));
        nsr.add(new StopPlaceRegisterMapper(type, datasetId, DestinationRef.class, idMappingPrefixes));
        return nsr;
    }


    public List<ValueAdapter> createIdPrefixAdapters(SubscriptionSetup subscriptionSetup) {
        List<ValueAdapter> adapters = new ArrayList<>();
        String datasetId = subscriptionSetup.getDatasetId();
        final SiriDataType dataType = subscriptionSetup.getSubscriptionType();
        adapters.add(new PrefixAdapter(dataType, datasetId, LineRef.class, datasetId + ":Line:"));
        adapters.add(new PrefixAdapter(dataType, datasetId, CourseOfJourneyRefStructure.class, datasetId + ":VehicleJourney:"));
        return adapters;
    }
}
