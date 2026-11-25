package no.rutebanken.anshar.routes.outbound;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

/**
 * Local, in-memory buffer per outbound subscription. Updates are collected for the subscription's
 * update interval and handed to the {@link Flusher} as one batch - either when the window ends or
 * when the buffer reaches the maximum size.
 * <p>
 * Every mutation of a buffer goes through the map (compute / remove(key, value)), so add, timer flush,
 * size flush and clear never lose or duplicate an object.
 */
final class OutboundBatcher {

    private static final Logger logger = LoggerFactory.getLogger(OutboundBatcher.class);

    @FunctionalInterface
    interface Flusher {
        void flush(OutboundSubscriptionSetup subscription, List<Object> objects, String breadcrumbId);
    }

    private static final class Batch {
        private final OutboundSubscriptionSetup subscription;
        private final String breadcrumbId;
        private final List<Object> objects = new ArrayList<>();
        private ScheduledFuture<?> timer;

        private Batch(OutboundSubscriptionSetup subscription, String breadcrumbId) {
            this.subscription = subscription;
            this.breadcrumbId = breadcrumbId;
        }
    }

    private final ScheduledExecutorService scheduler;
    private final Flusher flusher;
    private final int maxObjects;
    private final ConcurrentHashMap<String, Batch> batches = new ConcurrentHashMap<>();

    OutboundBatcher(ScheduledExecutorService scheduler, Flusher flusher, int maxObjects) {
        this.scheduler = scheduler;
        this.flusher = flusher;
        this.maxObjects = maxObjects;
    }

    /**
     * Adds to the subscription's buffer; the first add schedules a flush after subscription.getUpdateInterval() ms
     */
    void add(OutboundSubscriptionSetup subscription, List<?> objects, String breadcrumbId) {
        final String id = subscription.getSubscriptionId();
        final Batch[] full = new Batch[1];

        batches.compute(id, (key, existing) -> {
            Batch batch = existing;
            if (batch == null) {
                batch = new Batch(subscription, breadcrumbId);
                final Batch scheduled = batch;
                batch.timer = scheduler.schedule(() -> flushIfCurrent(key, scheduled),
                        subscription.getUpdateInterval(), TimeUnit.MILLISECONDS);
            }
            batch.objects.addAll(objects);
            if (batch.objects.size() >= maxObjects) {
                full[0] = batch;
                return null;
            }
            return batch;
        });

        if (full[0] != null) {
            cancelTimer(full[0]);
            flush(full[0]);
        }
    }

    /**
     * Discards the buffer and cancels its timer
     */
    void clear(String subscriptionId) {
        Batch removed = batches.remove(subscriptionId);
        if (removed != null) {
            cancelTimer(removed);
        }
    }

    /**
     * Discards every buffer whose subscription matches and cancels its timer. Removal is by key and value, so a
     * concurrent add, flush or clear sees each buffer either whole or not at all.
     */
    void clearIf(Predicate<OutboundSubscriptionSetup> predicate) {
        for (Map.Entry<String, Batch> entry : new ArrayList<>(batches.entrySet())) {
            Batch batch = entry.getValue();
            if (predicate.test(batch.subscription) && batches.remove(entry.getKey(), batch)) {
                cancelTimer(batch);
            }
        }
    }

    /**
     * Flushes every buffer now (shutdown)
     */
    void flushAll() {
        for (String id : new ArrayList<>(batches.keySet())) {
            Batch batch = batches.remove(id);
            if (batch != null) {
                cancelTimer(batch);
                flush(batch);
            }
        }
    }

    int bufferedObjects(String subscriptionId) {
        int[] size = new int[1];
        batches.computeIfPresent(subscriptionId, (key, batch) -> {
            size[0] = batch.objects.size();
            return batch;
        });
        return size[0];
    }

    private void flushIfCurrent(String subscriptionId, Batch batch) {
        if (batches.remove(subscriptionId, batch)) {
            flush(batch);
        }
    }

    private static void cancelTimer(Batch batch) {
        if (batch.timer != null) {
            batch.timer.cancel(false);
        }
    }

    private void flush(Batch batch) {
        try {
            flusher.flush(batch.subscription, batch.objects, batch.breadcrumbId);
        } catch (Exception e) {
            logger.error("Flushing batch for subscription {} failed", batch.subscription.getSubscriptionId(), e);
        }
    }
}
