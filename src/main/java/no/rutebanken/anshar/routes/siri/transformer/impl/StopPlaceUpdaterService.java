package no.rutebanken.anshar.routes.siri.transformer.impl;

import org.quartz.utils.counter.Counter;
import org.quartz.utils.counter.CounterImpl;
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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Component
@Configuration
public class StopPlaceUpdaterService {
    private Logger logger = LoggerFactory.getLogger(StopPlaceUpdaterService.class);

    private Map<String, String> stopPlaceMappings = new HashMap<>();

    @Value("${anshar.mapping.quays.url}")
    private String quayMappingUrl;

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
        updateIdMapping();
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        executor.scheduleAtFixedRate(() -> updateIdMapping(), updateFrequency, updateFrequency, TimeUnit.MINUTES);

        logger.info("Initialized id_mapping-updater with url:{}, updateFrequency:{} min", quayMappingUrl, updateFrequency);
    }

    private void updateIdMapping() {
        try {
            updateStopPlaceMapping(quayMappingUrl);
            updateStopPlaceMapping(stopPlaceMappingUrl);
        } catch (IOException e) {
            logger.error("Unable to initialize data",e);
        }
    }

    private void updateStopPlaceMapping(String mappingUrl) throws IOException {
        logger.info("Initializing data - start. Fetching mapping-data from {}", mappingUrl);

        if (mappingUrl != null && !mappingUrl.isEmpty()) {

            URL url = new URL(mappingUrl);

            Map<String, String> tmpStopPlaceMappings = new HashMap<>();
            Counter duplicates = new CounterImpl(0);

            URLConnection connection = url.openConnection();
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(30000);

            BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));

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
            stopPlaceMappings.putAll(tmpStopPlaceMappings);
            long t2 = System.currentTimeMillis();

            logger.info("Initializing data - done - {} mappings, found {} duplicates. [putAll:{}ms]", stopPlaceMappings.size(), duplicates.getValue(), (t2 - t1));
        }
    }

    //Called from tests
    public void addStopPlaceMappings(Map<String, String> stopPlaceMap) {
        this.stopPlaceMappings.putAll(stopPlaceMap);
    }
}
