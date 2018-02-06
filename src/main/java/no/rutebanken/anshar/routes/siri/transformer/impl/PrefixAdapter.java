package no.rutebanken.anshar.routes.siri.transformer.impl;


import no.rutebanken.anshar.routes.siri.transformer.ValueAdapter;

public class PrefixAdapter extends ValueAdapter {

    private final String prefix;


    public PrefixAdapter(Class clazz, String prefix) {
        super(clazz);
        this.prefix = prefix;
    }

    public String apply(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        if (text.startsWith(prefix)) {
            //Already prefixed
            return text;
        }
        return prefix+text;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PrefixAdapter)) return false;

        PrefixAdapter that = (PrefixAdapter) o;

        if (!super.getClassToApply().equals(that.getClassToApply())) return false;

        return prefix.equals(that.prefix);

    }
}
