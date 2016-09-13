package no.rutebanken.anshar.siri.transformer;

import no.rutebanken.anshar.routes.siri.transformer.impl.LeftPaddingAdapter;
import org.junit.Before;
import org.junit.Test;
import uk.org.siri.siri20.LineRef;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class LeftPaddingAdapterTest {

    private LeftPaddingAdapter adapter;

    @Before
    public void setUp() throws Exception {
        adapter = new LeftPaddingAdapter(LineRef.class, 4, '0');
    }

    @Test
    public void testLeftPadEmptyString() throws Exception {
        assertEquals("", adapter.apply(""));
    }
    @Test
    public void testLeftPadShortString() throws Exception {
        assertEquals("0012", adapter.apply("12"));
    }
    @Test
    public void testLeftPadFullLengthString() throws Exception {
        assertEquals("1234", adapter.apply("1234"));

    }
    @Test
    public void testLeftPadLongString() throws Exception {
        assertEquals("1234567890", adapter.apply("1234567890"));

    }
    @Test
    public void testLeftPadNullString() throws Exception {
        assertNull(adapter.apply(null));
    }
}
