package no.rutebanken.anshar.messages.collections;

import org.junit.Before;
import org.junit.Test;

import java.time.ZonedDateTime;
import java.util.concurrent.ConcurrentHashMap;

import static junit.framework.TestCase.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ExpiringConcurrentMapTest {

    private ExpiringConcurrentMap<String, Integer> map;

    @Before
    public void setUp() {
        map = new ExpiringConcurrentMap<>(new ConcurrentHashMap<>(), 1);
    }

    @Test
    public void testNoExpiration() {
        assertNull(map.lastRun);

        map.put("test", 1234);
        assertTrue(map.containsKey("test"));

        map.put("test2", 1234);
        assertTrue(map.containsKey("test2"));

    }

    @Test
    public void testExpiration() {

        ZonedDateTime expiry = ZonedDateTime.now().plusSeconds(2);

        map.put("test3", 1234, expiry);
        assertTrue(map.containsKey("test3"));

        map.removeExpiredElements();
        assertTrue("Expired element has been removed too soon.", map.containsKey("test3"));

        //Wait for expiration-job to run
        while (map.lastRun == null || map.lastRun.isBefore(expiry)) {
            try {
                Thread.sleep(300);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        assertFalse("Expired element has not been removed.", map.containsKey("test3"));

    }

    @Test
    public void testNullExpiry(){

        map.put("test4", 1234, null);

        map.removeExpiredElements();

        assertTrue("Element with no expiry has been removed.", map.containsKey("test4"));

    }
}
