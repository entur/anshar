package no.rutebanken.anshar.routes.siri.transformer.impl;


import no.rutebanken.anshar.routes.health.HealthManager;
import no.rutebanken.anshar.routes.siri.processor.BaneNorIdPlatformUpdaterService;
import no.rutebanken.anshar.routes.siri.transformer.ApplicationContextHolder;
import no.rutebanken.anshar.routes.siri.transformer.ValueAdapter;
import no.rutebanken.anshar.subscription.SubscriptionSetup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Set;

public class JbvCodeMapper extends ValueAdapter {

    private final String datasetId;
    private final SubscriptionSetup.SubscriptionType type;
    private Logger logger = LoggerFactory.getLogger(JbvCodeMapper.class);

    private static HealthManager healthManager;

    private Set<String> unmappedAlreadyAdded;

    public JbvCodeMapper(SubscriptionSetup.SubscriptionType type, String datasetId, Class clazz) {
        super(clazz);
        this.datasetId = datasetId;
        this.type = type;
        healthManager = ApplicationContextHolder.getContext().getBean(HealthManager.class);
        unmappedAlreadyAdded = new HashSet<>();
    }


    public String apply(String id) {
        if (id == null || id.isEmpty()) {
            return id;
        }
        BaneNorIdPlatformUpdaterService jbvCodeService = ApplicationContextHolder.getContext().getBean(BaneNorIdPlatformUpdaterService.class);

        if (jbvCodeService != null){
            String mappedValue = jbvCodeService.get(id);
            if (mappedValue != null) {
                return mappedValue;
            }
        }
        logger.warn("Unable to find mapped value for id {}", id);

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
