package no.rutebanken.anshar.metrics;

public enum SiriContent {
    TRIP_CANCELLATION("Trip cancelled"),
    STOP_CANCELLATION("Stop cancelled"),
    QUAY_CHANGED("Quay changed"),
    EXTRA_JOURNEY("Trip added"),
    OCCUPANCY_TRIP("Trip-occupancy"),
    OCCUPANCY_STOP("Stop-occupancy"),
    DESTINATION_DISPLAY("DestinationDisplay defined"),
    TOO_FAR_AHEAD("Update received more than 7 days ahead")
    ;

    private final String label;

    SiriContent(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

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
            case DESTINATION_DISPLAY:
                return ContentGroup.MISC;
            default:
                return ContentGroup.UNKNOWN;
        }
    }

    enum ContentGroup {
        CANCELLATION, OCCUPANCY, CHANGE, ADDED, MISC, UNKNOWN
    }
}
