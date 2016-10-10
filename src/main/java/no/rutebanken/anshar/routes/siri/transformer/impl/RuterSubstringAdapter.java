package no.rutebanken.anshar.routes.siri.transformer.impl;


import no.rutebanken.anshar.routes.siri.transformer.ValueAdapter;

public class RuterSubstringAdapter extends ValueAdapter {

    private String replacementChar;
    private String valueSeparator;
    private int lengthAfterSeparator;

    public String apply(String text) {
        if (text != null) {
            if (text.contains(valueSeparator)) {
                lengthAfterSeparator = 2;
                if (text.substring(text.indexOf(valueSeparator)).length() > lengthAfterSeparator) {
                    text = text.replaceFirst(valueSeparator, "");
                } else {
                    text = text.replaceFirst(valueSeparator, replacementChar);
                }
            }
        }
        return text;
    }

    public RuterSubstringAdapter(Class clazz, char valueSeparator, char replacementChar, int lengthAfterSeparator) {
        super(clazz);
        this.valueSeparator = ""+valueSeparator;
        this.replacementChar = ""+replacementChar;
        this.lengthAfterSeparator = lengthAfterSeparator;
    }
}
