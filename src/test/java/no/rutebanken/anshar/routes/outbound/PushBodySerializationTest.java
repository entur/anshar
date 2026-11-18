package no.rutebanken.anshar.routes.outbound;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Serializing a push body, with one retry when the first attempt fails */
class PushBodySerializationTest {

    private static String text(ChunkedBody body) {
        ByteArrayOutputStream joined = new ByteArrayOutputStream();
        body.chunks().forEach(joined::writeBytes);
        return joined.toString(StandardCharsets.UTF_8);
    }

    @Test
    void bodyIsWhatTheSerializerWrote() {
        ChunkedBody body = CamelRouteManager.serializeWithRetry(out -> out.write("<Siri/>".getBytes(StandardCharsets.UTF_8)));

        assertEquals("<Siri/>", text(body));
    }

    @Test
    void retryStartsFromAnEmptyBody() {
        AtomicInteger attempts = new AtomicInteger();

        ChunkedBody body = CamelRouteManager.serializeWithRetry(out -> {
            if (attempts.incrementAndGet() == 1) {
                out.write("<Siri><ServiceDel".getBytes(StandardCharsets.UTF_8));
                throw new IOException("failed part way");
            }
            out.write("<Siri/>".getBytes(StandardCharsets.UTF_8));
        });

        // Not the first attempt's half document followed by the whole one
        assertEquals("<Siri/>", text(body));
        assertEquals(2, attempts.get());
    }

    @Test
    void errorsAreNotRetried() {
        AtomicInteger attempts = new AtomicInteger();

        assertThrows(OutOfMemoryError.class, () -> CamelRouteManager.serializeWithRetry(out -> {
            attempts.incrementAndGet();
            throw new OutOfMemoryError("simulated");
        }));
        assertEquals(1, attempts.get());
    }

    @Test
    void twoFailuresAreThrown() {
        assertThrows(RuntimeException.class, () -> CamelRouteManager.serializeWithRetry(out -> {
            throw new IOException("always fails");
        }));
    }
}
