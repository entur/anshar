package no.rutebanken.anshar.routes.siri.transformer.impl;

import com.hazelcast.core.IMap;
import org.quartz.utils.counter.Counter;
import org.quartz.utils.counter.CounterImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Component
@Configuration
public class StopPlaceUpdaterService {
    private Logger logger = LoggerFactory.getLogger(StopPlaceUpdaterService.class);

    private static final String UPDATED_TIMESTAMP_KEY = "anshar.nsr.updater";

    @Autowired
    @Qualifier("getStopPlaceMappings")
    private IMap<String, String> stopPlaceMappings;

    @Autowired
    @Qualifier("getLockMap")
    private IMap<String, Instant> lockMap;

    @Value("${anshar.mapping.stopplaces.url}")
    private String stopPlaceMappingUrl;

    @Value("${anshar.mapping.stopplaces.update.frequency.min:60}")
    private int updateFrequency = 60;

    public String get(String id) {
        if (stopPlaceMappings.isEmpty()) {
            updateIdMapping();
        }
        return stopPlaceMappings.get(id);
    }

    @PostConstruct
    private void initialize() {

        int initialDelay;
        if (stopPlaceMappings.isEmpty()) {
            initialDelay = 0;
        } else {
            initialDelay = updateFrequency;
        }
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        executor.scheduleAtFixedRate(() -> updateIdMapping(), initialDelay, updateFrequency, TimeUnit.MINUTES);

        logger.info("Initialized id_mapping-updater with url:{}, updateFrequency:{} min", stopPlaceMappingUrl, updateFrequency);
    }

    private void updateIdMapping() {
        if (!lockMap.tryLock(UPDATED_TIMESTAMP_KEY)) {
            return;
        }
        try {
            Instant instant = lockMap.get(UPDATED_TIMESTAMP_KEY);

            if ((instant == null || instant.isBefore(Instant.now().minusSeconds(updateFrequency * 60)))) {
                // Data is not initialized, or is older than allowed
                updateStopPlaceMapping();
                lockMap.put(UPDATED_TIMESTAMP_KEY, Instant.now());
            }
        } catch (Exception e) {
            logger.warn("Fetching data - caused exception", e);
        } finally {
            lockMap.unlock(UPDATED_TIMESTAMP_KEY);
        }
        return;
    }

    private void updateStopPlaceMapping() throws IOException {

        if (stopPlaceMappingUrl != null && !stopPlaceMappingUrl.isEmpty()) {

            logger.info("Initializing data - start. Fetching mapping-data from {}", stopPlaceMappingUrl);
            URL url = new URL(stopPlaceMappingUrl);

            Map<String, String> tmpStopPlaceMappings = new HashMap<>();
            Counter duplicates = new CounterImpl(0);

            URLConnection con = url.openConnection();
            con.setReadTimeout(30000);

            BufferedReader reader = new BufferedReader(new InputStreamReader(con.getInputStream()));

            reader.lines().forEach(line -> {

                StringTokenizer tokenizer = new StringTokenizer(line, ",");
                String id = tokenizer.nextToken();
                String generatedId = tokenizer.nextToken();

                if (tmpStopPlaceMappings.containsKey(id)) {
                    duplicates.increment();
                }
                tmpStopPlaceMappings.put(id, generatedId);
            });

            //Adding to Hazelcast in one operation
            long t1 = System.currentTimeMillis();
            stopPlaceMappings.evictAll();
            long t2 = System.currentTimeMillis();
            stopPlaceMappings.putAll(tmpStopPlaceMappings);
            long t3 = System.currentTimeMillis();

            logger.info("Initializing data - done - {} mappings, found {} duplicates. [evict: {}ms, putAll:{}ms]", stopPlaceMappings.size(), duplicates.getValue(), (t2 - t1), (t3 - t2));
        }
    }

    //Called from tests
    public void addStopPlaceMappings(Map<String, String> stopPlaceMap) {
        this.stopPlaceMappings.putAll(stopPlaceMap);
    }
}
