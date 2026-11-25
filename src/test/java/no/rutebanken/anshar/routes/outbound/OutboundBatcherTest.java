package no.rutebanken.anshar.routes.outbound;

import no.rutebanken.anshar.subscription.SiriDataType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutboundBatcherTest {

    private record Flush(OutboundSubscriptionSetup subscription, List<Object> objects, String breadcrumbId) {}

    private ScheduledExecutorService scheduler;
    private final List<Flush> flushes = Collections.synchronizedList(new ArrayList<>());
    private final List<ScheduledFuture<?>> futures = new ArrayList<>();
    private boolean failFlush;
    private OutboundBatcher batcher;

    @BeforeEach
    void setUp() {
        scheduler = mock(ScheduledExecutorService.class);
        when(scheduler.schedule(any(Runnable.class), anyLong(), any(TimeUnit.class))).thenAnswer(inv -> {
            ScheduledFuture<?> future = mock(ScheduledFuture.class);
            futures.add(future);
            return future;
        });
        batcher = new OutboundBatcher(scheduler, this::capture, 1000);
    }

    private void capture(OutboundSubscriptionSetup subscription, List<Object> objects, String breadcrumbId) {
        if (failFlush) {
            throw new IllegalStateException("flush failed");
        }
        flushes.add(new Flush(subscription, objects, breadcrumbId));
    }

    private static OutboundSubscriptionSetup sub(String id, long interval) {
        OutboundSubscriptionSetup s = new OutboundSubscriptionSetup(SiriDataType.ESTIMATED_TIMETABLE, "http://localhost/x", 100, new ArrayList<>(), id);
        s.setUpdateInterval(interval);
        return s;
    }

    private List<Runnable> scheduledTasks() {
        ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler, atLeast(0)).schedule(captor.capture(), anyLong(), any(TimeUnit.class));
        return captor.getAllValues();
    }

    @Test
    void addsWithinWindowAreFlushedOnceInOrder() {
        OutboundSubscriptionSetup s = sub("s1", 2000);
        batcher.add(s, List.of("a"), "b1");
        batcher.add(s, List.of("b", "c"), "b2");
        assertTrue(flushes.isEmpty());
        verify(scheduler, times(1)).schedule(any(Runnable.class), eq(2000L), eq(MILLISECONDS));
        assertEquals(3, batcher.bufferedObjects("s1"));

        scheduledTasks().get(0).run();

        assertEquals(1, flushes.size());
        assertEquals(List.of("a", "b", "c"), flushes.get(0).objects());
        assertEquals("b1", flushes.get(0).breadcrumbId());
        assertEquals(0, batcher.bufferedObjects("s1"));
    }

    @Test
    void newWindowStartsAfterFlush() {
        OutboundSubscriptionSetup s = sub("s1", 2000);
        batcher.add(s, List.of("a"), "b1");
        scheduledTasks().get(0).run();
        batcher.add(s, List.of("b"), "b2");

        verify(scheduler, times(2)).schedule(any(Runnable.class), eq(2000L), eq(MILLISECONDS));
        scheduledTasks().get(1).run();
        assertEquals(2, flushes.size());
        assertEquals(List.of("b"), flushes.get(1).objects());
        assertEquals("b2", flushes.get(1).breadcrumbId());
    }

    @Test
    void flushesWhenMaxSizeReached() {
        batcher = new OutboundBatcher(scheduler, this::capture, 3);
        OutboundSubscriptionSetup s = new OutboundSubscriptionSetup(ZonedDateTime.now(), SiriDataType.ESTIMATED_TIMETABLE,
                "http://localhost/x", 0, 0, null, new ArrayList<>(), "s1", "s1", ZonedDateTime.now().plusHours(1), "TST", null, null);
        s.setUpdateInterval(2000);

        batcher.add(s, List.of("a", "b"), "b1");
        assertTrue(flushes.isEmpty());
        batcher.add(s, List.of("c", "d"), "b2");

        // A full batch is sent at once - the rest waits for the window
        assertEquals(1, flushes.size());
        assertEquals(List.of("a", "b", "c"), flushes.get(0).objects());
        assertEquals("b1", flushes.get(0).breadcrumbId());
        assertEquals(1, batcher.bufferedObjects("s1"));
        verify(futures.get(0), never()).cancel(false);

        scheduledTasks().get(0).run();
        assertEquals(2, flushes.size());
        assertEquals(List.of("d"), flushes.get(1).objects());
        assertEquals(0, batcher.bufferedObjects("s1"));
    }

    @Test
    void nonPositiveMaxSizeMeansNoSizeLimit() {
        for (int maxSize : new int[]{0, -1}) {
            flushes.clear();
            batcher = new OutboundBatcher(scheduler, this::capture, maxSize);
            String id = "s" + maxSize;

            assertDoesNotThrow(() -> batcher.add(sub(id, 2000), List.of("a", "b", "c"), "b1"));

            assertTrue(flushes.isEmpty());
            assertEquals(3, batcher.bufferedObjects(id));
        }
    }

    @Test
    void largeAddIsFlushedInBatchesOfMaxSize() {
        batcher = new OutboundBatcher(scheduler, this::capture, 3);

        batcher.add(sub("s1", 2000), List.of(1, 2, 3, 4, 5, 6, 7), "b1");

        assertEquals(2, flushes.size());
        assertEquals(List.of(1, 2, 3), flushes.get(0).objects());
        assertEquals(List.of(4, 5, 6), flushes.get(1).objects());
        assertEquals(1, batcher.bufferedObjects("s1"));

        batcher.add(sub("s1", 2000), List.of(8, 9), "b2");

        assertEquals(3, flushes.size());
        assertEquals(List.of(7, 8, 9), flushes.get(2).objects());
        assertEquals(0, batcher.bufferedObjects("s1"));
        verify(futures.get(0)).cancel(false);

        scheduledTasks().get(0).run();
        assertEquals(3, flushes.size());
    }

    @Test
    void clearDiscardsBufferAndCancelsTimer() {
        batcher.add(sub("s1", 2000), List.of("a"), "b1");
        batcher.clear("s1");

        assertEquals(0, batcher.bufferedObjects("s1"));
        verify(futures.get(0)).cancel(false);
        scheduledTasks().get(0).run();
        assertTrue(flushes.isEmpty());
    }

    @Test
    void clearIfRemovesOnlyMatchingBatchesAndCancelsTimers() {
        batcher.add(sub("s1", 2000), List.of("a"), "b1");
        batcher.add(sub("s2", 3000), List.of("b"), "b2");
        batcher.add(sub("s3", 2000), List.of("c"), "b3");

        batcher.clearIf(s -> s.getUpdateInterval() == 2000);

        assertEquals(0, batcher.bufferedObjects("s1"));
        assertEquals(1, batcher.bufferedObjects("s2"));
        assertEquals(0, batcher.bufferedObjects("s3"));
        verify(futures.get(0)).cancel(false);
        verify(futures.get(2)).cancel(false);
        verify(futures.get(1), never()).cancel(false);

        scheduledTasks().forEach(Runnable::run);
        assertEquals(1, flushes.size());
        assertEquals("s2", flushes.get(0).subscription().getSubscriptionId());
    }

    @Test
    void flushAllFlushesEveryBuffer() {
        batcher.add(sub("s1", 2000), List.of("a"), "b1");
        batcher.add(sub("s2", 2000), List.of("b"), "b2");

        batcher.flushAll();

        assertEquals(2, flushes.size());
        assertEquals(0, batcher.bufferedObjects("s1"));
        assertEquals(0, batcher.bufferedObjects("s2"));
        scheduledTasks().forEach(Runnable::run);
        assertEquals(2, flushes.size());
    }

    @Test
    void flusherExceptionIsSwallowed() {
        OutboundSubscriptionSetup s = sub("s1", 2000);
        failFlush = true;
        batcher.add(s, List.of("a"), "b1");
        assertDoesNotThrow(() -> scheduledTasks().get(0).run());

        failFlush = false;
        batcher.add(s, List.of("b"), "b2");
        scheduledTasks().get(1).run();
        assertEquals(1, flushes.size());
        assertEquals(List.of("b"), flushes.get(0).objects());
    }

    @Test
    void concurrentAddsAreSentExactlyOnce() throws Exception {
        ScheduledThreadPoolExecutor real = new ScheduledThreadPoolExecutor(2);
        List<Object> sent = Collections.synchronizedList(new ArrayList<>());
        OutboundBatcher concurrent = new OutboundBatcher(real, (sub, objects, breadcrumb) -> sent.addAll(objects), 50);
        OutboundSubscriptionSetup s = sub("s1", 5);

        int threads = 8;
        int perThread = 1000;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger errors = new AtomicInteger();
        Set<Integer> expected = new HashSet<>();
        for (int t = 0; t < threads; t++) {
            final int base = t * perThread;
            for (int i = 0; i < perThread; i++) {
                expected.add(base + i);
            }
            new Thread(() -> {
                try {
                    start.await();
                    for (int i = 0; i < perThread; i++) {
                        concurrent.add(s, List.of(base + i), "b");
                    }
                } catch (Throwable e) {
                    errors.incrementAndGet();
                } finally {
                    done.countDown();
                }
            }).start();
        }
        start.countDown();
        assertTrue(done.await(30, TimeUnit.SECONDS));
        assertEquals(0, errors.get());

        concurrent.flushAll();
        real.shutdown();
        assertTrue(real.awaitTermination(10, TimeUnit.SECONDS));

        assertEquals(threads * perThread, sent.size());
        assertEquals(expected, new HashSet<>(sent));
        assertEquals(0, concurrent.bufferedObjects("s1"));
    }
}
