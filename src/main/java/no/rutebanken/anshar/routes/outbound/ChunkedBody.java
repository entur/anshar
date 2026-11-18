package no.rutebanken.anshar.routes.outbound;

import java.io.OutputStream;
import java.net.http.HttpRequest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * A request body written as XML once and sent as is, held in fixed-size chunks. A {@link java.io.ByteArrayOutputStream}
 * doubles one array as it grows and {@code toByteArray()} copies it again for every send attempt, which for a large
 * delivery means arrays of tens of megabytes: humongous objects for G1. Not thread-safe.
 * <p>
 * The first chunk starts small and doubles up to {@link #CHUNK_SIZE}, so a heartbeat or a small delivery costs about
 * what it would in a {@code ByteArrayOutputStream}; only a body larger than that moves on to further full-size chunks.
 */
final class ChunkedBody extends OutputStream {

    /** Well below G1's humongous threshold (half a region; 8 MB with the 16 MB regions in prd) */
    static final int CHUNK_SIZE = 256 * 1024;

    /** Fits a heartbeat */
    static final int INITIAL_SIZE = 1024;

    /** Each of size {@link #CHUNK_SIZE} */
    private final List<byte[]> full = new ArrayList<>();
    private byte[] current = new byte[INITIAL_SIZE];
    private int position;

    @Override
    public void write(int b) {
        if (position == current.length) {
            makeRoom();
        }
        current[position++] = (byte) b;
    }

    @Override
    public void write(byte[] bytes, int offset, int length) {
        Objects.checkFromIndexSize(offset, length, bytes.length);
        while (length > 0) {
            if (position == current.length) {
                makeRoom();
            }
            int copied = Math.min(length, current.length - position);
            System.arraycopy(bytes, offset, current, position, copied);
            position += copied;
            offset += copied;
            length -= copied;
        }
    }

    /** Grows the first chunk up to {@link #CHUNK_SIZE}, then starts a new one */
    private void makeRoom() {
        if (current.length < CHUNK_SIZE) {
            current = Arrays.copyOf(current, Math.min(2 * current.length, CHUNK_SIZE));
        } else {
            full.add(current);
            current = new byte[CHUNK_SIZE];
            position = 0;
        }
    }

    /** Discards what was written, e.g. before serializing again after a failure part way through; keeps the first chunk */
    void reset() {
        if (!full.isEmpty()) {
            current = full.get(0);
            full.clear();
        }
        position = 0;
    }

    long size() {
        return (long) full.size() * CHUNK_SIZE + position;
    }

    /** The body as written, in chunks of at most {@link #CHUNK_SIZE} bytes. Copies the last one; for tests */
    List<byte[]> chunks() {
        List<byte[]> chunks = new ArrayList<>(full);
        if (position > 0) {
            chunks.add(Arrays.copyOf(current, position));
        }
        return chunks;
    }

    /**
     * Sends the body as written, with its length; can be sent any number of times, e.g. for a retry, without copying.
     * Nothing may be written after this.
     */
    HttpRequest.BodyPublisher publisher() {
        if (size() == 0) {
            return HttpRequest.BodyPublishers.noBody();
        }
        List<HttpRequest.BodyPublisher> parts = new ArrayList<>(full.size() + 1);
        for (byte[] chunk : full) {
            parts.add(HttpRequest.BodyPublishers.ofByteArray(chunk));
        }
        if (position > 0) {
            parts.add(HttpRequest.BodyPublishers.ofByteArray(current, 0, position));
        }
        return HttpRequest.BodyPublishers.concat(parts.toArray(HttpRequest.BodyPublisher[]::new));
    }
}
