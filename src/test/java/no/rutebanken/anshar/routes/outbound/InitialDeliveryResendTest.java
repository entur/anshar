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

import static no.rutebanken.anshar.routes.outbound.InitialDeliveryResend.NOT_STARTED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class InitialDeliveryResendTest {

    private static final long INITIAL_BACKOFF = 1000;
    private static final long MAX_BACKOFF = 3000;
    private static final long MAX_DURATION = 10000;

    private final InitialDeliveryResend resend = new InitialDeliveryResend(INITIAL_BACKOFF, MAX_BACKOFF, MAX_DURATION);

    private long started(long now) {
        long attempt = resend.start(now);
        assertNotEquals(NOT_STARTED, attempt, "should start at " + now);
        return attempt;
    }

    private void notStarted(long now) {
        assertEquals(NOT_STARTED, resend.start(now), "should not start at " + now);
    }

    @Test
    void nothingToResendUntilRequested() {
        notStarted(0);

        resend.request();
        started(0);
    }

    @Test
    void onlyOneResendAtATime() {
        resend.request();
        long attempt = started(0);

        // E.g. the circuit closes again while the resend is running
        resend.request();
        notStarted(1);

        resend.succeeded(attempt);
        started(2);
    }

    @Test
    void successfulResendIsNotRepeated() {
        resend.request();
        resend.succeeded(started(0));

        notStarted(1);
        notStarted(MAX_BACKOFF * 10);
    }

    @Test
    void failedResendWaitsForBackoffThenIsRetried() {
        resend.request();
        assertEquals(INITIAL_BACKOFF, resend.failed(started(0), 100));

        // Small deliveries getting through do not start it again before the backoff has passed
        notStarted(100 + INITIAL_BACKOFF - 1);
        // Still pending - started by the first success after the backoff
        started(100 + INITIAL_BACKOFF);
    }

    @Test
    void backoffDoublesUpToMaxAndResetsOnSuccess() {
        resend.request();
        long now = 0;
        long[] expected = {INITIAL_BACKOFF, 2 * INITIAL_BACKOFF, MAX_BACKOFF, MAX_BACKOFF};
        for (long backoff : expected) {
            assertEquals(backoff, resend.failed(started(now), now));
            now += backoff;
        }

        resend.succeeded(started(now));
        resend.request();
        assertEquals(INITIAL_BACKOFF, resend.failed(started(now), now));
    }

    @Test
    void resendThatNeverReportsBackIsStartedAgain() {
        resend.request();
        started(0);

        // Not requested again, but the recipient still needs the data
        notStarted(MAX_DURATION - 1);
        started(MAX_DURATION);
    }

    @Test
    void reportFromAnEarlierAttemptIsIgnored() {
        resend.request();
        long lost = started(0);
        long current = started(MAX_DURATION);

        // The attempt considered lost reports back late - the current one is still running
        assertEquals(-1, resend.failed(lost, MAX_DURATION + 1));
        resend.succeeded(lost);
        resend.request();
        notStarted(MAX_DURATION + 2);

        assertEquals(INITIAL_BACKOFF, resend.failed(current, MAX_DURATION + 3));
    }
}
