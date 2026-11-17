package no.rutebanken.anshar.routes.pubsub.publisher;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** Connects to the topics the outbound topic properties name */
public interface TopicSinkFactory {

    /** @return a sink for the configured topic, or empty when the value does not name a Pub/Sub topic */
    Optional<TopicSink> create(String configuredTopic);

    @Component
    class PubsubTopicSinkFactory implements TopicSinkFactory {

        @Value("${anshar.outbound.pubsub.emulator.host:}")
        private String emulatorHost;

        @Override
        public Optional<TopicSink> create(String configuredTopic) {
            Optional<String> emulator = emulatorHost == null || emulatorHost.isBlank() ? Optional.empty() : Optional.of(emulatorHost);
            return TopicAddress.parse(configuredTopic).map(address -> new PubsubTopicSink(address, emulator));
        }
    }
}
