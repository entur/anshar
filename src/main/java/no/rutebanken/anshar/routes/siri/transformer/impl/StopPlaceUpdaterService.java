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
    private String stopPlaceMappingUrl = "http://tiamat/jersey/quay/id_mapping?recordsPerRoundTrip=50000";

    @Value("${anshar.mapping.stopplaces.update.frequency.min}")
    private int updateFrequency = 60;

    public String get(String id) {
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

    }

    private boolean updateIdMapping() {
        boolean locked = lockMap.tryLock(UPDATED_TIMESTAMP_KEY);
        if (!locked) {
            return false;
        }
        try {
            Instant instant = lockMap.get(UPDATED_TIMESTAMP_KEY);

            if ((instant == null || instant.isBefore(Instant.now().minusSeconds(updateFrequency * 60)))) {
                // Data is not initialized, or is older than allowed
                updateStopPlaceMapping();
                lockMap.put(UPDATED_TIMESTAMP_KEY, Instant.now());
            }
        } catch (Exception e) {
            logger.error("Initializing data - caused exception", e);
        } finally {
            lockMap.unlock(UPDATED_TIMESTAMP_KEY);
        }
        return true;
    }

    private void updateStopPlaceMapping() {
        //                logger.info("Initializing data - start. Fetching mapping-data from {}", stopPlaceMappingUrl);
//                URL url = new URL(stopPlaceMappingUrl);

        logger.info("Initializing data - start. Fetching mapping-data from internal file ");
        ClassLoader classLoader = getClass().getClassLoader();

        Map<String, String> tmpStopPlaceMappings = new HashMap<>();
        Counter duplicates = new CounterImpl(0);
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(classLoader.getResourceAsStream("id_mapping.csv")))) {
//                try (BufferedReader reader = new BufferedReader(new InputStreamReader(url.openStream()))) {

            reader.lines().forEach(line -> {

                StringTokenizer tokenizer = new StringTokenizer(line, ",");
                String id = tokenizer.nextToken();
                String generatedId = tokenizer.nextToken().replaceAll(":", "."); //Converting to OTP naming convention

                if (tmpStopPlaceMappings.containsKey(id)) {
                    duplicates.increment();
                }
                tmpStopPlaceMappings.put(id, generatedId);
            });

            //Adding to Hazelcast in one operation
            stopPlaceMappings.putAll(tmpStopPlaceMappings);

            logger.info("Initializing data - done - {} mappings, found {} duplicates.", stopPlaceMappings.size(), duplicates.getValue());

        } catch (IOException io) {
            logger.info("Initializing data failed during loading- keeping existing data.");
        }
    }

    //Called from tests
    public void addStopPlaceMappings(Map<String, String> stopPlaceMap) {
        this.stopPlaceMappings.putAll(stopPlaceMap);
    }
}
