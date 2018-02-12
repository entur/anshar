package no.rutebanken.anshar.data;

import java.util.Collection;
import java.util.List;

interface SiriRepository<T> {

    Collection<T> getAll();

    int getSize();

    Collection<T> getAll(String datasetId);

    Collection<T> getAllUpdates(String requestorId, String datasetId);

    Collection<T> addAll(String datasetId, List<T> ptList);

    T add(String datasetId, T timetableDelivery);

    long getExpiration(T s);
}
