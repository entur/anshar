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

import no.rutebanken.anshar.routes.siri.transformer.impl.LeftPaddingAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.org.siri.siri21.LineRef;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class LeftPaddingAdapterTest {

    private LeftPaddingAdapter adapter;

    @BeforeEach
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
