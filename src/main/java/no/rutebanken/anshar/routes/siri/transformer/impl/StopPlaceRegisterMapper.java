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

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static no.rutebanken.anshar.routes.siri.transformer.MappingNames.INVALID_NSR_ID;
import static no.rutebanken.anshar.routes.siri.transformer.MappingNames.ORIGINAL_ID_TO_NSR;

public class StopPlaceRegisterMapper extends ValueAdapter {

    private Logger logger = LoggerFactory.getLogger(StopPlaceRegisterMapper.class);

    private static HealthManager healthManager;

    private final List<String> prefixes;
    private final String datatype;

    private static final Set<String> unmappedAlreadyAdded = new HashSet<>();

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
                if (unmappedAlreadyAdded.add(id)) {
                    if (metricsEnabled) {
                        getMetricsService().registerDataMapping(type, datasetId, INVALID_NSR_ID, 1);
                    }
                    healthManager.addUnmappedId(type, datasetId, id);
                }
            } else if (unmappedAlreadyAdded.contains(id)) {
                healthManager.removeUnmappedId(type, datasetId, id);
                unmappedAlreadyAdded.remove(id);
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
            if (mappedValue != null && unmappedAlreadyAdded.contains(id)) {
                healthManager.removeUnmappedId(type, datasetId, id);
                unmappedAlreadyAdded.remove(id);
            }
        }

        if (unmappedAlreadyAdded.add(id)) {
            healthManager.addUnmappedId(type, datasetId, id);
        }
        return id;
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
