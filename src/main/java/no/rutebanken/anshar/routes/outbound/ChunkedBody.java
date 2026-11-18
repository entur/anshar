package no.rutebanken.anshar.routes.outbound;

import java.io.OutputStream;
import java.net.http.HttpRequest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A request body written as XML once and sent as is, held in fixed-size chunks. A {@link java.io.ByteArrayOutputStream}
 * doubles one array as it grows and {@code toByteArray()} copies it again for every send attempt, which for a large
 * delivery means arrays of tens of megabytes: humongous objects for G1. Not thread-safe.
 */
final class ChunkedBody extends OutputStream {

    /** Well below G1's humongous threshold (half a region; 8 MB with the 16 MB regions in prd) */
    static final int CHUNK_SIZE = 256 * 1024;

    private final List<byte[]> full = new ArrayList<>();
    private byte[] current = new byte[CHUNK_SIZE];
    private int position;

    @Override
    public void write(int b) {
        if (position == CHUNK_SIZE) {
            nextChunk();
        }
        current[position++] = (byte) b;
    }

    @Override
    public void write(byte[] bytes, int offset, int length) {
        while (length > 0) {
            if (position == CHUNK_SIZE) {
                nextChunk();
            }
            int copied = Math.min(length, CHUNK_SIZE - position);
            System.arraycopy(bytes, offset, current, position, copied);
            position += copied;
            offset += copied;
            length -= copied;
        }
    }

    private void nextChunk() {
        full.add(current);
        current = new byte[CHUNK_SIZE];
        position = 0;
    }

    /** Discards what was written, e.g. before serializing again after a failure part way through */
    void reset() {
        full.clear();
        current = new byte[CHUNK_SIZE];
        position = 0;
    }

    long size() {
        return (long) full.size() * CHUNK_SIZE + position;
    }

    /** The body as written, in chunks of at most {@link #CHUNK_SIZE} bytes */
    List<byte[]> chunks() {
        List<byte[]> chunks = new ArrayList<>(full);
        if (position > 0) {
            chunks.add(Arrays.copyOf(current, position));
        }
        return chunks;
    }

    /** Sends the body as written, with its length; can be sent any number of times, e.g. for a retry, without copying */
    HttpRequest.BodyPublisher publisher() {
        if (size() == 0) {
            // fromPublisher rejects a length of 0
            return HttpRequest.BodyPublishers.noBody();
        }
        return HttpRequest.BodyPublishers.fromPublisher(HttpRequest.BodyPublishers.ofByteArrays(chunks()), size());
    }
}
