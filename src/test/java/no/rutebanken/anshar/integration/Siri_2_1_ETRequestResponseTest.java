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

package no.rutebanken.anshar.integration;

import io.restassured.http.ContentType;
import no.rutebanken.anshar.data.EstimatedTimetables;
import no.rutebanken.anshar.routes.siri.helpers.SiriObjectFactory;
import no.rutebanken.anshar.subscription.SiriDataType;
import org.entur.siri21.util.SiriXml;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import uk.org.siri.siri21.EstimatedCall;
import uk.org.siri.siri21.EstimatedVehicleJourney;
import uk.org.siri.siri21.FramedVehicleJourneyRefStructure;
import uk.org.siri.siri21.LineRef;
import uk.org.siri.siri21.Siri;
import uk.org.siri.siri21.StopPointRefStructure;
import uk.org.siri.siri21.VehicleOccupancyStructure;
import uk.org.siri.siri21.VehicleRef;

import java.math.BigInteger;
import java.time.ZonedDateTime;

import static io.restassured.RestAssured.given;
import static no.rutebanken.anshar.helpers.SleepUtil.sleep;
import static no.rutebanken.anshar.routes.HttpParameter.SIRI_VERSION_HEADER_NAME;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;

public class Siri_2_1_ETRequestResponseTest extends BaseHttpTest {

    private static final String lineRef = "TEST:Line:1";
    private static final String datedVehicleRef = "TEST:ServiceJourney:1";
    private static final int callCount = 5;

    private int alightingCount;

    @Autowired
    private EstimatedTimetables repo;

    @BeforeEach
    public void addData() {
        super.init();
        repo.clearAll();
        alightingCount = (int) (Math.random()*1000);
        repo.add(dataSource, createEstimatedVehicleJourney("1234", 0, callCount, ZonedDateTime.now().plusMinutes(30), Boolean.TRUE));
        sleep(250);
    }

    @Test
    public void test_2_0_Request() throws Exception {

        //Test default SIRI Request - i.e. SIRI 2.0
        Siri siriRequest = SiriObjectFactory.createServiceRequest(getSubscriptionSetup(SiriDataType.ESTIMATED_TIMETABLE));

        // Specifying 2.1-version in header
        given()
                .when()
                    .contentType(ContentType.XML)
                    .body(SiriXml.toXml(siriRequest))
                    .post("anshar/services")
                .then()
                    .statusCode(200)
                    .rootPath("Siri.ServiceDelivery.EstimatedTimetableDelivery.EstimatedJourneyVersionFrame.EstimatedVehicleJourney")
                        .body("LineRef", equalTo(lineRef))
                        .body("FramedVehicleJourneyRef.DatedVehicleJourneyRef", equalTo(datedVehicleRef))
                        .body("EstimatedCalls.EstimatedCall.ExpectedDepartureOccupancy.AlightingCount", not(""+alightingCount))
        ;
    }


    @Test
    public void test_2_1_Request() throws Exception {

        //Test SIRI 2.1 Request
        Siri siriRequest = SiriObjectFactory.createServiceRequest(getSubscriptionSetup(SiriDataType.ESTIMATED_TIMETABLE));
        given()
                .when()
                .contentType(ContentType.XML)
                .body(SiriXml.toXml(siriRequest))
                .header(SIRI_VERSION_HEADER_NAME, "2.1")
                .post("anshar/services")
                .then()
                .statusCode(200)
                .rootPath("Siri.ServiceDelivery.EstimatedTimetableDelivery.EstimatedJourneyVersionFrame.EstimatedVehicleJourney")
                .body("LineRef", equalTo(lineRef))
                .body("FramedVehicleJourneyRef.DatedVehicleJourneyRef", equalTo(datedVehicleRef))
                .body("EstimatedCalls.EstimatedCall[0].ExpectedDepartureOccupancy.AlightingCount", equalTo(""+alightingCount))
        ;

    }

    private EstimatedVehicleJourney createEstimatedVehicleJourney(String vehicleRefValue, int startOrder, int callCount, ZonedDateTime time, Boolean isComplete) {
        EstimatedVehicleJourney element = new EstimatedVehicleJourney();

        LineRef lineRefObj = new LineRef();
        lineRefObj.setValue(lineRef);
        element.setLineRef(lineRefObj);

        VehicleRef vehicleRef = new VehicleRef();
        vehicleRef.setValue(vehicleRefValue);
        element.setVehicleRef(vehicleRef);
        element.setMonitored(Boolean.TRUE);

        element.setIsCompleteStopSequence(isComplete);

        FramedVehicleJourneyRefStructure framedVehicleJourney = new FramedVehicleJourneyRefStructure();
        framedVehicleJourney.setDatedVehicleJourneyRef(datedVehicleRef);
        element.setFramedVehicleJourneyRef(framedVehicleJourney);

        EstimatedVehicleJourney.EstimatedCalls estimatedCalls = new EstimatedVehicleJourney.EstimatedCalls();
        for (int i = startOrder; i < callCount; i++) {

            StopPointRefStructure stopPointRef = new StopPointRefStructure();
            stopPointRef.setValue("NSR:TEST:" + i);
            EstimatedCall call = new EstimatedCall();
            call.setStopPointRef(stopPointRef);
            call.setAimedArrivalTime(time);
            call.setExpectedArrivalTime(time);
            call.setAimedDepartureTime(time);
            call.setExpectedDepartureTime(time);
            call.setOrder(BigInteger.valueOf(i));
            VehicleOccupancyStructure occupancy = new VehicleOccupancyStructure();
            occupancy.setAlightingCount(BigInteger.valueOf(alightingCount));
            call.getExpectedDepartureOccupancies().add(occupancy);
            estimatedCalls.getEstimatedCalls().add(call);
        }

        element.setEstimatedCalls(estimatedCalls);
        element.setRecordedAtTime(ZonedDateTime.now());

        return element;
    }
}
