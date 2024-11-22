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

import com.google.common.base.Objects;
import no.rutebanken.anshar.routes.siri.handlers.OutboundIdMappingPolicy;
import no.rutebanken.anshar.routes.siri.processor.CodespaceOutboundProcessor;
import no.rutebanken.anshar.routes.siri.processor.RemoveEmojiPostProcessor;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MappingAdapterPresets {

    private static Map<CacheKey, List<ValueAdapter>> adapterCache = new HashMap<>();

    public static List<ValueAdapter> getOutboundAdapters(SiriDataType dataType, OutboundIdMappingPolicy outboundIdMappingPolicy) {
        CacheKey key = new CacheKey(dataType, outboundIdMappingPolicy);
        if (!adapterCache.containsKey(key)) {
            List<ValueAdapter> adapters = new ArrayList<>();
            adapters.add(new OutboundIdAdapter(StopPointRefStructure.class, outboundIdMappingPolicy));
            adapters.add(new OutboundIdAdapter(LineRef.class, outboundIdMappingPolicy));
            adapters.add(new CodespaceOutboundProcessor(outboundIdMappingPolicy));

            switch (dataType) {
                case ESTIMATED_TIMETABLE:
                case VEHICLE_MONITORING:
                    adapters.add(new OutboundIdAdapter(JourneyPlaceRefStructure.class, outboundIdMappingPolicy));
                    adapters.add(new OutboundIdAdapter(DestinationRef.class, outboundIdMappingPolicy));
                    break;
                case SITUATION_EXCHANGE:
                    adapters.add(new OutboundIdAdapter(RequestorRef.class, outboundIdMappingPolicy));
                    adapters.add(new OutboundIdAdapter(StopPlaceRef.class, outboundIdMappingPolicy));
                    adapters.add(new RemoveEmojiPostProcessor(outboundIdMappingPolicy));
                    break;
                default:
                    adapters = getOutboundAdapters(outboundIdMappingPolicy);
            }
            adapterCache.put(key, adapters);
        }
        return adapterCache.get(key);
    }

    public static List<ValueAdapter> getOutboundAdapters(OutboundIdMappingPolicy outboundIdMappingPolicy) {
        CacheKey key = new CacheKey(null, outboundIdMappingPolicy);
        if (!adapterCache.containsKey(key)) {
            List<ValueAdapter> adapters = new ArrayList<>();
            adapters.add(new OutboundIdAdapter(LineRef.class, outboundIdMappingPolicy));
            adapters.add(new OutboundIdAdapter(StopPointRefStructure.class, outboundIdMappingPolicy));
            adapters.add(new OutboundIdAdapter(StopPlaceRef.class, outboundIdMappingPolicy));
            adapters.add(new OutboundIdAdapter(JourneyPlaceRefStructure.class, outboundIdMappingPolicy));
            adapters.add(new OutboundIdAdapter(DestinationRef.class, outboundIdMappingPolicy));
            adapters.add(new OutboundIdAdapter(CourseOfJourneyRefStructure.class, outboundIdMappingPolicy));

            //Adapter for SIRI-SX ParticipantRef
            adapters.add(new OutboundIdAdapter(RequestorRef.class, outboundIdMappingPolicy));

            // Adding postprocessor for removing emojis etc. from SX-messages
            adapters.add(new RemoveEmojiPostProcessor(outboundIdMappingPolicy));

            // Postprocessor to set "correct" datasource/codespaceId
            adapters.add(new CodespaceOutboundProcessor(outboundIdMappingPolicy));

            adapterCache.put(key, adapters);
        }
        return adapterCache.get(key);
    }
}
class CacheKey {
    private final SiriDataType dataType;
    private final OutboundIdMappingPolicy outboundIdMappingPolicy;

    public CacheKey(SiriDataType dataType, OutboundIdMappingPolicy outboundIdMappingPolicy) {
        this.dataType = dataType;
        this.outboundIdMappingPolicy = outboundIdMappingPolicy;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CacheKey cacheKey = (CacheKey) o;
        return dataType == cacheKey.dataType && outboundIdMappingPolicy == cacheKey.outboundIdMappingPolicy;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(dataType, outboundIdMappingPolicy);
    }
}