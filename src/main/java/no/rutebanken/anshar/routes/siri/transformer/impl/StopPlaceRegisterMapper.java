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


import no.rutebanken.anshar.routes.health.HealthManager;
import no.rutebanken.anshar.routes.mapping.StopPlaceUpdaterService;
import no.rutebanken.anshar.routes.siri.transformer.ApplicationContextHolder;
import no.rutebanken.anshar.routes.siri.transformer.ValueAdapter;
import no.rutebanken.anshar.subscription.SiriDataType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static no.rutebanken.anshar.routes.siri.transformer.MappingNames.INVALID_NSR_ID;
import static no.rutebanken.anshar.routes.siri.transformer.MappingNames.ORIGINAL_ID_TO_NSR;

public class StopPlaceRegisterMapper extends ValueAdapter {

    private Logger logger = LoggerFactory.getLogger(StopPlaceRegisterMapper.class);

    private static HealthManager healthManager;

    private final List<String> prefixes;
    private final String datatype;

    // Limits memory usage if a provider keeps sending new, unknown ids - limit is per dataset and datatype
    // Same limit as HealthManager, to ensure that all tracked ids are reported when there is room
    private static final int MAX_TRACKED_UNMAPPED_IDS = HealthManager.MAX_PENDING_UNMAPPED_IDS;

    private static final Map<String, Set<String>> unmappedAlreadyAdded = new ConcurrentHashMap<>();

    private final String datasetId;
    private final SiriDataType type;
    private boolean metricsEnabled = true;

    public StopPlaceRegisterMapper(SiriDataType type, String datasetId, Class clazz, List<String> prefixes) {
        this(type, datasetId, clazz, prefixes, "Quay");
    }

    public StopPlaceRegisterMapper(SiriDataType type, String datasetId, Class clazz, List<String> prefixes, String datatype) {
        super(clazz);
        this.type = type;
        this.datasetId = datasetId;
        this.prefixes = prefixes;
        this.datatype = datatype;
    }


    public String apply(String id) {
        StopPlaceUpdaterService stopPlaceService = ApplicationContextHolder.getContext().getBean(StopPlaceUpdaterService.class);

        if (healthManager == null) {
            healthManager = ApplicationContextHolder.getContext().getBean(HealthManager.class);
        }

        if (id == null || id.isEmpty() || id.startsWith("NSR:")) {
            if (!stopPlaceService.isKnownId(id)) {
                if (trackUnmapped(id)) {
                    if (metricsEnabled) {
                        getMetricsService().registerDataMapping(type, datasetId, INVALID_NSR_ID, 1);
                    }
                    healthManager.addUnmappedId(type, datasetId, id);
                }
            } else if (trackedUnmappedIds().contains(id)) {
                healthManager.removeUnmappedId(type, datasetId, id);
                trackedUnmappedIds().remove(id);
            }
            return id;
        }

        String mappedValue = null;

        try {
            if (stopPlaceService != null) {
                if (prefixes != null && !prefixes.isEmpty()) {

                    for (String prefix : prefixes) {
                        mappedValue = stopPlaceService.get(createCompleteId(prefix, id, datatype));
                        if (mappedValue != null) {
                            if (metricsEnabled) {
                                getMetricsService().registerDataMapping(type, datasetId, ORIGINAL_ID_TO_NSR, 1);
                            }
                            return mappedValue;
                        }
                    }
                } else {
                    mappedValue = stopPlaceService.get(id);
                    if (mappedValue != null) {
                        if (metricsEnabled) {
                            getMetricsService().registerDataMapping(type, datasetId, ORIGINAL_ID_TO_NSR, 1);
                        }
                        return mappedValue;
                    }
                }
            }
        } finally {
            if (mappedValue != null && trackedUnmappedIds().contains(id)) {
                healthManager.removeUnmappedId(type, datasetId, id);
                trackedUnmappedIds().remove(id);
            }
        }

        if (trackUnmapped(id)) {
            healthManager.addUnmappedId(type, datasetId, id);
        }
        return id;
    }

    /**
     * @return true if the id has not been seen before - false if already tracked, or if the limit has been reached
     */
    private boolean trackUnmapped(String id) {
        final Set<String> trackedIds = trackedUnmappedIds();
        synchronized (trackedIds) {
            if (trackedIds.size() >= MAX_TRACKED_UNMAPPED_IDS) {
                return false;
            }
            boolean added = trackedIds.add(id);
            if (added && trackedIds.size() == MAX_TRACKED_UNMAPPED_IDS) {
                logger.warn("Reached limit of {} unmapped ids for {} {} - new unmapped ids will not be reported", MAX_TRACKED_UNMAPPED_IDS, datasetId, type);
            }
            return added;
        }
    }

    private Set<String> trackedUnmappedIds() {
        return unmappedAlreadyAdded.computeIfAbsent(datasetId + "|" + type, key -> Collections.synchronizedSet(new HashSet<>()));
    }

    private String createCompleteId(String prefix, String id, String datatype) {
        String nsrIdPrefix = new StringBuilder().append(prefix).append(":").append(datatype).append(":").toString();
        if (id.startsWith(nsrIdPrefix)) {
            return id;
        }
        return new StringBuilder().append(nsrIdPrefix).append(id).toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof StopPlaceRegisterMapper)) return false;

        StopPlaceRegisterMapper that = (StopPlaceRegisterMapper) o;

        if (!super.getClassToApply().equals(that.getClassToApply())) return false;
        return prefixes.equals(that.prefixes);
    }

    public void disableMetrics() {
        this.metricsEnabled = false;
    }
}
