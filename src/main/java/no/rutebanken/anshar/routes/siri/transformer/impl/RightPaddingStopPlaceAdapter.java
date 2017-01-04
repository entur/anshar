package no.rutebanken.anshar.routes.siri.transformer.impl;


import no.rutebanken.anshar.routes.siri.transformer.ValueAdapter;

public class RightPaddingStopPlaceAdapter extends ValueAdapter {

    private int length;
    private String paddingValue;


    public RightPaddingStopPlaceAdapter(Class clazz, int length, String paddingValue) {
        super(clazz);
        this.length = length;
        this.paddingValue = paddingValue;
    }

    public String apply(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        if (text.length() == length) {
            return text + paddingValue;
        }
        return text;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RightPaddingStopPlaceAdapter)) return false;

        RightPaddingStopPlaceAdapter that = (RightPaddingStopPlaceAdapter) o;

        if (length != that.length) return false;
        if (!super.getClassToApply().equals(that.getClassToApply())) return false;
        return paddingValue.equals(that.paddingValue);

    }
}
