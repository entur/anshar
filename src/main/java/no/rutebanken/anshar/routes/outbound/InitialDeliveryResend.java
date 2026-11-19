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

/**
 * When to resend all current data to a subscriber that has missed deliveries.
 * <p>
 * A resend that fails is retried after a backoff that doubles each time (up to {@code maxBackoffMillis}), instead of
 * on the next successful delivery. A full dataset takes long to build and send, and a recipient that cannot take it in
 * time would otherwise get it rebuilt and resent every time a small delivery gets through. A resend still pending after
 * a failure is never dropped: it is started on the first successful delivery after the backoff.
 * <p>
 * State is local to each pod. Owned by the {@link OutboundCircuitBreaker} of the subscription, so it is reset when the
 * subscription is re-created.
 */
class InitialDeliveryResend {

    /** Returned by {@link #start(long)} when no resend should be started */
    static final long NOT_STARTED = 0;

    private final long initialBackoffMillis;
    private final long maxBackoffMillis;
    /**
     * A resend that has neither posted anything nor reported back within this time is considered lost, e.g. dropped
     * from a full queue, and is started again
     */
    private final long maxDurationMillis;

    private boolean pending;
    private boolean inProgress;
    /** Number of the latest attempt - a report from an earlier one, e.g. one considered lost, is ignored */
    private long attempt;
    /** When the current attempt started, or last completed a post */
    private long lastProgressAt;
    private long backoffMillis;
    private long notBefore;

    InitialDeliveryResend(long initialBackoffMillis, long maxBackoffMillis, long maxDurationMillis) {
        this.initialBackoffMillis = initialBackoffMillis;
        this.maxBackoffMillis = maxBackoffMillis;
        this.maxDurationMillis = maxDurationMillis;
    }

    /** The recipient has missed deliveries and needs all current data */
    synchronized void request() {
        pending = true;
    }

    /**
     * @return the number of the attempt if a resend should be started now, otherwise {@link #NOT_STARTED} - the
     * caller must report the result of the attempt with {@link #succeeded(long)} or {@link #failed(long, long)}
     */
    synchronized long start(long now) {
        if (inProgress) {
            if (now - lastProgressAt < maxDurationMillis) {
                return NOT_STARTED;
            }
            // Never reported back - the recipient still needs the data
            inProgress = false;
            pending = true;
        }
        if (!pending || now < notBefore) {
            return NOT_STARTED;
        }
        pending = false;
        inProgress = true;
        lastProgressAt = now;
        return ++attempt;
    }

    /** The attempt completed a post - a resend split into many posts is not lost while it keeps making progress */
    synchronized void progressed(long attempt, long now) {
        if (isCurrent(attempt)) {
            lastProgressAt = now;
        }
    }

    /** The attempt got through, or the recipient rejected it and would reject it again - not retried */
    synchronized void succeeded(long attempt) {
        if (!isCurrent(attempt)) {
            return;
        }
        inProgress = false;
        backoffMillis = 0;
        notBefore = 0;
    }

    /** The attempt was not sent, e.g. skipped by the circuit breaker - started again without adding to the backoff */
    synchronized void skipped(long attempt) {
        if (!isCurrent(attempt)) {
            return;
        }
        inProgress = false;
        pending = true;
    }

    /**
     * @return milliseconds until the resend may be tried again, or -1 if the attempt is no longer the current one
     */
    synchronized long failed(long attempt, long now) {
        if (!isCurrent(attempt)) {
            return -1;
        }
        inProgress = false;
        pending = true;
        backoffMillis = backoffMillis == 0 ? initialBackoffMillis : Math.min(backoffMillis * 2, maxBackoffMillis);
        notBefore = now + backoffMillis;
        return backoffMillis;
    }

    private boolean isCurrent(long attempt) {
        return inProgress && attempt == this.attempt;
    }
}
