package no.rutebanken.anshar.routes.siri.transformer.impl;


import no.rutebanken.anshar.routes.siri.handlers.OutboundIdMappingPolicy;
import no.rutebanken.anshar.routes.siri.transformer.SiriValueTransformer;
import no.rutebanken.anshar.routes.siri.transformer.ValueAdapter;

public class OutboundIdAdapter extends ValueAdapter {

    private OutboundIdMappingPolicy outboundIdMappingPolicy;

    public OutboundIdAdapter(Class clazz, OutboundIdMappingPolicy outboundIdMappingPolicy) {
        super(clazz);
        this.outboundIdMappingPolicy = outboundIdMappingPolicy;
    }

    public String apply(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        if (text.contains(SiriValueTransformer.SEPARATOR)) {
            if (outboundIdMappingPolicy == OutboundIdMappingPolicy.DEFAULT) {
                text = getMappedId(text);
            } else if (outboundIdMappingPolicy == OutboundIdMappingPolicy.ORIGINAL_ID) {
                text = getOriginalId(text);
            } else if (outboundIdMappingPolicy == OutboundIdMappingPolicy.OTP_FRIENDLY_ID) {
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
        return outboundIdMappingPolicy == that.outboundIdMappingPolicy;

    }
}
