package no.rutebanken.anshar.routes.siri.transformer.impl;


import no.rutebanken.anshar.routes.siri.transformer.ValueAdapter;

public class UnalteredAdapter extends ValueAdapter {


    public UnalteredAdapter(Class clazz) {
        super(clazz);
    }

    public String apply(String text) {
        return text;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UnalteredAdapter)) return false;

        UnalteredAdapter that = (UnalteredAdapter) o;

        return (super.getClassToApply().equals(that.getClassToApply()));

    }
}
