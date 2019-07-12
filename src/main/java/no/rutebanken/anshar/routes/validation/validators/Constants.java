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

package no.rutebanken.anshar.routes.validation.validators;

public class Constants {

    /*
     * Defines xpaths used to resolve the correct elements when validating XML
     */

    private static final String SERVICE_DELIVERY = "Siri/ServiceDelivery/";

    public static final String PT_SITUATION_ELEMENT = SERVICE_DELIVERY + "SituationExchangeDelivery/Situations/PtSituationElement";
    private static final String AFFECTS = PT_SITUATION_ELEMENT + "/Affects";

    public static final String AFFECTED_NETWORK = AFFECTS + "/Networks/AffectedNetwork";
    public static final String AFFECTED_LINE = AFFECTED_NETWORK + "/AffectedLine";
    public static final String AFFECTED_ROUTE = AFFECTED_LINE + "/Routes/AffectedRoute";

    public static final String AFFECTED_STOP_POINT = AFFECTS + "/StopPoints/AffectedStopPoint";
    public static final String AFFECTED_STOP_PLACE = AFFECTS + "/StopPlaces/AffectedStopPlace";
    public static final String ACCESSIBILITY_ASSESSMENT = AFFECTED_STOP_PLACE + "/StopPlaces";
    public static final String AFFECTED_COMPONENTS = AFFECTED_STOP_PLACE + "/AffectedComponents";
    public static final String AFFECTED_VEHICLE_JOURNEY = AFFECTS + "/VehicleJourneys/AffectedVehicleJourney";


    public static final String ESTIMATED_VEHICLE_JOURNEY = SERVICE_DELIVERY + "EstimatedTimetableDelivery/EstimatedJourneyVersionFrame/EstimatedVehicleJourney";
    public static final String ESTIMATED_CALL = ESTIMATED_VEHICLE_JOURNEY + "/EstimatedCalls/EstimatedCall";
    public static final String RECORDED_CALL = ESTIMATED_VEHICLE_JOURNEY + "/RecordedCalls/RecordedCall";


    private static final String VEHICLE_ACTIVITY = SERVICE_DELIVERY + "/VehicleMonitoringDelivery/VehicleActivity";
    public static final String MONITORED_VEHICLE_JOURNEY =  VEHICLE_ACTIVITY + "/MonitoredVehicleJourney";
    public static final String MONITORED_CALL_STRUCTURE =  MONITORED_VEHICLE_JOURNEY + "/MonitoredCall";
}
