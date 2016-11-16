package no.rutebanken.anshar.routes.siri.transformer.impl;


import no.rutebanken.anshar.routes.siri.transformer.ValueAdapter;

public class PrefixAdapter extends ValueAdapter {

    private String prefix;


    public PrefixAdapter(Class clazz, String prefix) {
        super(clazz);
        this.prefix = prefix;
    }

    public String apply(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        return prefix+text;
    }
}
