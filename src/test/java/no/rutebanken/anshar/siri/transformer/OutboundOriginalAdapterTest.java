package no.rutebanken.anshar.siri.transformer;

import no.rutebanken.anshar.routes.siri.transformer.SiriValueTransformer;
import no.rutebanken.anshar.routes.siri.transformer.impl.OutboundOriginalAdapter;
import org.junit.Test;
import uk.org.siri.siri20.LineRef;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNull;

public class OutboundOriginalAdapterTest {

    @Test
    public void testEmptyString() throws Exception {
        OutboundOriginalAdapter adapter = new OutboundOriginalAdapter(LineRef.class, false);
        assertEquals("", adapter.apply(""));
    }
    @Test
    public void testGetOriginalValueString() throws Exception {
        OutboundOriginalAdapter adapter = new OutboundOriginalAdapter(LineRef.class,false);
        String originalId = "1234";
        String mappedId = "ATB.Line.1234";
        assertEquals(originalId, adapter.apply(originalId + SiriValueTransformer.SEPARATOR + mappedId));
    }
    @Test
    public void testGetMappedValueString() throws Exception {
        OutboundOriginalAdapter adapter = new OutboundOriginalAdapter(LineRef.class,true);
        String originalId = "1234";
        String mappedId = "ATB.Line.1234";
        assertEquals(mappedId, adapter.apply(originalId + SiriValueTransformer.SEPARATOR + mappedId));
    }

    @Test
    public void testPrefixNullString() throws Exception {
        OutboundOriginalAdapter adapter = new OutboundOriginalAdapter(LineRef.class,false);
        assertNull(adapter.apply(null));
    }
}
