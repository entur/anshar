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

/**
 * Stops posting to an outbound subscription that keeps failing, to avoid a constant stream of requests that fail anyway.
 *
 * After {@code failureThreshold} consecutive failures the circuit opens, and deliveries are skipped. When the backoff
 * has passed, a single delivery is let through to probe the recipient - success closes the circuit, failure opens it
 * again with doubled backoff (up to {@code maxBackoffMillis}). A probe that has not completed within
 * {@code maxProbeDurationMillis} is abandoned, and a new probe is allowed.
 *
 * State is local to each pod, and is replaced when the subscription is re-created (i.e. gets a newer requestTimestamp).
 */
class OutboundCircuitBreaker {

    /**
     * Permission to post a delivery - must be passed back when recording the result
     */
    static final class Permit {
        static final Permit DENIED = new Permit(false, false, 0);
        static final Permit ALLOWED = new Permit(true, false, 0);

        private final boolean allowed;
        private final boolean probe;
        private final long probeStartedAt;

        private Permit(boolean allowed, boolean probe, long probeStartedAt) {
            this.allowed = allowed;
            this.probe = probe;
            this.probeStartedAt = probeStartedAt;
        }

        boolean isAllowed() {
            return allowed;
        }

        boolean isProbe() {
            return probe;
        }
    }

    private final ZonedDateTime subscriptionRequestTimestamp;
    private final int failureThreshold;
    private final long initialBackoffMillis;
    private final long maxBackoffMillis;
    private final long maxProbeDurationMillis;

    private int consecutiveFailures;
    private long backoffMillis;
    private long openUntil;
    private boolean probeInProgress;
    private long probeStartedAt;
    private long skippedDeliveries;

    OutboundCircuitBreaker(ZonedDateTime subscriptionRequestTimestamp, int failureThreshold, long initialBackoffMillis,
                           long maxBackoffMillis, long maxProbeDurationMillis) {
        this.subscriptionRequestTimestamp = subscriptionRequestTimestamp;
        this.failureThreshold = failureThreshold;
        this.initialBackoffMillis = initialBackoffMillis;
        this.maxBackoffMillis = maxBackoffMillis;
        this.maxProbeDurationMillis = maxProbeDurationMillis;
    }

    /**
     * @return true if the subscription has been re-created after this circuit breaker was created
     */
    boolean isReplacedBy(OutboundSubscriptionSetup subscription) {
        final ZonedDateTime requestTimestamp = subscription.getRequestTimestamp();
        return requestTimestamp != null &&
                (subscriptionRequestTimestamp == null || requestTimestamp.isAfter(subscriptionRequestTimestamp));
    }

    /**
     * @return true if the subscription is a previous version of the one this circuit breaker was created for
     */
    boolean isNewerThan(OutboundSubscriptionSetup subscription) {
        final ZonedDateTime requestTimestamp = subscription.getRequestTimestamp();
        return requestTimestamp != null && subscriptionRequestTimestamp != null &&
                requestTimestamp.isBefore(subscriptionRequestTimestamp);
    }

    synchronized Permit allowRequest(long now) {
        if (!isOpen()) {
            return Permit.ALLOWED;
        }
        final boolean probeRunning = probeInProgress && now - probeStartedAt < maxProbeDurationMillis;
        if (now < openUntil || probeRunning) {
            skippedDeliveries++;
            return Permit.DENIED;
        }
        probeInProgress = true;
        probeStartedAt = now;
        return new Permit(true, true, now);
    }

    /**
     * Any successful delivery closes the circuit - the recipient is responding
     *
     * @return number of deliveries skipped while the circuit was open, or -1 if the circuit was not open
     */
    synchronized long recordSuccess() {
        final boolean wasOpen = isOpen();
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
    synchronized boolean recordFailure(long now, Permit permit) {
        final boolean wasOpen = isOpen();
        final boolean isCurrentProbe = permit.isProbe() && probeInProgress && permit.probeStartedAt == probeStartedAt;
        if (isCurrentProbe) {
            probeInProgress = false;
        }

        consecutiveFailures++;
        if (!isOpen() || (wasOpen && !isCurrentProbe)) {
            // Not failed enough yet - or already open, and this is not the probe (e.g. requests that were in flight when the circuit opened)
            return false;
        }
        backoffMillis = backoffMillis == 0 ? initialBackoffMillis : Math.min(backoffMillis * 2, maxBackoffMillis);
        openUntil = now + backoffMillis;
        return true;
    }

    private boolean isOpen() {
        return consecutiveFailures >= failureThreshold;
    }

    synchronized int getConsecutiveFailures() {
        return consecutiveFailures;
    }

    synchronized long getBackoffMillis() {
        return backoffMillis;
    }
}
