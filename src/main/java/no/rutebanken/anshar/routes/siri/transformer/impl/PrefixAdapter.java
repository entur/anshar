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


import no.rutebanken.anshar.routes.siri.transformer.ValueAdapter;
import no.rutebanken.anshar.subscription.SiriDataType;

import static no.rutebanken.anshar.routes.siri.transformer.MappingNames.APPEND_PREFIX;

public class PrefixAdapter extends ValueAdapter {

    private final String prefix;
    private final String datasetId;
    private final SiriDataType dataType;
    private final String pattern;


    public PrefixAdapter(SiriDataType dataType, String datasetId, Class clazz, String prefix) {
        super(clazz);
        this.dataType = dataType;
        this.datasetId = datasetId;
        this.prefix = prefix;
        this.pattern = prefix.replaceAll(datasetId, "");
    }

    public String apply(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        if (text.startsWith(prefix)) {
            //Already prefixed
            return text;
        }
        if (text.contains(pattern)) {
            //Do not prefix if the text already matches the intended pattern, could be from a different codespace
            return text;
        }
        getMetricsService().registerDataMapping(dataType, datasetId, APPEND_PREFIX, 1);
        return prefix+text;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PrefixAdapter)) return false;

        PrefixAdapter that = (PrefixAdapter) o;

        if (!super.getClassToApply().equals(that.getClassToApply())) return false;

        return prefix.equals(that.prefix);

    }
}
