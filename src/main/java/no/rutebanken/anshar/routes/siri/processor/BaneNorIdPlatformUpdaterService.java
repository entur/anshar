package no.rutebanken.anshar.routes.siri.processor;

import com.hazelcast.core.IMap;
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
public class BaneNorIdPlatformUpdaterService {
    private Logger logger = LoggerFactory.getLogger(BaneNorIdPlatformUpdaterService.class);

    private static final String UPDATED_TIMESTAMP_KEY = "anshar.jbvCode.updater";

    @Autowired
    @Qualifier("getJbvStopPlaceMappings")
    private IMap<String, String> jbvCodeStopPlaceMappings;

    @Autowired
    @Qualifier("getLockMap")
    private IMap<String, Instant> lockMap;

    @Value("${anshar.mapping.jbvCode.url}")
    private String jbvCodeStopPlaceMappingUrl;

    @Value("${anshar.mapping.jbvCode.update.frequency.min:60}")
    private int updateFrequency = 60;

    public String get(String id) {
        if (jbvCodeStopPlaceMappings.isEmpty()) {
            updateIdMapping();
        }
        return jbvCodeStopPlaceMappings.get(id);
    }

    @PostConstruct
    private void initialize() {

        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        executor.scheduleAtFixedRate(() -> updateIdMapping(), 0, updateFrequency, TimeUnit.MINUTES);

        logger.info("Initialized jbvCode_mapping-updater with url:{}, updateFrequency:{} min", jbvCodeStopPlaceMappingUrl, updateFrequency);
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

        if (jbvCodeStopPlaceMappingUrl != null && !jbvCodeStopPlaceMappingUrl.isEmpty()) {

            logger.info("Initializing data - start. Fetching mapping-data from {}", jbvCodeStopPlaceMappingUrl);
            URL url = new URL(jbvCodeStopPlaceMappingUrl);

            Map<String, String> tmpStopPlaceMappings = new HashMap<>();

            URLConnection connection = url.openConnection();
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(30000);

            BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));

            reader.lines().forEach(line -> {

                StringTokenizer tokenizer = new StringTokenizer(line, ",");
                String jbvCodePlatform = tokenizer.nextToken();
                String generatedId = tokenizer.nextToken();

                tmpStopPlaceMappings.put(jbvCodePlatform, generatedId);
            });

            //Adding to Hazelcast in one operation
            jbvCodeStopPlaceMappings.putAll(tmpStopPlaceMappings);
        }
    }
}
