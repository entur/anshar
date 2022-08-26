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

package no.rutebanken.anshar.subscription.helpers;

import no.rutebanken.anshar.routes.siri.handlers.OutboundIdMappingPolicy;
import no.rutebanken.anshar.routes.siri.processor.CodespaceOutboundProcessor;
import no.rutebanken.anshar.routes.siri.processor.RemoveEmojiPostProcessor;
import no.rutebanken.anshar.routes.siri.processor.RuterOutboundDatedVehicleRefAdapter;
import no.rutebanken.anshar.routes.siri.transformer.ValueAdapter;
import no.rutebanken.anshar.routes.siri.transformer.impl.OutboundIdAdapter;
import no.rutebanken.anshar.subscription.SiriDataType;
import uk.org.ifopt.siri21.StopPlaceRef;
import uk.org.siri.siri21.CourseOfJourneyRefStructure;
import uk.org.siri.siri21.DestinationRef;
import uk.org.siri.siri21.JourneyPlaceRefStructure;
import uk.org.siri.siri21.LineRef;
import uk.org.siri.siri21.RequestorRef;
import uk.org.siri.siri21.StopPointRefStructure;

import java.util.ArrayList;
import java.util.List;

public class MappingAdapterPresets {

    public static List<ValueAdapter> getOutboundAdapters(SiriDataType dataType, OutboundIdMappingPolicy outboundIdMappingPolicy) {
        List<ValueAdapter> adapters = new ArrayList<>();
        adapters.add(new OutboundIdAdapter(StopPointRefStructure.class, outboundIdMappingPolicy));
        adapters.add(new OutboundIdAdapter(LineRef.class, outboundIdMappingPolicy));
        adapters.add(new CodespaceOutboundProcessor(outboundIdMappingPolicy));

        switch (dataType) {
            case ESTIMATED_TIMETABLE:
                adapters.add(new OutboundIdAdapter(JourneyPlaceRefStructure.class, outboundIdMappingPolicy));
                adapters.add(new OutboundIdAdapter(DestinationRef.class, outboundIdMappingPolicy));
                break;
            case VEHICLE_MONITORING:
                adapters.add(new OutboundIdAdapter(JourneyPlaceRefStructure.class, outboundIdMappingPolicy));
                adapters.add(new OutboundIdAdapter(DestinationRef.class, outboundIdMappingPolicy));
                adapters.add(new OutboundIdAdapter(CourseOfJourneyRefStructure.class, outboundIdMappingPolicy));
                adapters.add(new RuterOutboundDatedVehicleRefAdapter(MappingAdapterPresets.class, outboundIdMappingPolicy));
                break;
            case SITUATION_EXCHANGE:
                adapters.add(new OutboundIdAdapter(RequestorRef.class, outboundIdMappingPolicy));
                adapters.add(new OutboundIdAdapter(StopPlaceRef.class, outboundIdMappingPolicy));
                adapters.add(new RemoveEmojiPostProcessor(outboundIdMappingPolicy));
                break;
            default:
                return getOutboundAdapters(outboundIdMappingPolicy);
        }
        return adapters;
    }

    public static List<ValueAdapter> getOutboundAdapters(OutboundIdMappingPolicy outboundIdMappingPolicy) {
        List<ValueAdapter> adapters = new ArrayList<>();
        adapters.add(new OutboundIdAdapter(LineRef.class, outboundIdMappingPolicy));
        adapters.add(new OutboundIdAdapter(StopPointRefStructure.class, outboundIdMappingPolicy));
        adapters.add(new OutboundIdAdapter(StopPlaceRef.class, outboundIdMappingPolicy));
        adapters.add(new OutboundIdAdapter(JourneyPlaceRefStructure.class, outboundIdMappingPolicy));
        adapters.add(new OutboundIdAdapter(DestinationRef.class, outboundIdMappingPolicy));
        adapters.add(new OutboundIdAdapter(CourseOfJourneyRefStructure.class, outboundIdMappingPolicy));

        //Adapter for SIRI-SX ParticipantRef
        adapters.add(new OutboundIdAdapter(RequestorRef.class, outboundIdMappingPolicy));

        //Adding postprocessor for Ruter DatedVehicleRef
        adapters.add(new RuterOutboundDatedVehicleRefAdapter(MappingAdapterPresets.class, outboundIdMappingPolicy));

        // Adding postprocessor for removing emojis etc. from SX-messages
        adapters.add(new RemoveEmojiPostProcessor(outboundIdMappingPolicy));

        // Postprocessor to set "correct" datasource/codespaceId
        adapters.add(new CodespaceOutboundProcessor(outboundIdMappingPolicy));
        return adapters;
    }
}
