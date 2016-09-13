package no.rutebanken.anshar.routes.siri.transformer;

public abstract class ValueAdapter {

    private Class clazz;

    public ValueAdapter(Class clazz) {
        this.clazz = clazz;
    }

    public Class getClassToApply() {
        return clazz;
    }

    protected abstract String apply(String value);
}
