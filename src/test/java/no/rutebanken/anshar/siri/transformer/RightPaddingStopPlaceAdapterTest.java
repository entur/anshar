package no.rutebanken.anshar.siri.transformer;

import no.rutebanken.anshar.routes.siri.transformer.impl.RightPaddingStopPlaceAdapter;
import org.junit.Before;
import org.junit.Test;
import uk.org.siri.siri20.JourneyPlaceRefStructure;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class RightPaddingStopPlaceAdapterTest {

    private RightPaddingStopPlaceAdapter adapter;

    @Before
    public void setUp() throws Exception {
        adapter = new RightPaddingStopPlaceAdapter(JourneyPlaceRefStructure.class, 8, "01");
    }

    @Test
    public void testLeftPadEmptyString() throws Exception {
        assertEquals("", adapter.apply(""));
    }
    @Test
    public void testPadShortString() throws Exception {
        assertEquals("1234", adapter.apply("1234"));
    }
    @Test
    public void testPadFullLengthString() throws Exception {
        assertEquals("1234123401", adapter.apply("12341234"));

    }
    @Test
    public void testPadLongString() throws Exception {
        assertEquals("1234123499", adapter.apply("1234123499"));

    }
    @Test
    public void testPadNullString() throws Exception {
        assertNull(adapter.apply(null));
    }
}
