package no.rutebanken.anshar.siri.transformer;

import no.rutebanken.anshar.routes.siri.transformer.impl.PrefixAdapter;
import org.junit.Test;
import uk.org.siri.siri20.LineRef;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class PrefixAdapterTest {

    @Test
    public void testPrefixEmptyString() throws Exception {
        PrefixAdapter adapter = new PrefixAdapter(LineRef.class,"ATB.Line.");
        assertEquals("", adapter.apply(""));
    }
    @Test
    public void testPrefixString() throws Exception {
        PrefixAdapter adapter = new PrefixAdapter(LineRef.class,"ATB.Line.");
        assertEquals("ATB.Line.12", adapter.apply("12"));
    }

    @Test
    public void testDuplicatePrefixString() throws Exception {
        PrefixAdapter adapter = new PrefixAdapter(LineRef.class,"ATB.Line.");
        assertEquals("ATB.Line.12", adapter.apply("ATB.Line.12"));
    }

    @Test
    public void testPrefixNullString() throws Exception {
        PrefixAdapter adapter = new PrefixAdapter(LineRef.class,"ATB.Line.");
        assertNull(adapter.apply(null));
    }
}
