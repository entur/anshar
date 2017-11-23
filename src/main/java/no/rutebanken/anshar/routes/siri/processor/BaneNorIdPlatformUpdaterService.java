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
import java.util.Random;
import java.util.StringTokenizer;
import java.util.concurrent.*;

@Component
@Configuration
public class BaneNorIdPlatformUpdaterService {
    private Logger logger = LoggerFactory.getLogger(BaneNorIdPlatformUpdaterService.class);

    private static final Object LOCK = new Object();

    private ConcurrentMap<String, String> jbvCodeStopPlaceMappings = new ConcurrentHashMap<>();

    @Value("${anshar.mapping.jbvCode.url}")
    private String jbvCodeStopPlaceMappingUrl;

    @Value("${anshar.mapping.jbvCode.update.frequency.min:60}")
    private int updateFrequency = 60;


    @Value("${anshar.mapping.jbvCode.update.timeout.read.ms:40000}")
    private int readTimeoutMs = 40000;

    @Value("${anshar.mapping.jbvCode.update.timeout.connect.ms:5000}")
    private int connectTimeoutMs = 5000;

    @Value("${HOSTNAME:anshar}")
    private String userAgent;

    public String get(String id) {
        if (jbvCodeStopPlaceMappings.isEmpty()) {
            // Avoid multiple calls at the same time.
            // Could have used a timed lock here.
            synchronized (LOCK) {
                // Check again.
                if (jbvCodeStopPlaceMappings.isEmpty()) {
                    updateIdMapping();
                }
            }
        }
        return jbvCodeStopPlaceMappings.get(id);
    }

    @PostConstruct
    private void initialize() {
        updateIdMapping();
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

        int initialDelay = updateFrequency + new Random().nextInt(10);
        executor.scheduleAtFixedRate(() -> updateIdMapping(), initialDelay, updateFrequency, TimeUnit.MINUTES);


        logger.info("Initialized jbvCode_mapping-updater with url:{}, updateFrequency:{} min, initialDelay:{} min", jbvCodeStopPlaceMappingUrl, updateFrequency, initialDelay);
    }

    private void updateIdMapping() {
        try {
            // re-entrant
            synchronized (LOCK) {
                updateStopPlaceMapping();
            }
        } catch (Exception e) {
            logger.warn("Fetching data - caused exception", e);
        }
    }

    private void updateStopPlaceMapping() throws IOException {

        if (jbvCodeStopPlaceMappingUrl != null && !jbvCodeStopPlaceMappingUrl.isEmpty()) {

            logger.info("Fetching mapping-data from {}", jbvCodeStopPlaceMappingUrl);
            URL url = new URL(jbvCodeStopPlaceMappingUrl);

            Map<String, String> tmpStopPlaceMappings = new HashMap<>();

            URLConnection connection = url.openConnection();
            connection.setConnectTimeout(connectTimeoutMs);
            connection.setReadTimeout(readTimeoutMs);
            connection.setRequestProperty("User-Agent", userAgent);

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
