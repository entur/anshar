package no.rutebanken.anshar.routes.pubsub.publisher;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A Pub/Sub topic, read from the Camel endpoint URIs the outbound topic properties hold, e.g.
 * {@code google-pubsub://ent-anshar-dev:xml.estimated_timetables}.
 */
public record TopicAddress(String project, String topic) {

    private static final Pattern GOOGLE_PUBSUB_URI = Pattern.compile("google-pubsub:(?://)?([^:/?]+):([^:/?]+)(?:\\?.*)?");

    /**
     * @return the topic, or empty when the value is not a {@code google-pubsub} endpoint (empty, {@code mock:ignore},
     * a plain topic name in test configuration), which disables publishing to that topic
     */
    public static Optional<TopicAddress> parse(String value) {
        if (value == null) {
            return Optional.empty();
        }
        Matcher matcher = GOOGLE_PUBSUB_URI.matcher(value.trim());
        if (!matcher.matches()) {
            return Optional.empty();
        }
        return Optional.of(new TopicAddress(matcher.group(1), matcher.group(2)));
    }

    public String topicName() {
        return "projects/" + project + "/topics/" + topic;
    }
}
