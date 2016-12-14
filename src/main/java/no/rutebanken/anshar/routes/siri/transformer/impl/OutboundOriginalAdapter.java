package no.rutebanken.anshar.routes.siri.transformer.impl;


import no.rutebanken.anshar.routes.siri.transformer.SiriValueTransformer;
import no.rutebanken.anshar.routes.siri.transformer.ValueAdapter;

public class OutboundOriginalAdapter extends ValueAdapter {

    private boolean mapped;

    public OutboundOriginalAdapter(Class clazz, boolean mapped) {
        super(clazz);
        this.mapped = mapped;
    }

    public String apply(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        if (text.indexOf(SiriValueTransformer.SEPARATOR) > 0) {
            if (mapped) {
                text = text.substring(text.indexOf(SiriValueTransformer.SEPARATOR)+1);
            } else {
                text = text.substring(0, text.indexOf(SiriValueTransformer.SEPARATOR));
            }
        }
        return text;
    }
}
