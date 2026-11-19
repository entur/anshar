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

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The time a post may take grows with the body, so a full dataset is not cut off by a timeout meant for small deliveries */
class RequestTimeoutTest {

    private static final long REQUEST_TIMEOUT_MILLIS = 5000;
    private static final long MBIT_PER_SECOND = 25;
    private static final long MAX_SECONDS = 30;

    private static Duration timeout(long bodyBytes) {
        return CamelRouteManager.requestTimeout(bodyBytes, REQUEST_TIMEOUT_MILLIS, MBIT_PER_SECOND, MAX_SECONDS);
    }

    @Test
    void smallBodyGetsTheRequestTimeout() {
        assertEquals(Duration.ofMillis(5000), timeout(0));
        // A heartbeat or a few changes: well under a millisecond to upload
        assertEquals(Duration.ofMillis(5000), timeout(500));
        assertEquals(Duration.ofMillis(5016), timeout(50_000));
    }

    @Test
    void largeDeliveryGetsTimeToUpload() {
        // One part of a split full dataset: 6 MB at 25 Mbit/s takes 1.92 s
        assertEquals(Duration.ofMillis(5000 + 1_920), timeout(6_000_000));
        // 60 MB takes 19.2 s - still under the cap
        assertEquals(Duration.ofMillis(5000 + 19_200), timeout(60_000_000));
    }

    @Test
    void timeoutIsCapped() {
        // An unsplit full dataset of 120 MB would need 43.4 s
        assertEquals(Duration.ofSeconds(MAX_SECONDS), timeout(120_000_000));
        assertEquals(Duration.ofSeconds(MAX_SECONDS), timeout(1_000_000_000));
    }

    @Test
    void noThroughputSetAddsNoUploadTime() {
        assertEquals(Duration.ofMillis(5000), CamelRouteManager.requestTimeout(120_000_000, 5000, 0, MAX_SECONDS));
        assertEquals(Duration.ofMillis(5000), CamelRouteManager.requestTimeout(120_000_000, 5000, -1, MAX_SECONDS));
    }

    @Test
    void capNeverShortensTheRequestTimeout() {
        assertEquals(Duration.ofMillis(5000), CamelRouteManager.requestTimeout(1_000_000_000, 5000, MBIT_PER_SECOND, 1));
    }
}
