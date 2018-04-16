/*
 * Licensed under the EUPL, Version 1.2 or – as soon they will be approved by
 * the European Commission - subsequent versions of the EUPL (the "Licence");
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 *
 *   https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and
 * limitations under the Licence.
 */

package no.rutebanken.anshar.routes.siri.transformer.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.*;

@Component
@Configuration
public class StopPlaceUpdaterService {

    private final Logger logger = LoggerFactory.getLogger(StopPlaceUpdaterService.class);

    private static final Object LOCK = new Object();

    private final ConcurrentMap<String, String> stopPlaceMappings = new ConcurrentHashMap<>();

    @Autowired
    private StopPlaceRegisterMappingFetcher stopPlaceRegisterMappingFetcher;

    @Value("${anshar.mapping.quays.url}")
    private String quayMappingUrl;

    @Value("${anshar.mapping.stopplaces.url}")
    private String stopPlaceMappingUrl;

    @Value("${anshar.mapping.stopplaces.update.frequency.min:60}")
    private int updateFrequency = 60;

    public String get(String id) {
        if (stopPlaceMappings.isEmpty()) {
            // Avoid multiple calls at the same time.
            // Could have used a timed lock here.
            synchronized (LOCK) {
                // Check again.
                if (stopPlaceMappings.isEmpty()) {
                    updateIdMapping();
                }
            }
        }
        return stopPlaceMappings.get(id);
    }

    @PostConstruct
    private void initialize() {

        updateIdMapping();
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

        int initialDelay = updateFrequency + new Random().nextInt(10);
        executor.scheduleAtFixedRate(this::updateIdMapping, initialDelay, updateFrequency, TimeUnit.MINUTES);

        logger.info("Initialized id_mapping-updater with urls:{}, updateFrequency:{} min, initialDelay:{} min", new String[]{quayMappingUrl, stopPlaceMappingUrl}, updateFrequency, initialDelay);
    }

    private void updateIdMapping() {
        try {
            // re-entrant
            synchronized (LOCK) {
                updateStopPlaceMapping(quayMappingUrl);
                updateStopPlaceMapping(stopPlaceMappingUrl);
            }
        } catch (IOException e) {
            logger.error("Unable to initialize data", e);
        }
    }

    private void updateStopPlaceMapping(String mappingUrl) throws IOException {
        logger.info("Fetching mapping data - start. Fetching mapping-data from {}", mappingUrl);

        stopPlaceMappings.putAll(stopPlaceRegisterMappingFetcher.fetchStopPlaceMapping(mappingUrl));
    }


    //Called from tests
    public void addStopPlaceMappings(Map<String, String> stopPlaceMap) {
        this.stopPlaceMappings.putAll(stopPlaceMap);
    }
}
