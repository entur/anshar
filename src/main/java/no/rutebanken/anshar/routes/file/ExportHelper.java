package no.rutebanken.anshar.routes.file;

import no.rutebanken.anshar.routes.outbound.SiriHelper;
import no.rutebanken.anshar.routes.siri.handlers.OutboundIdMappingPolicy;
import no.rutebanken.anshar.routes.siri.transformer.SiriValueTransformer;
import no.rutebanken.anshar.subscription.MappingAdapterPresets;
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
