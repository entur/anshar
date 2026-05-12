/*
 * Licensed under the EUPL, Version 1.2 or – as soon they will be approved by
 * the European Commission - subsequent versions of the EUPL (the "Licence");
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 *
 *   https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and
 * limitations under the Licence.
 */
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
        ENSURE_INCREASING_TIMES_CANCELLED_STOPS("Ensure increasing times for cancelled stops"),
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
        OVERRIDE_MONITORED_FALSE("Monitored is set to false but a pattern-change has been detected"),
        OVERRIDE_MONITORED_NO_LONGER_TRUE("Monitored is set to false but was previously set to true"),
        UPDATED_CODESPACE("Updated the defined codespace"),
        REMOVE_INVALID_CODESPACE("Received data on codespace that is not valid for subscription"),
        LIMIT_CLOSED_SX_MESSAGE_END_TIME("Limit validity-period for closed SX-messages")
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