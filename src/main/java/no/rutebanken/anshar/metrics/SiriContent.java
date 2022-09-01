package no.rutebanken.anshar.metrics;

public enum SiriContent {
    TRIP_CANCELLATION,
    STOP_CANCELLATION,
    QUAY_CHANGED,
    EXTRA_JOURNEY,
    OCCUPANCY_TRIP,
    OCCUPANCY_STOP;

    ContentGroup getGroup() {
        switch (this){
            case TRIP_CANCELLATION:
            case STOP_CANCELLATION:
                return ContentGroup.CANCELLATION;
            case OCCUPANCY_STOP:
            case OCCUPANCY_TRIP:
                return ContentGroup.OCCUPANCY;
            case QUAY_CHANGED:
                return ContentGroup.CHANGE;
            case EXTRA_JOURNEY:
                return ContentGroup.ADDED;
            default:
                return ContentGroup.UNKNOWN;
        }
    }

    enum ContentGroup {
        CANCELLATION, OCCUPANCY, CHANGE, ADDED, UNKNOWN
    }
}
