package no.rutebanken.anshar.routes.siri.transformer;

import java.io.Serializable;

public abstract class ValueAdapter implements Serializable {

    private Class clazz;

    public ValueAdapter(Class clazz) {
        this.clazz = clazz;
    }

    public Class getClassToApply() {
        return clazz;
    }

    protected abstract String apply(String value);

    public String toString() {
        return this.getClass().getSimpleName() + "[" + (clazz != null ? clazz.getSimpleName():"null") + "]";
    }
}
