package no.rutebanken.anshar.routes.pubsub.publisher;

import java.util.Optional;

/** Connects to the topics the outbound topic properties name */
public interface TopicSinkFactory {

    /** @return a sink for the configured topic, or empty when the value does not name a Pub/Sub topic */
    Optional<TopicSink> create(String configuredTopic);
}
