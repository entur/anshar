package no.rutebanken.anshar.routes.siri.transformer.impl;


import com.hazelcast.core.IMap;
import no.rutebanken.anshar.messages.collections.DistributedCollection;
import no.rutebanken.anshar.routes.siri.transformer.ValueAdapter;
import org.quartz.utils.counter.Counter;
import org.quartz.utils.counter.CounterImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.time.Instant;
import java.util.*;

@Configuration
public class StopPlaceRegisterMapper extends ValueAdapter {

    private static final String UPDATED_TIMESTAMP_KEY = "anshar.nsr.updater";
    private static Logger logger = LoggerFactory.getLogger(StopPlaceRegisterMapper.class);

    private static Map<String, String> stopPlaceMappings;
    private static IMap<String, Instant> lockMap;

    @Value("${anshar.mapping.stopplaces.url}")
    private String stopPlaceMappingUrl = "http://tiamat/jersey/id_mapping?recordsPerRoundTrip=50000";

    @Value("${anshar.mapping.stopplaces.update.frequency.min}")
    private int updateFrequency = 60;

    private static Timer updater;

    private List<String> prefixes;

    static {
        DistributedCollection dc = new DistributedCollection();
        stopPlaceMappings = dc.getStopPlaceMappings();
        lockMap = dc.getLockMap();
    }

    public StopPlaceRegisterMapper() {
        //called by Spring during startup
    }

    public StopPlaceRegisterMapper(Class clazz, List<String> prefixes) {
        super(clazz);
        this.prefixes = prefixes;
        initialize();
    }


    public String apply(String id) {
        if (id == null || id.isEmpty()) {
            return id;
        }
        if (prefixes != null && !prefixes.isEmpty()) {
            for (String prefix : prefixes) {
                String mappedValue = stopPlaceMappings.get(createCompleteId(prefix, id));
                if (mappedValue != null) {
                    return mappedValue;
                }
            }
        }

        String mappedValue = stopPlaceMappings.get(id);
        return mappedValue != null ? mappedValue : id;
    }

    private String createCompleteId(String prefix, String id) {
        return new StringBuilder().append(prefix).append(":").append("StopArea").append(":").append(id).toString();
    }

    //Used for testing
    public static void setStopPlaceMappings(Map<String, String> stopPlaceMappings) {
        StopPlaceRegisterMapper.stopPlaceMappings = stopPlaceMappings;
    }

    public void initialize() {
        if (updater == null) {
            TimerTask cacheUpdater = new TimerTask() {
                @Override
                public void run() {
                    updateIdMapping();
                }
            };
            updater = new Timer(true);
            updater.schedule(cacheUpdater,  new Date(), updateFrequency*60*1000);
        }
    }

    @Bean
    protected boolean updateIdMapping() {
        boolean locked = lockMap.tryLock(UPDATED_TIMESTAMP_KEY);
        if (!locked) {
            return false;
        }
        try {
            Instant instant = lockMap.get(UPDATED_TIMESTAMP_KEY);

            if ((instant == null || instant.isBefore(Instant.now().minusSeconds(updateFrequency * 60)))) {
                // Data is not initialized, or is older than allowed

                logger.info("Initializing data - start. Fetching mapping-data from {}", stopPlaceMappingUrl);
                URL url = new URL(stopPlaceMappingUrl);

                Map<String, String> tmpStopPlaceMappings = new HashMap<>();
                Counter duplicates = new CounterImpl(0);
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(url.openStream()))) {

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
                    lockMap.put(UPDATED_TIMESTAMP_KEY, Instant.now());

                    logger.info("Initializing data - done - {} mappings, found {} duplicates.", stopPlaceMappings.size(), duplicates.getValue());

                } catch (IOException io) {
                    logger.info("Initializing data failed during loading- keeping existing data.");
                }
            } else {
                logger.trace("Initializing data - already initialized [{}].", instant);
            }
        } catch (IOException e) {
            logger.error("Initializing data - caused exception", e);
        } finally {
            lockMap.unlock(UPDATED_TIMESTAMP_KEY);
        }
        return true;
    }
}
