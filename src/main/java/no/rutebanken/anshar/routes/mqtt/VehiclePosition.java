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
interface VehiclePosition {
    // digitransit-ui mapping
    String ROOT = "VP";
    String DESIGNATION = "desi";
    String DIRECTION = "dir";
    String OPERATOR = "oper";
    String VEHICLE_ID = "veh";
    String TIMESTAMP = "tst";
    String TSI = "tsi";
    String HEADING = "hdg";
    String SPEED = "spd";
    String LATITUDE = "lat";
    String LONGITUDE = "long";
    String DELAY = "dl";
    String ODOMETER = "odo";
    String ODAY = "oday";
    String JOURNEY = "jrn";
    String LINE = "line";
    String STARTTIME = "start";
    String STOP_INDEX = "stop_index";
    String TRIP_ID = "trip_id";
    String SOURCE = "source";
    String MODE = "mode";

    String UNKNOWN = "XXX";
}
