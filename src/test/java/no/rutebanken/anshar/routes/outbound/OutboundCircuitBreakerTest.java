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

package no.rutebanken.anshar.routes.outbound;

import no.rutebanken.anshar.subscription.SiriDataType;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class OutboundCircuitBreakerTest {

    private static final int THRESHOLD = 3;
    private static final long INITIAL_BACKOFF = 1000;
    private static final long MAX_BACKOFF = 3000;

    private final OutboundCircuitBreaker breaker = new OutboundCircuitBreaker(null, THRESHOLD, INITIAL_BACKOFF, MAX_BACKOFF);

    @Test
    public void testOpensAfterConsecutiveFailures() {
        long now = 0;
        assertFalse(breaker.recordFailure(now));
        assertFalse(breaker.recordFailure(now));
        assertTrue(breaker.allowRequest(now));

        assertTrue(breaker.recordFailure(now));
        assertFalse(breaker.allowRequest(now));
        assertFalse(breaker.allowRequest(now + INITIAL_BACKOFF - 1));
    }

    @Test
    public void testSuccessResetsFailureCount() {
        breaker.recordFailure(0);
        breaker.recordFailure(0);
        assertEquals(-1, breaker.recordSuccess());

        assertFalse(breaker.recordFailure(0));
        assertTrue(breaker.allowRequest(0));
    }

    @Test
    public void testSingleProbeAfterBackoff() {
        openCircuit(0);

        long afterBackoff = INITIAL_BACKOFF;
        assertTrue(breaker.allowRequest(afterBackoff));
        // Only one probe at a time
        assertFalse(breaker.allowRequest(afterBackoff));
    }

    @Test
    public void testFailedProbeDoublesBackoffUpToMax() {
        openCircuit(0);
        assertEquals(INITIAL_BACKOFF, breaker.getBackoffMillis());

        long now = INITIAL_BACKOFF;
        assertTrue(breaker.allowRequest(now));
        assertTrue(breaker.recordFailure(now));
        assertEquals(2 * INITIAL_BACKOFF, breaker.getBackoffMillis());

        now += 2 * INITIAL_BACKOFF;
        assertTrue(breaker.allowRequest(now));
        assertTrue(breaker.recordFailure(now));
        assertEquals(MAX_BACKOFF, breaker.getBackoffMillis());

        now += MAX_BACKOFF;
        assertTrue(breaker.allowRequest(now));
        assertTrue(breaker.recordFailure(now));
        assertEquals(MAX_BACKOFF, breaker.getBackoffMillis());
    }

    @Test
    public void testFailuresWhileOpenDoNotExtendBackoff() {
        openCircuit(0);
        // E.g. requests that were in flight when the circuit opened
        assertFalse(breaker.recordFailure(10));
        assertEquals(INITIAL_BACKOFF, breaker.getBackoffMillis());
        assertTrue(breaker.allowRequest(INITIAL_BACKOFF));
    }

    @Test
    public void testSuccessfulProbeClosesCircuit() {
        openCircuit(0);
        breaker.allowRequest(1);
        breaker.allowRequest(2);

        assertTrue(breaker.allowRequest(INITIAL_BACKOFF));
        // One skipped when opening the circuit, plus two
        assertEquals(3, breaker.recordSuccess());

        assertEquals(0, breaker.getConsecutiveFailures());
        assertTrue(breaker.allowRequest(INITIAL_BACKOFF));
        assertTrue(breaker.allowRequest(INITIAL_BACKOFF));
    }

    @Test
    public void testBelongsToSubscriptionWithSameRequestTimestamp() {
        ZonedDateTime created = ZonedDateTime.now();
        OutboundCircuitBreaker breaker = new OutboundCircuitBreaker(created, THRESHOLD, INITIAL_BACKOFF, MAX_BACKOFF);

        assertTrue(breaker.belongsTo(subscription(created)));
        assertFalse(breaker.belongsTo(subscription(created.plusSeconds(1))));
    }

    private void openCircuit(long now) {
        for (int i = 0; i < THRESHOLD; i++) {
            breaker.recordFailure(now);
        }
        assertFalse(breaker.allowRequest(now));
    }

    private static OutboundSubscriptionSetup subscription(ZonedDateTime requestTimestamp) {
        return new OutboundSubscriptionSetup(requestTimestamp, SiriDataType.VEHICLE_MONITORING, "http://localhost/siri",
                60000, 0, null, new ArrayList<>(), "id", "ref", requestTimestamp.plusHours(1), null, null, null);
    }
}
