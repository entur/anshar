package no.rutebanken.anshar.messages.collections;

import com.google.common.base.Preconditions;
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
public class ExpiringConcurrentMap<String,T>  implements Map<String,T> {
    private static Logger logger = LoggerFactory.getLogger(ExpiringConcurrentMap.class);

    final ConcurrentMap<String,T> map;
    final Map<String, ZonedDateTime> expiryMap = new ConcurrentHashMap<>();
    int expiryPeriodSeconds = 30;
    ZonedDateTime lastRun;

    /**
     *
     * @param map Wrapped map to store actual data
     * @param expiryPeriodSeconds Period between each execution of expiration-check
     */
    public ExpiringConcurrentMap(ConcurrentMap<String,T> map, int expiryPeriodSeconds) {
        Preconditions.checkNotNull(map, "Parameter 'map' cannot be null");
        Preconditions.checkArgument(expiryPeriodSeconds > 0, "Parameter 'expiryPeriodSeconds' must be > 0");

        this.map = map;
        this.expiryPeriodSeconds = expiryPeriodSeconds;
        initExpiryManagerThread();
    }

    public T put(String key, T value, ZonedDateTime expiry) {
        if (expiry == null) {
           return put(key, value);
        }
        expiryMap.put(key, expiry);
        return map.put(key, value);
    }

    public T put(String key, T value) {
        if (expiryPeriodSeconds > 0 && !expiryMap.containsKey(key)) {
            expiryMap.put(key, ZonedDateTime.now().plusSeconds(expiryPeriodSeconds));
        }
        return map.put(key, value);
    }

    public int size(){
        return map.size();
    }

    @Override
    public boolean isEmpty() {
        return map.isEmpty();
    }

    @Override
    public boolean containsKey(Object key) {
        return map.containsKey(key);
    }

    @Override
    public boolean containsValue(Object value) {
        return map.containsValue(value);
    }

    public T get(Object key){
        return map.get(key);
    }

    public Collection<T> values() {
        return map.values();
    }

    @Override
    public Set<Entry<String, T>> entrySet() {
        return map.entrySet();
    }

    public void clear() {
        map.clear();
    }

    @Override
    public Set<String> keySet() {
        return map.keySet();
    }

    @Override
    public T remove(Object key) {
        T value = map.remove(key);
        expiryMap.remove(key);
        return value;
    }

    @Override
    public void putAll(Map<? extends String, ? extends T> m) {
        map.putAll(m);
    }

    public void putAll(Map<? extends String, ? extends T> m, Map<String, ZonedDateTime> expiry) {
        map.putAll(m);
        expiryMap.putAll(expiry);
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
