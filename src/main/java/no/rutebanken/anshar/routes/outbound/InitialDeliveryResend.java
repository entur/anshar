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

    private final long initialBackoffMillis;
    private final long maxBackoffMillis;
    /** A resend that has not reported back within this time is considered lost, e.g. its executor was shut down */
    private final long maxDurationMillis;

    private boolean pending;
    private boolean inProgress;
    private long startedAt;
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
     * @return true if a resend should be started now - the caller must then report the result with
     * {@link #succeeded()} or {@link #failed(long)}
     */
    synchronized boolean start(long now) {
        boolean running = inProgress && now - startedAt < maxDurationMillis;
        if (!pending || running || now < notBefore) {
            return false;
        }
        pending = false;
        inProgress = true;
        startedAt = now;
        return true;
    }

    synchronized void succeeded() {
        inProgress = false;
        backoffMillis = 0;
        notBefore = 0;
    }

    /**
     * @return milliseconds until the resend may be tried again
     */
    synchronized long failed(long now) {
        inProgress = false;
        pending = true;
        backoffMillis = backoffMillis == 0 ? initialBackoffMillis : Math.min(backoffMillis * 2, maxBackoffMillis);
        notBefore = now + backoffMillis;
        return backoffMillis;
    }
}
