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

package no.rutebanken.anshar.routes.mqtt;

/**
 * digitransit-ui message keys
 */
class VehiclePosition {
    // digitransit-ui mapping
    static final String ROOT = "VP";
    static final String DESIGNATION = "desi";
    static final String DIRECTION = "dir";
    static final String OPERATOR = "oper";
    static final String VEHICLE_ID = "veh";
    static final String TIMESTAMP = "tst";
    static final String TSI = "tsi";
    static final String HEADING = "hdg";
    static final String SPEED = "spd";
    static final String LATITUDE = "lat";
    static final String LONGITUDE = "long";
    static final String DELAY = "dl";
    static final String ODOMETER = "odo";
    static final String ODAY = "oday";
    static final String JOURNEY = "jrn";
    static final String LINE = "line";
    static final String STARTTIME = "start";
    static final String STOP_INDEX = "stop_index";
    static final String TRIP_ID = "trip_id";
    static final String SOURCE = "source";
    static final String MODE = "mode";

    static final String UNKNOWN = "XXX";

    private VehiclePosition() {
        // Dummy-constructor
    }
}
