package no.rutebanken.anshar.helpers;

public class SleepUtil {
    public static void sleep(int millis) {
        try {
            // Wait for updates to be processed...
            Thread.sleep(1000 + millis); // Adding a second to allow buffered changes to be committed
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
