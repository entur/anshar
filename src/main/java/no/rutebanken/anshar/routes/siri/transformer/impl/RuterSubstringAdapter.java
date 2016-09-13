package no.rutebanken.anshar.routes.siri.transformer.impl;


import no.rutebanken.anshar.routes.siri.transformer.ValueAdapter;

public class RuterSubstringAdapter extends ValueAdapter {

    private char valueSeparator;

    public String apply(String text) {
        if (text != null && text.indexOf(valueSeparator) > 0) {
            return text.substring(0, text.indexOf(valueSeparator));
        }
        return text;
    }

    public RuterSubstringAdapter(Class clazz, char valueSeparator) {
        super(clazz);
        this.valueSeparator = valueSeparator;
    }
}
