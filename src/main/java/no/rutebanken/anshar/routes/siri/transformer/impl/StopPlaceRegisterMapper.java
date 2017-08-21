package no.rutebanken.anshar.routes.siri.transformer.impl;


import no.rutebanken.anshar.routes.siri.transformer.ApplicationContextHolder;
import no.rutebanken.anshar.routes.siri.transformer.ValueAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class StopPlaceRegisterMapper extends ValueAdapter {

    private Logger logger = LoggerFactory.getLogger(StopPlaceRegisterMapper.class);

    private List<String> prefixes;
    private String datatype;

    public StopPlaceRegisterMapper(Class clazz, List<String> prefixes) {
        this(clazz, prefixes, "Quay");
    }

    public StopPlaceRegisterMapper(Class clazz, List<String> prefixes, String datatype) {
        super(clazz);
        this.prefixes = prefixes;
        this.datatype = datatype;
    }


    public String apply(String id) {
        if (id == null || id.isEmpty()) {
            return id;
        }
        StopPlaceUpdaterService stopPlaceService = ApplicationContextHolder.getContext().getBean(StopPlaceUpdaterService.class);

        if (stopPlaceService != null){
            if (prefixes != null && !prefixes.isEmpty()) {

                for (String prefix : prefixes) {
                    String mappedValue = stopPlaceService.get(createCompleteId(prefix, id, datatype));
                    if (mappedValue != null) {
                        return mappedValue;
                    }
                }
            } else {
                String mappedValue = stopPlaceService.get(id);
                if (mappedValue != null) {
                    return mappedValue;
                }
            }
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
}
