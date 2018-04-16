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
import no.rutebanken.anshar.routes.siri.processor.BaneNorIdPlatformUpdaterService;
import no.rutebanken.anshar.routes.siri.transformer.ApplicationContextHolder;
import no.rutebanken.anshar.routes.siri.transformer.ValueAdapter;
import no.rutebanken.anshar.subscription.SiriDataType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Set;

public class JbvCodeMapper extends ValueAdapter {

    private final String datasetId;
    private final SiriDataType type;
    private Logger logger = LoggerFactory.getLogger(JbvCodeMapper.class);

    private static HealthManager healthManager;

    private final Set<String> unmappedAlreadyAdded;

    public JbvCodeMapper(SiriDataType type, String datasetId, Class clazz) {
        super(clazz);
        this.datasetId = datasetId;
        this.type = type;
        unmappedAlreadyAdded = new HashSet<>();
    }


    public String apply(String id) {
        if (id == null || id.isEmpty()) {
            return id;
        }
        BaneNorIdPlatformUpdaterService jbvCodeService = ApplicationContextHolder.getContext().getBean(BaneNorIdPlatformUpdaterService.class);

        if (healthManager == null) {
            healthManager = ApplicationContextHolder.getContext().getBean(HealthManager.class);
        }
        String mappedValue = null;
        try {
            if (jbvCodeService != null) {
                 mappedValue = jbvCodeService.get(id);
                if (mappedValue != null) {
                    return mappedValue;
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof JbvCodeMapper)) return false;

        JbvCodeMapper that = (JbvCodeMapper) o;

        return  (!super.getClassToApply().equals(that.getClassToApply()));
    }
}
