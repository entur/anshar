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


import no.rutebanken.anshar.routes.siri.handlers.OutboundIdMappingPolicy;
import no.rutebanken.anshar.routes.siri.transformer.SiriValueTransformer;
import no.rutebanken.anshar.routes.siri.transformer.ValueAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OutboundIdAdapter extends ValueAdapter {

    private final Logger logger = LoggerFactory.getLogger(OutboundIdAdapter.class);

    private final OutboundIdMappingPolicy outboundIdMappingPolicy;

    public OutboundIdAdapter(Class clazz, OutboundIdMappingPolicy outboundIdMappingPolicy) {
        super(clazz);
        this.outboundIdMappingPolicy = outboundIdMappingPolicy;
    }

    public static String createCombinedId(String originalId, String mappedId) {
        return originalId + SiriValueTransformer.SEPARATOR + mappedId;
    }

    public String apply(String text) {
        try {
            if (text == null || text.isEmpty()) {
                return text;
            }
            if (text.contains(SiriValueTransformer.SEPARATOR)) {
                if (outboundIdMappingPolicy == OutboundIdMappingPolicy.DEFAULT) {
                    text = getMappedId(text);
                }
                else if (outboundIdMappingPolicy == OutboundIdMappingPolicy.ORIGINAL_ID) {
                    text = getOriginalId(text);
                }
            }
        } catch (NullPointerException npe) {
            logger.warn("Caught NullPointerException while mapping ID.", npe);
            logger.warn("Caught exception when mapping value '{}'", text);
            throw npe;
        }
        return text;
    }

    public static String getOriginalId(String text) {
        if (text.contains(SiriValueTransformer.SEPARATOR)) {
            return text.substring(0, text.indexOf(SiriValueTransformer.SEPARATOR));
        }
        return text;
    }

    public static String getMappedId(String text) {
        if (text.contains(SiriValueTransformer.SEPARATOR)) {
            return text.substring(text.indexOf(SiriValueTransformer.SEPARATOR) + 1);
        }
        return text;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof OutboundIdAdapter)) return false;

        OutboundIdAdapter that = (OutboundIdAdapter) o;

        if (!super.getClassToApply().equals(that.getClassToApply())) return false;
        return outboundIdMappingPolicy == that.outboundIdMappingPolicy;

    }
}
