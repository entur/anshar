package no.rutebanken.anshar.messages;

import java.util.Collection;
import java.util.List;

public interface SiriRepository<T> {

    int trackingPeriodMinutes = 2;

    Collection<T> getAll();

    int getSize();

    Collection<T> getAll(String datasetId);

    Collection<T> getAllUpdates(String requestorId, String datasetId);

    Collection<T> addAll(String datasetId, List<T> ptList);

    T add(String datasetId, T timetableDelivery);

    long getExpiration(T s);

    int cleanup();
}
