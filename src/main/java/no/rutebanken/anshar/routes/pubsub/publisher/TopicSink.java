package no.rutebanken.anshar.routes.pubsub.publisher;

import com.google.api.core.ApiFuture;
import com.google.pubsub.v1.PubsubMessage;

/** A Pub/Sub topic messages are published to without waiting for the answer */
public interface TopicSink extends AutoCloseable {

    /** @return completes with the message id once Pub/Sub has accepted the message, or fails */
    ApiFuture<String> publish(PubsubMessage message);

    /** Sends what is still queued, then releases the connection */
    @Override
    void close();
}
