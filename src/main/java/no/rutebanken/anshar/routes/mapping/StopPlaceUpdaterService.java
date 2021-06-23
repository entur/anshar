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
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Component
@Configuration
public class StopPlaceUpdaterService {

    private final Logger logger = LoggerFactory.getLogger(StopPlaceUpdaterService.class);

    private static final Object LOCK = new Object();

    private transient final ConcurrentMap<String, String> stopPlaceMappings = new ConcurrentHashMap<>();

    private transient final Set<String> validNsrIds = new HashSet<>();

    @Autowired
    private StopPlaceRegisterMappingFetcher stopPlaceRegisterMappingFetcher;

    @Value("${anshar.mapping.quays.gcs.path}")
    private String quayMappingPath;

    @Value("${anshar.mapping.stopplaces.gcs.path}")
    private String stopPlaceMappingPath;

    @Value("${anshar.mapping.stopquayjson.gcs.path}")
    private String stopPlaceQuayJsonPath;

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

    /**
     * Returns true if provided id is included in the latest dataset from NSR
     * @param id
     * @return
     */
    public boolean isKnownId(String id) {
        return validNsrIds.isEmpty() || validNsrIds.contains(id);
    }

    @PostConstruct
    private void initialize() {

        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

        executor.scheduleAtFixedRate(this::updateIdMapping, 0, updateFrequency, TimeUnit.MINUTES);

        logger.info("Initialized id_mapping-updater with urls:{}, updateFrequency:{} min", new String[]{quayMappingPath, stopPlaceMappingPath}, updateFrequency);
    }

    private void updateIdMapping() {
        // re-entrant
        synchronized (LOCK) {
            updateStopPlaceMapping(quayMappingPath);
            updateStopPlaceMapping(stopPlaceMappingPath);
            updateStopPlacesAndQuays(stopPlaceQuayJsonPath);
        }
    }

    private void updateStopPlaceMapping(String mappingUrl) {
        logger.info("Fetching mapping data - start. Fetching mapping-data from {}", mappingUrl);

        stopPlaceMappings.putAll(stopPlaceRegisterMappingFetcher.fetchStopPlaceMapping(mappingUrl));
        logger.info("Fetching mapping data - done.");
    }

    private void updateStopPlacesAndQuays(String url) {
        logger.info("Fetching stops and quay data - start. Fetching mapping-data from {}", url);
        final Map<String, Collection<String>> stopQuayMap = stopPlaceRegisterMappingFetcher.fetchStopPlaceQuayJson(url);
        if (!stopQuayMap.isEmpty()) {
            validNsrIds.clear();

            int stopsCounter = stopQuayMap.size();
            int quayCounter = 0;
            for (String s : stopQuayMap.keySet()) {
                // Add StopPlace-id
                validNsrIds.add(s);

                //Add quay-ids
                final Collection<String> quayIds = stopQuayMap.get(s);
                quayCounter += quayIds.size();
                validNsrIds.addAll(quayIds);
            }

            logger.info("Fetching stops and quay data - done. Found {} stops, {} quays", stopsCounter, quayCounter);
        } else {
            logger.info("Fetching stops and quay data - done. No stops found");
        }
    }


    //Called from tests
    public void addStopPlaceMappings(Map<String, String> stopPlaceMap) {
        this.stopPlaceMappings.putAll(stopPlaceMap);
    }

    //Called from tests
    public void addStopQuays(Collection<String> stopQuays) {
        this.validNsrIds.addAll(stopQuays);
    }
}
