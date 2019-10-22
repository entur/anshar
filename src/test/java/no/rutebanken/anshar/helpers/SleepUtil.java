package no.rutebanken.anshar.helpers;

public class SleepUtil {
    public static void sleep(int millis) {
        try {
            // Wait for updates to be processed...
            Thread.sleep(1500 + millis); // Adding 2 seconds to allow changes to be committed
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
