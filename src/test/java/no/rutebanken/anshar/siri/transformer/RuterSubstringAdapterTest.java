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

import no.rutebanken.anshar.routes.siri.transformer.impl.RuterSubstringAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.org.siri.siri21.LineRef;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class RuterSubstringAdapterTest {

    private RuterSubstringAdapter adapter;

    @BeforeEach
    public void setUp() throws Exception {
        adapter = new RuterSubstringAdapter(LineRef.class, ':', '0', 2);
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
        assertEquals("1234123401", adapter.apply("12341234:1"));
    }

    @Test
    public void testDoubleDigitAfterSeparator() throws Exception {
        assertEquals("301061930", adapter.apply("3010619:30"));
    }
}
