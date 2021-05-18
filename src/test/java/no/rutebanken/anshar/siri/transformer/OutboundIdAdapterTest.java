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

package no.rutebanken.anshar.siri.transformer;

import no.rutebanken.anshar.routes.siri.handlers.OutboundIdMappingPolicy;
import no.rutebanken.anshar.routes.siri.transformer.SiriValueTransformer;
import no.rutebanken.anshar.routes.siri.transformer.impl.OutboundIdAdapter;
import org.junit.jupiter.api.Test;
import uk.org.siri.siri20.LineRef;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class OutboundIdAdapterTest {

    @Test
    public void testEmptyString() throws Exception {
        OutboundIdAdapter adapter = new OutboundIdAdapter(LineRef.class, OutboundIdMappingPolicy.ORIGINAL_ID);
        assertEquals("", adapter.apply(""));
    }
    @Test
    public void testGetOriginalValueString() throws Exception {
        OutboundIdAdapter adapter = new OutboundIdAdapter(LineRef.class, OutboundIdMappingPolicy.ORIGINAL_ID);
        String originalId = "1234";
        String mappedId = "ATB:Line:1234";
        String completeValue = originalId + SiriValueTransformer.SEPARATOR + mappedId;
        assertEquals(originalId, adapter.apply(completeValue));
        assertEquals(originalId, OutboundIdAdapter.getOriginalId(completeValue));
    }

    @Test
    public void testGetMappedValueString() throws Exception {
        OutboundIdAdapter adapter = new OutboundIdAdapter(LineRef.class, OutboundIdMappingPolicy.DEFAULT);
        String originalId = "1234";
        String mappedId = "ATB:Line:1234";
        String completeValue = originalId + SiriValueTransformer.SEPARATOR + mappedId;
        assertEquals(mappedId, adapter.apply(completeValue));
        assertEquals(mappedId, OutboundIdAdapter.getMappedId(completeValue));
    }

    @Test
    public void testPrefixNullString() throws Exception {
        OutboundIdAdapter adapter = new OutboundIdAdapter(LineRef.class, OutboundIdMappingPolicy.DEFAULT);
        assertNull(adapter.apply(null));
    }
}
