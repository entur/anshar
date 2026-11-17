package no.rutebanken.anshar.routes.pubsub.publisher;

import com.google.api.core.ApiFuture;
import com.google.pubsub.v1.PubsubMessage;

import java.time.Duration;

/** A Pub/Sub topic messages are published to without waiting for the answer */
public interface TopicSink extends AutoCloseable {

    Duration CLOSE_TIMEOUT = Duration.ofSeconds(30);

    /** @return completes with the message id once Pub/Sub has accepted the message, or fails */
    ApiFuture<String> publish(PubsubMessage message);

    /** Stops accepting messages and starts sending what is still queued, without waiting */
    void startShutdown();

    /** @return whether everything queued was sent within the timeout */
    boolean awaitShutdown(Duration timeout);

    /** Sends what is still queued, then releases the connection */
    @Override
    default void close() {
        startShutdown();
        awaitShutdown(CLOSE_TIMEOUT);
    }
}
