package no.rutebanken.anshar.routes.siri.helpers;

import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingFile;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Measures the largest single object the current thread allocates outside a TLAB, with an in-process JFR recording.
 * Objects large enough to matter to G1 (humongous ones above all) are always allocated outside a TLAB.
 */
public final class LargestAllocation {

    public static final long MB = 1024 * 1024;

    @FunctionalInterface
    public interface Work {
        void run() throws Exception;
    }

    private LargestAllocation() {
    }

    /** @return the size in bytes of the largest object allocated outside a TLAB while {@code work} ran */
    public static long of(Work work) throws Exception {
        Path file = Files.createTempFile("allocations", ".jfr");
        try (Recording recording = new Recording()) {
            recording.enable("jdk.ObjectAllocationOutsideTLAB");
            recording.start();
            work.run();
            recording.stop();
            recording.dump(file);
            long thread = Thread.currentThread().threadId();
            long largest = 0;
            for (RecordedEvent event : RecordingFile.readAllEvents(file)) {
                if (event.getThread() != null && event.getThread().getJavaThreadId() == thread) {
                    largest = Math.max(largest, event.getLong("allocationSize"));
                }
            }
            return largest;
        } finally {
            Files.deleteIfExists(file);
        }
    }
}
