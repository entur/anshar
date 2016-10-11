package no.rutebanken.anshar.messages.collections;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

/**
 * Map that maintains expiry per element, and periodically removes expired elements
 *
 * @param <String>
 * @param <T>
 */
public class ExpiringConcurrentMap<String,T>  extends ConcurrentHashMap<String,T> {
    private static Logger logger = LoggerFactory.getLogger(ExpiringConcurrentMap.class);

    final ConcurrentMap map;
    final Map<String, ZonedDateTime> expiryMap = new HashMap<>();
    private int defaultExpirationSeconds;
    int expiryPeriodSeconds = 30;
    ZonedDateTime lastRun;

    /**
     *
     * @param map Wrapped map to store actual data
     * @param expiryPeriodSeconds Period between each execution og expiration-check
     */
    public ExpiringConcurrentMap(ConcurrentMap map, int expiryPeriodSeconds, int defaultExpirationSeconds) {
        this.map = map;
        this.expiryPeriodSeconds = expiryPeriodSeconds;
        this.defaultExpirationSeconds = defaultExpirationSeconds;
        initExpiryManagerThread();
    }
    /**
     *
     * @param map Wrapped map to store actual data
     * @param expiryPeriodSeconds Period between each execution og expiration-check
     */
    public ExpiringConcurrentMap(ConcurrentMap map, int expiryPeriodSeconds) {
        this.map = map;
        this.expiryPeriodSeconds = expiryPeriodSeconds;
        initExpiryManagerThread();
    }

    public T put(String key, T value, ZonedDateTime expiry) {
        expiryMap.put(key, expiry);
        return super.put(key, value);
    }

    public T put(String key, T value) {
        if (defaultExpirationSeconds > 0 && !expiryMap.containsKey(key)) {
            expiryMap.put(key, ZonedDateTime.now().plusSeconds(defaultExpirationSeconds));
        }
        return super.put(key, value);
    }

    @Override
    public T remove(Object key) {
        T value = super.remove(key);
        expiryMap.remove(key);
        return value;
    }

    private void initExpiryManagerThread() {
        TimerTask timerTask = new TimerTask() {
            @Override
            public void run() {
                removeExpiredElements();
            }
        };

        Timer timer = new Timer();
        timer.schedule(timerTask, expiryPeriodSeconds*1000, expiryPeriodSeconds*1000);
        logger.info("Expiration thread started - checking every {} sec", expiryPeriodSeconds);
    }

    public void removeExpiredElements() {
        long t1 = System.currentTimeMillis();
        List<String> expiredKeys = expiryMap.keySet()
                .stream()
                .filter(key ->
                                (expiryMap.get(key) != null &&
                                        expiryMap.get(key).isBefore(ZonedDateTime.now()))
                ).collect(Collectors.toList());

        expiredKeys.forEach(key -> remove(key));

        lastRun = ZonedDateTime.now();
        logger.debug("Expiring {} elements took {} ms",expiredKeys.size(), (System.currentTimeMillis() - t1));
    }
}
