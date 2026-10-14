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

import java.time.ZonedDateTime;
import java.util.Objects;

/**
 * Stops posting to an outbound subscription that keeps failing, to avoid a constant stream of requests that fail anyway.
 *
 * After {@code failureThreshold} consecutive failures the circuit opens, and deliveries are skipped. When the backoff
 * has passed, a single delivery is let through to probe the recipient - success closes the circuit, failure opens it
 * again with doubled backoff (up to {@code maxBackoffMillis}).
 *
 * State is local to each pod, and is reset when the subscription is re-created (i.e. gets a new requestTimestamp).
 */
class OutboundCircuitBreaker {

    private final ZonedDateTime subscriptionRequestTimestamp;
    private final int failureThreshold;
    private final long initialBackoffMillis;
    private final long maxBackoffMillis;

    private int consecutiveFailures;
    private long backoffMillis;
    private long openUntil;
    private boolean probeInProgress;
    private long skippedDeliveries;

    OutboundCircuitBreaker(ZonedDateTime subscriptionRequestTimestamp, int failureThreshold, long initialBackoffMillis, long maxBackoffMillis) {
        this.subscriptionRequestTimestamp = subscriptionRequestTimestamp;
        this.failureThreshold = failureThreshold;
        this.initialBackoffMillis = initialBackoffMillis;
        this.maxBackoffMillis = maxBackoffMillis;
    }

    boolean belongsTo(OutboundSubscriptionSetup subscription) {
        return Objects.equals(subscriptionRequestTimestamp, subscription.getRequestTimestamp());
    }

    /**
     * @return true if the delivery should be posted, false if it should be skipped
     */
    synchronized boolean allowRequest(long now) {
        if (consecutiveFailures < failureThreshold) {
            return true;
        }
        if (now < openUntil || probeInProgress) {
            skippedDeliveries++;
            return false;
        }
        probeInProgress = true;
        return true;
    }

    /**
     * @return number of deliveries skipped while the circuit was open, or -1 if the circuit was not open
     */
    synchronized long recordSuccess() {
        final boolean wasOpen = consecutiveFailures >= failureThreshold;
        final long skipped = skippedDeliveries;

        consecutiveFailures = 0;
        backoffMillis = 0;
        openUntil = 0;
        probeInProgress = false;
        skippedDeliveries = 0;

        return wasOpen ? skipped : -1;
    }

    /**
     * @return true if this failure opened the circuit
     */
    synchronized boolean recordFailure(long now) {
        probeInProgress = false;
        consecutiveFailures++;
        if (consecutiveFailures < failureThreshold || now < openUntil) {
            // Not failed enough yet - or already open, e.g. requests that were in flight when the circuit opened
            return false;
        }
        backoffMillis = backoffMillis == 0 ? initialBackoffMillis : Math.min(backoffMillis * 2, maxBackoffMillis);
        openUntil = now + backoffMillis;
        return true;
    }

    synchronized int getConsecutiveFailures() {
        return consecutiveFailures;
    }

    synchronized long getBackoffMillis() {
        return backoffMillis;
    }
}
