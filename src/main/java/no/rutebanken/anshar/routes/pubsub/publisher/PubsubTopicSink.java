package no.rutebanken.anshar.routes.pubsub.publisher;

import com.google.api.core.ApiFuture;
import com.google.api.gax.batching.BatchingSettings;
import com.google.api.gax.batching.FlowControlSettings;
import com.google.api.gax.batching.FlowController;
import com.google.api.gax.core.NoCredentialsProvider;
import com.google.api.gax.grpc.InstantiatingGrpcChannelProvider;
import com.google.cloud.pubsub.v1.Publisher;
import com.google.pubsub.v1.PubsubMessage;
import io.grpc.ManagedChannelBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Publishes to one Pub/Sub topic through the client library's {@link Publisher}, which batches messages and sends
 * them in the background. When too many messages are waiting for Pub/Sub, {@link #publish} blocks the caller
 * instead of queueing without limit.
 */
public class PubsubTopicSink implements TopicSink {

    private static final Logger logger = LoggerFactory.getLogger(PubsubTopicSink.class);

    // The client library's own batching defaults, spelled out since it keeps them package-private
    private static final long BATCH_ELEMENT_COUNT = 100L;
    private static final long BATCH_REQUEST_BYTES = 1000L;
    private static final Duration BATCH_DELAY = Duration.ofMillis(1);

    private static final long MAX_OUTSTANDING_MESSAGES = 10_000L;
    private static final long MAX_OUTSTANDING_BYTES = 100L * 1024 * 1024;
    private static final long CLOSE_TIMEOUT_SECONDS = 30;

    private final Publisher publisher;

    /**
     * @param emulatorHost when present, e.g. {@code localhost:8085}, connects to the Pub/Sub emulator without
     *                     credentials; otherwise to Pub/Sub with application default credentials
     */
    public PubsubTopicSink(TopicAddress address, Optional<String> emulatorHost) {
        this(build(address, emulatorHost));
    }

    PubsubTopicSink(Publisher publisher) {
        this.publisher = publisher;
    }

    private static Publisher build(TopicAddress address, Optional<String> emulatorHost) {
        BatchingSettings batching = BatchingSettings.newBuilder()
                .setElementCountThreshold(BATCH_ELEMENT_COUNT)
                .setRequestByteThreshold(BATCH_REQUEST_BYTES)
                .setDelayThresholdDuration(BATCH_DELAY)
                .setFlowControlSettings(FlowControlSettings.newBuilder()
                        .setMaxOutstandingElementCount(MAX_OUTSTANDING_MESSAGES)
                        .setMaxOutstandingRequestBytes(MAX_OUTSTANDING_BYTES)
                        .setLimitExceededBehavior(FlowController.LimitExceededBehavior.Block)
                        .build())
                .build();
        Publisher.Builder builder = Publisher.newBuilder(address.topicName()).setBatchingSettings(batching);
        emulatorHost.ifPresent(host -> builder
                .setChannelProvider(InstantiatingGrpcChannelProvider.newBuilder()
                        .setEndpoint(host)
                        .setChannelConfigurator(ManagedChannelBuilder::usePlaintext)
                        .build())
                .setCredentialsProvider(NoCredentialsProvider.create()));
        try {
            return builder.build();
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to create a publisher for " + address.topicName(), e);
        }
    }

    @Override
    public ApiFuture<String> publish(PubsubMessage message) {
        return publisher.publish(message);
    }

    @Override
    public void close() {
        publisher.shutdown();
        try {
            if (!publisher.awaitTermination(CLOSE_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                logger.warn("Messages to {} still unsent after {} s", publisher.getTopicNameString(), CLOSE_TIMEOUT_SECONDS);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
