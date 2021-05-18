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

import no.rutebanken.anshar.routes.siri.transformer.impl.RightPaddingStopPlaceAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.org.siri.siri20.JourneyPlaceRefStructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class RightPaddingStopPlaceAdapterTest {

    private RightPaddingStopPlaceAdapter adapter;

    @BeforeEach
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
