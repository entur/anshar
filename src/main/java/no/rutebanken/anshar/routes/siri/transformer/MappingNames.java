package no.rutebanken.anshar.routes.siri.transformer;

public enum MappingNames {

        REMOVE_EXPIRED_JOURNEYS("Remove expired journeys"),
        TRAIN_AND_STATION_PLATFORM_TO_NSR("Map station/platform to NSR"),
        TRAIN_STATION_TO_NSR("Map station to NSR"),
        ORIGINAL_ID_TO_NSR("Map stopId to NSR"),
        REMOVE_FREIGHT_TRAIN("Remove freight train"),
        REMOVE_UNKNOWN_DEPARTURE("Remove unknown departure"),
        RESTRUCTURE_DEPARTURE("Restructure departure"),
        ENSURE_INCREASING_TIMES("Ensure increasing times");

        private final String name;

        MappingNames(String name) {
                this.name = name;
        }

        @Override
        public String toString() {
                return name;
        }
}