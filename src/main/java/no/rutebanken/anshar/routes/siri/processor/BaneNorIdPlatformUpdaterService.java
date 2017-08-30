package no.rutebanken.anshar.routes.siri.processor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.concurrent.*;

@Component
@Configuration
public class BaneNorIdPlatformUpdaterService {
    private Logger logger = LoggerFactory.getLogger(BaneNorIdPlatformUpdaterService.class);

    private ConcurrentMap<String, String> jbvCodeStopPlaceMappings = new ConcurrentHashMap<>();

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
        updateIdMapping();
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        executor.scheduleAtFixedRate(() -> updateIdMapping(), updateFrequency, updateFrequency, TimeUnit.MINUTES);

        logger.info("Initialized jbvCode_mapping-updater with url:{}, updateFrequency:{} min", jbvCodeStopPlaceMappingUrl, updateFrequency);
    }

    private void updateIdMapping() {
        try {
            updateStopPlaceMapping();
        } catch (Exception e) {
            logger.warn("Fetching data - caused exception", e);
        }
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
