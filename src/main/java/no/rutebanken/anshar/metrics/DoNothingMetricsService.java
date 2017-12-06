package no.rutebanken.anshar.metrics;

import no.rutebanken.anshar.subscription.SubscriptionSetup;

public class DoNothingMetricsService implements MetricsService {
    @Override
    public void registerIncomingData(SubscriptionSetup.SubscriptionType subscriptionType, String agencyId, int count) {

    }
}
