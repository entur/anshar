package no.rutebanken.anshar.routes.siri.transformer.impl;


import no.rutebanken.anshar.routes.siri.transformer.ValueAdapter;
import org.apache.commons.lang3.StringUtils;

public class LeftPaddingAdapter extends ValueAdapter {

    private int paddingLength;
    private char paddingChar;


    public LeftPaddingAdapter(Class clazz, int paddingLength, char paddingChar) {
        super(clazz);
        this.paddingLength = paddingLength;
        this.paddingChar = paddingChar;
    }

    public String apply(String text) {
        if (text != null && text.isEmpty()) {
            return text;
        }
        return StringUtils.leftPad(text, paddingLength, paddingChar);
    }
}
