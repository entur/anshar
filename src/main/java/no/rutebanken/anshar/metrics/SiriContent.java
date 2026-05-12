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
package no.rutebanken.anshar.metrics;

public enum SiriContent {
    TRIP_CANCELLATION("Trip cancelled"),
    STOP_CANCELLATION("Stop cancelled"),
    QUAY_CHANGED("Quay changed"),
    EXTRA_JOURNEY("Trip added"),
    EXTRA_CALL("Stop added"),
    OCCUPANCY_TRIP("Trip-occupancy"),
    OCCUPANCY_STOP("Stop-occupancy"),
    DESTINATION_DISPLAY("DestinationDisplay defined"),
    TOO_FAR_AHEAD("Update received more than 7 days ahead"),
    TOO_LATE("Update received too late"),
    EXTENSION_REMOVED("Extension removed"),
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
            case EXTRA_CALL:
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
