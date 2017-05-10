package no.rutebanken.anshar.messages;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Collection;
import java.util.List;

public abstract class SiriRepository<T> implements CamelContextAware{

    @Autowired
    protected CamelContext camelContext;

    abstract Collection<T> getAll();

    abstract int getSize();

    abstract Collection<T> getAll(String datasetId);

    abstract Collection<T> getAllUpdates(String requestorId, String datasetId);

    abstract Collection<T> addAll(String datasetId, List<T> ptList);

    abstract T add(String datasetId, T timetableDelivery);

    abstract long getExpiration(T s);

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }
}
