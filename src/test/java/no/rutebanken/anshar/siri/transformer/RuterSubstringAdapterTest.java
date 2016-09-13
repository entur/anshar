package no.rutebanken.anshar.siri.transformer;

import no.rutebanken.anshar.routes.siri.transformer.impl.RuterSubstringAdapter;
import org.junit.Before;
import org.junit.Test;
import uk.org.siri.siri20.LineRef;

import static org.junit.Assert.assertEquals;

public class RuterSubstringAdapterTest {

    private RuterSubstringAdapter adapter;

    @Before
    public void setUp() throws Exception {
        adapter = new RuterSubstringAdapter(LineRef.class, ':');
    }

    @Test
    public void testLeftPadEmptyString() throws Exception {
        assertEquals("", adapter.apply(""));
    }

    @Test
    public void testLeftPadNullString() throws Exception {
        assertEquals(null, adapter.apply(null));
    }

    @Test
    public void testLeftPadNotMatchingString() throws Exception {
        assertEquals("12341234", adapter.apply("12341234"));
    }

    @Test
    public void testLeftPadExpectedString() throws Exception {
        assertEquals("12341234", adapter.apply("12341234:1"));
    }

    @Test
    public void testLeftPadMultipleMatchesString() throws Exception {
        assertEquals("12341234", adapter.apply("12341234:123:12:1"));
    }
}
