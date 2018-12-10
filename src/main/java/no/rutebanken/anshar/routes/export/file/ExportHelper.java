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

package no.rutebanken.anshar.routes.export.file;

import no.rutebanken.anshar.routes.outbound.SiriHelper;
import no.rutebanken.anshar.routes.siri.handlers.OutboundIdMappingPolicy;
import no.rutebanken.anshar.routes.siri.transformer.SiriValueTransformer;
import no.rutebanken.anshar.subscription.helpers.MappingAdapterPresets;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import uk.org.siri.siri20.Siri;

@Component
public class ExportHelper {

    @Autowired
    private SiriHelper siriHelper;

    @Autowired
    private MappingAdapterPresets mappingAdapterPresets;

    public Siri exportET() {
        return transform(siriHelper.getAllET());
    }
    public Siri exportSX() {
        return transform(siriHelper.getAllSX());
    }
    public Siri exportVM() {
        return transform(siriHelper.getAllVM());
    }
    public Siri exportPT() {
        return transform(siriHelper.getAllPT());
    }

    private Siri transform(Siri body) {
        return SiriValueTransformer.transform(body, mappingAdapterPresets.getOutboundAdapters(OutboundIdMappingPolicy.DEFAULT));
    }

}
