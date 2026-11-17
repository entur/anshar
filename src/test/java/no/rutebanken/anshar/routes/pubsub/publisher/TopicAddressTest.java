package no.rutebanken.anshar.routes.pubsub.publisher;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TopicAddressTest {

    @Test
    void parsesCamelUriWithSlashes() {
        assertEquals(Optional.of(new TopicAddress("ent-anshar-dev", "xml.estimated_timetables")),
                TopicAddress.parse("google-pubsub://ent-anshar-dev:xml.estimated_timetables"));
    }

    @Test
    void parsesCamelUriWithoutSlashes() {
        assertEquals(Optional.of(new TopicAddress("p", "t")), TopicAddress.parse("google-pubsub:p:t"));
    }

    @Test
    void ignoresQueryParameters() {
        assertEquals(Optional.of(new TopicAddress("p", "t")), TopicAddress.parse("google-pubsub://p:t?synchronousPull=true"));
    }

    @Test
    void disabledValues() {
        for (String value : new String[]{null, "", " ", "mock:ignore", "direct:x", "anshar.xml.estimated_timetables",
                "google-pubsub://onlyproject", "google-pubsub://:topic", "google-pubsub://project:"}) {
            assertEquals(Optional.empty(), TopicAddress.parse(value), String.valueOf(value));
        }
    }

    @Test
    void topicName() {
        assertEquals("projects/p/topics/t", new TopicAddress("p", "t").topicName());
    }
}
