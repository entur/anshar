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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InitialDeliveryResendTest {

    private static final long INITIAL_BACKOFF = 1000;
    private static final long MAX_BACKOFF = 3000;
    private static final long MAX_DURATION = 10000;

    private final InitialDeliveryResend resend = new InitialDeliveryResend(INITIAL_BACKOFF, MAX_BACKOFF, MAX_DURATION);

    @Test
    void nothingToResendUntilRequested() {
        assertFalse(resend.start(0));

        resend.request();
        assertTrue(resend.start(0));
    }

    @Test
    void onlyOneResendAtATime() {
        resend.request();
        assertTrue(resend.start(0));

        // E.g. the circuit closes again while the resend is running
        resend.request();
        assertFalse(resend.start(1));

        resend.succeeded();
        assertTrue(resend.start(2));
    }

    @Test
    void successfulResendIsNotRepeated() {
        resend.request();
        assertTrue(resend.start(0));
        resend.succeeded();

        assertFalse(resend.start(1));
        assertFalse(resend.start(MAX_BACKOFF * 10));
    }

    @Test
    void failedResendWaitsForBackoffThenIsRetried() {
        resend.request();
        assertTrue(resend.start(0));
        assertEquals(INITIAL_BACKOFF, resend.failed(100));

        // Small deliveries getting through do not start it again before the backoff has passed
        assertFalse(resend.start(100 + INITIAL_BACKOFF - 1));
        // Still pending - started by the first success after the backoff
        assertTrue(resend.start(100 + INITIAL_BACKOFF));
    }

    @Test
    void backoffDoublesUpToMaxAndResetsOnSuccess() {
        resend.request();
        long now = 0;
        long[] expected = {INITIAL_BACKOFF, 2 * INITIAL_BACKOFF, MAX_BACKOFF, MAX_BACKOFF};
        for (long backoff : expected) {
            assertTrue(resend.start(now));
            assertEquals(backoff, resend.failed(now));
            now += backoff;
        }

        assertTrue(resend.start(now));
        resend.succeeded();
        resend.request();
        assertTrue(resend.start(now));
        assertEquals(INITIAL_BACKOFF, resend.failed(now));
    }

    @Test
    void resendThatNeverReportsBackIsAbandoned() {
        resend.request();
        assertTrue(resend.start(0));
        resend.request();

        assertFalse(resend.start(MAX_DURATION - 1));
        assertTrue(resend.start(MAX_DURATION));
    }
}
