package no.rutebanken.anshar.routes.siri.transformer.impl;


import no.rutebanken.anshar.routes.siri.transformer.ValueAdapter;

public class RuterSubstringAdapter extends ValueAdapter {

    private final String replacementChar;
    private final String valueSeparator;
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RuterSubstringAdapter)) return false;

        RuterSubstringAdapter that = (RuterSubstringAdapter) o;

        if (lengthAfterSeparator != that.lengthAfterSeparator) return false;
        if (!replacementChar.equals(that.replacementChar)) return false;
        if (!super.getClassToApply().equals(that.getClassToApply())) return false;
        return valueSeparator.equals(that.valueSeparator);

    }
}
