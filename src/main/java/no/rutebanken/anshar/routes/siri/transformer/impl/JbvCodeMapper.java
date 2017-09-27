package no.rutebanken.anshar.routes.siri.transformer.impl;


import no.rutebanken.anshar.routes.siri.processor.BaneNorIdPlatformUpdaterService;
import no.rutebanken.anshar.routes.siri.transformer.ApplicationContextHolder;
import no.rutebanken.anshar.routes.siri.transformer.ValueAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JbvCodeMapper extends ValueAdapter {

    private Logger logger = LoggerFactory.getLogger(JbvCodeMapper.class);

    public JbvCodeMapper(Class clazz) {
        super(clazz);
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
