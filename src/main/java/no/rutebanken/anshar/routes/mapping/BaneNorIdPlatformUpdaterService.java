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

package no.rutebanken.anshar.routes.mapping;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Component
@Configuration
public class BaneNorIdPlatformUpdaterService {
    private final Logger logger = LoggerFactory.getLogger(BaneNorIdPlatformUpdaterService.class);

    private static final Object LOCK = new Object();

    private final ConcurrentMap<String, String> jbvCodeStopPlaceMappings = new ConcurrentHashMap<>();

    @Value("${anshar.mapping.jbvCode.gcs.path:}")
    private String jbvCodeStopPlaceMappingPath;

    @Value("${anshar.mapping.update.frequency.min:60}")
    private int updateFrequency = 60;


    @Value("${anshar.startup.wait.for.netex.initialization:false}")
    private boolean delayStartupForInitialization;

    @Value("${anshar.startup.load.mapping.data:true}")
    private boolean loadMappingData;

    @Autowired
    private StopPlaceRegisterMappingFetcher stopPlaceRegisterMappingFetcher;

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
        if (!loadMappingData) {
            logger.info("Loading Station-data disabled.");
            return;
        }
        int initialDelay = 0;
        if (delayStartupForInitialization) {
            initialDelay = updateFrequency;
            updateIdMapping();
        }

        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

        executor.scheduleAtFixedRate(this::updateIdMapping, initialDelay, updateFrequency, TimeUnit.MINUTES);


        logger.info("Initialized jbvCode_mapping-updater with url:{}, updateFrequency:{} min, initialDelay:{} min", jbvCodeStopPlaceMappingPath, updateFrequency, initialDelay);
    }

    private void updateIdMapping() {
        if (loadMappingData) {
            try {
                // re-entrant
                synchronized (LOCK) {
                    updateStopPlaceMapping();
                }
            } catch (Exception e) {
                logger.warn("Fetching data - caused exception", e);
            }
        }
    }

    private void updateStopPlaceMapping() throws IOException {
        if (jbvCodeStopPlaceMappingPath != null && !jbvCodeStopPlaceMappingPath.isEmpty()) {
            logger.info("Fetching mapping-data from {}", jbvCodeStopPlaceMappingPath);
            jbvCodeStopPlaceMappings.putAll(stopPlaceRegisterMappingFetcher.fetchStopPlaceMapping(jbvCodeStopPlaceMappingPath));
        }
    }
}
