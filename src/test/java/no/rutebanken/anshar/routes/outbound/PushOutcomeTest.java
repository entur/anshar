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

import no.rutebanken.anshar.routes.outbound.CamelRouteManager.DroppableTask;
import no.rutebanken.anshar.routes.outbound.CamelRouteManager.Outcome;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** How a push is judged, and that a push dropped from a full queue is told */
class PushOutcomeTest {

    @Test
    void responseCodes() {
        assertEquals(Outcome.DELIVERED, Outcome.of(200));
        assertEquals(Outcome.DELIVERED, Outcome.of(204));
        // Would be rejected again
        assertEquals(Outcome.REJECTED, Outcome.of(400));
        assertEquals(Outcome.REJECTED, Outcome.of(413));
        // May succeed later
        assertEquals(Outcome.FAILED, Outcome.of(408));
        assertEquals(Outcome.FAILED, Outcome.of(425));
        assertEquals(Outcome.FAILED, Outcome.of(429));
        assertEquals(Outcome.FAILED, Outcome.of(500));
        assertEquals(Outcome.FAILED, Outcome.of(503));
    }

    @Test
    void pushWithSeveralPostsGetsTheWorstOutcome() {
        assertEquals(Outcome.DELIVERED, Outcome.DELIVERED.worst(Outcome.DELIVERED));
        assertEquals(Outcome.REJECTED, Outcome.DELIVERED.worst(Outcome.REJECTED));
        assertEquals(Outcome.FAILED, Outcome.REJECTED.worst(Outcome.FAILED));
        assertEquals(Outcome.FAILED, Outcome.FAILED.worst(Outcome.DELIVERED));
    }

    @Test
    void droppedPushIsToldAndNewOneRuns() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        ThreadPoolExecutor executor = new ThreadPoolExecutor(1, 1, 1, TimeUnit.SECONDS, new ArrayBlockingQueue<>(1),
                CamelRouteManager::discardOldest);
        try {
            // Occupies the only thread, so the next task waits in the queue
            executor.execute(() -> awaitQuietly(release));

            AtomicBoolean queuedRan = new AtomicBoolean();
            AtomicBoolean queuedDropped = new AtomicBoolean();
            executor.execute(new DroppableTask() {
                @Override
                public void run() {
                    queuedRan.set(true);
                }

                @Override
                public void dropped() {
                    queuedDropped.set(true);
                }
            });

            CountDownLatch newRan = new CountDownLatch(1);
            // Queue is full - the oldest queued task is dropped to make room
            executor.execute(newRan::countDown);
            assertTrue(queuedDropped.get(), "The dropped task should be told");

            release.countDown();
            assertTrue(newRan.await(5, TimeUnit.SECONDS), "The new task should run");
            assertFalse(queuedRan.get(), "The dropped task should not run");
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
