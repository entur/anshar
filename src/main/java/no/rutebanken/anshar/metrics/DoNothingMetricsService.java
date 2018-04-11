package no.rutebanken.anshar.metrics;

import no.rutebanken.anshar.subscription.SiriDataType;

public class DoNothingMetricsService implements MetricsService {
    @Override
    public void registerIncomingData(SiriDataType subscriptionType, String agencyId, int count) {

    }
}
