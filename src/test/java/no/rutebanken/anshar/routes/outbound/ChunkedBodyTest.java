package no.rutebanken.anshar.routes.outbound;

import no.rutebanken.anshar.routes.siri.helpers.LargestAllocation;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.net.http.HttpRequest;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChunkedBodyTest {

    private static byte[] bytes(int length) {
        byte[] bytes = new byte[length];
        new Random(length).nextBytes(bytes);
        return bytes;
    }

    private static byte[] joined(List<byte[]> chunks) {
        ByteArrayOutputStream joined = new ByteArrayOutputStream();
        chunks.forEach(joined::writeBytes);
        return joined.toByteArray();
    }

    @Test
    void bytesComeBackInOrderAcrossChunks() throws Exception {
        byte[] content = bytes(3 * ChunkedBody.CHUNK_SIZE + 17);
        ChunkedBody body = new ChunkedBody();
        // Single bytes, a write crossing a chunk boundary, and one larger than a chunk
        body.write(content[0]);
        body.write(content, 1, ChunkedBody.CHUNK_SIZE);
        body.write(content, ChunkedBody.CHUNK_SIZE + 1, content.length - ChunkedBody.CHUNK_SIZE - 1);

        assertArrayEquals(content, joined(body.chunks()));
        assertEquals(content.length, body.size());
    }

    @Test
    void noChunkIsLargerThanTheChunkSize() throws Exception {
        ChunkedBody body = new ChunkedBody();
        body.write(bytes(10 * ChunkedBody.CHUNK_SIZE + 1));

        assertTrue(body.chunks().stream().allMatch(chunk -> chunk.length <= ChunkedBody.CHUNK_SIZE));
        assertEquals(11, body.chunks().size());
    }

    @Test
    void aLargeBodyAllocatesNoLargeArrays() throws Exception {
        byte[] block = bytes(8 * 1024);
        ChunkedBody body = new ChunkedBody();

        // 64 MB, as a large ET delivery; a ByteArrayOutputStream would double its array up to 64 MB
        long largest = LargestAllocation.of(() -> {
            for (int i = 0; i < 8 * 1024; i++) {
                body.write(block);
            }
            body.publisher();
        });

        assertTrue(largest < LargestAllocation.MB, "largest allocation was " + largest + " bytes");
        assertEquals(64L * 1024 * 1024, body.size());
    }

    @Test
    void resetDiscardsWhatWasWritten() throws Exception {
        ChunkedBody body = new ChunkedBody();
        body.write(bytes(2 * ChunkedBody.CHUNK_SIZE));
        body.reset();
        body.write(new byte[]{1, 2, 3});

        assertArrayEquals(new byte[]{1, 2, 3}, joined(body.chunks()));
        assertEquals(3, body.size());
    }

    @Test
    void emptyBodyHasNoBytes() throws Exception {
        ChunkedBody body = new ChunkedBody();

        assertEquals(0, body.size());
        assertEquals(0, joined(body.chunks()).length);
        assertEquals(0, body.publisher().contentLength());
        assertEquals(0, read(body.publisher()).length);
    }

    @Test
    void publisherSendsTheWholeBodyEveryTimeWithItsLength() throws Exception {
        byte[] content = bytes(2 * ChunkedBody.CHUNK_SIZE + 5);
        ChunkedBody body = new ChunkedBody();
        body.write(content);
        HttpRequest.BodyPublisher publisher = body.publisher();

        assertEquals(content.length, publisher.contentLength());
        // As for a send and its retry
        assertArrayEquals(content, read(publisher));
        assertArrayEquals(content, read(publisher));
    }

    private static byte[] read(HttpRequest.BodyPublisher publisher) throws Exception {
        ByteArrayOutputStream received = new ByteArrayOutputStream();
        CompletableFuture<Void> done = new CompletableFuture<>();
        publisher.subscribe(new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(ByteBuffer item) {
                byte[] chunk = new byte[item.remaining()];
                item.get(chunk);
                received.writeBytes(chunk);
            }

            @Override
            public void onError(Throwable throwable) {
                done.completeExceptionally(throwable);
            }

            @Override
            public void onComplete() {
                done.complete(null);
            }
        });
        done.get(5, TimeUnit.SECONDS);
        return received.toByteArray();
    }
}
