package no.rutebanken.anshar.routes.siri.transformer;

public enum MappingNames {

        REMOVE_EXPIRED_JOURNEYS("Remove expired journeys"),
        STOP_AND_PLATFORM_TO_NSR("Map station/platform to NSR"),
        TRAIN_STATION_TO_NSR("Map station to NSR"),
        ORIGINAL_ID_TO_NSR("Map stopId to NSR"),
        INVALID_NSR_ID("Invalid NSR id"),
        REMOVE_FREIGHT_TRAIN("Remove freight train"),
        REMOVE_UNKNOWN_DEPARTURE("Remove unknown departure"),
        RESTRUCTURE_DEPARTURE("Restructure departure"),
        REPLACE_TRAIN_NUMBER("Train-number replaced"),
        ENSURE_INCREASING_TIMES("Ensure increasing times"),
        ENSURE_INCREASING_INACCURATE_TIMES("Override missing realtime (predictionInaccurate)"),
        LINE_MAPPING("Create NeTEx-LineRef"),
        SET_MISSING_REPORT_TYPE("Set missing ReportType"),
        APPEND_PREFIX("Add prefix to create NeTEx-ID"),
        POPULATE_STOP_ASSIGNMENTS("Populate StopAssigment"),
        CREATE_RECORDED_AT_TIME("Create missing RecordedAtTime"),
        OVERRIDE_EMPTY_DESTINATION_DISPLAY_FOR_EXTRA_JOURNEYS("Override empty DestinationDisplay for ExtraJourneys"),
        ADD_ORDER_TO_CALLS("Add Order to calls"),
        EXTRA_JOURNEY_TOO_FAST("ExtraJourney is reported to travel too fast between stops"),
        EXTRA_JOURNEY_INVALID_MODE("ExtraJourney mode does not match stops in use"),
        EXTRA_JOURNEY_ID_EXISTS("ExtraJourney has ID that already exists in plan-data"),
        OVERRIDE_MONITORED_FALSE("Monitored is set to false, but a pattern-change has been detected")
        ;


        private final String name;

        MappingNames(String name) {
                this.name = name;
        }

        @Override
        public String toString() {
                return name;
        }
}