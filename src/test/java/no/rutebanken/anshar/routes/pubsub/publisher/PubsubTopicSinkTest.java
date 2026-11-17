package no.rutebanken.anshar.routes.pubsub.publisher;

import com.google.api.core.ApiFuture;
import com.google.api.core.ApiFutures;
import com.google.cloud.pubsub.v1.Publisher;
import com.google.protobuf.ByteString;
import com.google.pubsub.v1.PubsubMessage;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PubsubTopicSinkTest {

    private final Publisher publisher = mock(Publisher.class);
    private final PubsubTopicSink sink = new PubsubTopicSink(publisher);

    @Test
    void publishDelegates() {
        PubsubMessage message = PubsubMessage.newBuilder().setData(ByteString.copyFromUtf8("<Siri/>")).build();
        ApiFuture<String> future = ApiFutures.immediateFuture("id-1");
        when(publisher.publish(message)).thenReturn(future);

        assertSame(future, sink.publish(message));
    }

    @Test
    void closeFlushesPublisher() throws Exception {
        sink.close();

        InOrder order = inOrder(publisher);
        order.verify(publisher).shutdown();
        order.verify(publisher).awaitTermination(30, TimeUnit.SECONDS);
    }
}
