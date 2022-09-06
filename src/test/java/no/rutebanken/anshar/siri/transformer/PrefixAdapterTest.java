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

import no.rutebanken.anshar.routes.siri.transformer.impl.PrefixAdapter;
import no.rutebanken.anshar.subscription.SiriDataType;
import org.junit.jupiter.api.Test;
import uk.org.siri.siri21.LineRef;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class PrefixAdapterTest {

    @Test
    public void testPrefixEmptyString() throws Exception {
        PrefixAdapter adapter = new PrefixAdapter(SiriDataType.VEHICLE_MONITORING, "TST", LineRef.class,"ATB.Line.");
        assertEquals("", adapter.apply(""));
    }
    @Test
    public void testPrefixString() throws Exception {
        PrefixAdapter adapter = new PrefixAdapter(SiriDataType.VEHICLE_MONITORING, "TST", LineRef.class,"ATB.Line.");
        assertEquals("ATB.Line.12", adapter.apply("12"));
    }

    @Test
    public void testDuplicatePrefixString() throws Exception {
        PrefixAdapter adapter = new PrefixAdapter(SiriDataType.VEHICLE_MONITORING, "TST", LineRef.class,"ATB.Line.");
        assertEquals("ATB.Line.12", adapter.apply("ATB.Line.12"));
    }

    @Test
    public void testPrefixNullString() throws Exception {
        PrefixAdapter adapter = new PrefixAdapter(SiriDataType.VEHICLE_MONITORING, "TST", LineRef.class,"ATB.Line.");
        assertNull(adapter.apply(null));
    }
}
