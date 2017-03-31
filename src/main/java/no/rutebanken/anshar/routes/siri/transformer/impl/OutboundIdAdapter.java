package no.rutebanken.anshar.routes.siri.transformer.impl;


import no.rutebanken.anshar.routes.siri.handlers.IdMappingPolicy;
import no.rutebanken.anshar.routes.siri.transformer.SiriValueTransformer;
import no.rutebanken.anshar.routes.siri.transformer.ValueAdapter;

public class OutboundIdAdapter extends ValueAdapter {

    private IdMappingPolicy idMappingPolicy;

    public OutboundIdAdapter(Class clazz, IdMappingPolicy idMappingPolicy) {
        super(clazz);
        this.idMappingPolicy = idMappingPolicy;
    }

    public String apply(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        if (text.contains(SiriValueTransformer.SEPARATOR)) {
            if (idMappingPolicy == IdMappingPolicy.DEFAULT) {
                text = getMappedId(text);
            } else if (idMappingPolicy == IdMappingPolicy.ORIGINAL_ID) {
                text = getOriginalId(text);
            } else if (idMappingPolicy == IdMappingPolicy.OTP_FRIENDLY_ID) {
                text = getOtpFriendly(text);
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

    public static String getOtpFriendly(String text) {
        return text.substring(text.indexOf(SiriValueTransformer.SEPARATOR)+1).replace(":", ".");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof OutboundIdAdapter)) return false;

        OutboundIdAdapter that = (OutboundIdAdapter) o;

        if (!super.getClassToApply().equals(that.getClassToApply())) return false;
        return idMappingPolicy == that.idMappingPolicy;

    }
}
