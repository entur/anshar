package no.rutebanken.anshar.routes.siri.transformer.impl;


import no.rutebanken.anshar.routes.siri.transformer.ValueAdapter;

public class RuterSubstringAdapter extends ValueAdapter {

    private String replacementChar;
    private String valueSeparator;

    public String apply(String text) {
        if (text != null) {
            text = text.replaceFirst(valueSeparator, replacementChar);
            if (text.contains(valueSeparator)) {
                return text.substring(0, text.indexOf(valueSeparator));
            }
        }
        return text;
    }

    public RuterSubstringAdapter(Class clazz, char valueSeparator, char replacementChar) {
        super(clazz);
        this.valueSeparator = ""+valueSeparator;
        this.replacementChar = ""+replacementChar;
    }
}
