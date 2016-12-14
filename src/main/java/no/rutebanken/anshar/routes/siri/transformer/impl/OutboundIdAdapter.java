package no.rutebanken.anshar.routes.siri.transformer.impl;


import no.rutebanken.anshar.routes.siri.transformer.SiriValueTransformer;
import no.rutebanken.anshar.routes.siri.transformer.ValueAdapter;

public class OutboundIdAdapter extends ValueAdapter {

    private boolean mapped;

    public OutboundIdAdapter(Class clazz, boolean mapped) {
        super(clazz);
        this.mapped = mapped;
    }

    public String apply(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        if (text.indexOf(SiriValueTransformer.SEPARATOR) > 0) {
            if (mapped) {
                text = getMappedId(text);
            } else {
                text = getOriginalId(text);
            }
        }
        return text;
    }

    public static String getOriginalId(String text) {
        return text.substring(0, text.indexOf(SiriValueTransformer.SEPARATOR));
    }

    public static String getMappedId(String text) {
        return text.substring(text.indexOf(SiriValueTransformer.SEPARATOR)+1);
    }
}
