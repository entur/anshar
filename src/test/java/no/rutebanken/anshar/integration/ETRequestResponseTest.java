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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.rutebanken.siri20.util.SiriXml;
import org.springframework.beans.factory.annotation.Autowired;
import uk.org.siri.siri20.EstimatedCall;
import uk.org.siri.siri20.EstimatedVehicleJourney;
import uk.org.siri.siri20.FramedVehicleJourneyRefStructure;
import uk.org.siri.siri20.LineRef;
import uk.org.siri.siri20.Siri;
import uk.org.siri.siri20.StopPointRef;
import uk.org.siri.siri20.VehicleRef;

import java.math.BigInteger;
import java.time.ZonedDateTime;

import static io.restassured.RestAssured.given;
import static no.rutebanken.anshar.helpers.SleepUtil.sleep;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;

public class ETRequestResponseTest extends BaseHttpTest {

    private static final String lineRef = "TEST:Line:1";
    private static final String datedVehicleRef = "TEST:ServiceJourney:1";
    private static final int callCount = 5;

    @Autowired
    private EstimatedTimetables repo;

    @BeforeEach
    public void addData() {
        super.init();
        repo.clearAll();
        repo.add(dataSource, createEstimatedVehicleJourney("1234", 0, callCount, ZonedDateTime.now().plusMinutes(30), Boolean.TRUE));
        sleep(250);
    }

    @Test
    public void testETRequest() throws Exception {

        //Test SIRI Request
        Siri siriRequest = SiriObjectFactory.createServiceRequest(getSubscriptionSetup(SiriDataType.ESTIMATED_TIMETABLE));
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
        ;
    }


    @Test
    public void testETRequestWithExcludedIds() throws Exception {

        //Test SIRI Request
        Siri siriRequest = SiriObjectFactory.createServiceRequest(getSubscriptionSetup(SiriDataType.ESTIMATED_TIMETABLE));
        given()
                .when()
                    .contentType(ContentType.XML)
                    .body(SiriXml.toXml(siriRequest))
                    .post("anshar/services?excludedDatasetIds=DUMMY")
                .then()
                    .statusCode(200)
                    .rootPath("Siri.ServiceDelivery.EstimatedTimetableDelivery.EstimatedJourneyVersionFrame.EstimatedVehicleJourney")
                        .body("LineRef", equalTo(lineRef))
                        .body("FramedVehicleJourneyRef.DatedVehicleJourneyRef", equalTo(datedVehicleRef))
        ;

        given()
                .when()
                    .contentType(ContentType.XML)
                    .body(SiriXml.toXml(siriRequest))
                    .post("anshar/services?excludedDatasetIds="+dataSource)
                .then()
                    .statusCode(200)
                    .rootPath("Siri.ServiceDelivery.EstimatedTimetableDelivery.EstimatedJourneyVersionFrame")
                        .body("$", not(hasKey("EstimatedVehicleJourney")))
        ;
    }

    @Test
    public void testLiteETRequest() throws Exception {

        //Test SIRI Lite Request
        given()
                .when()
                    .get("anshar/rest/et")
                .then()
                    .statusCode(200)
                    .contentType(ContentType.XML)
                .rootPath("Siri.ServiceDelivery.EstimatedTimetableDelivery.EstimatedJourneyVersionFrame.EstimatedVehicleJourney")
                    .body("LineRef", equalTo(lineRef))
                    .body("FramedVehicleJourneyRef.DatedVehicleJourneyRef", equalTo(datedVehicleRef))
        ;
    }

    @Test
    public void testLiteETRequestWithExcludedDatasetIds() throws Exception {

        //Test SIRI Lite Request
        given()
                .when()
                    .get("anshar/rest/et?excludedDatasetIds=DUMMY")
                .then()
                    .statusCode(200)
                    .contentType(ContentType.XML)
                .rootPath("Siri.ServiceDelivery.EstimatedTimetableDelivery.EstimatedJourneyVersionFrame.EstimatedVehicleJourney")
                    .body("LineRef", equalTo(lineRef))
                    .body("FramedVehicleJourneyRef.DatedVehicleJourneyRef", equalTo(datedVehicleRef))
        ;

        given()
                .when()
                    .get("anshar/rest/et?excludedDatasetIds="+dataSource)
                .then()
                    .statusCode(200)
                    .contentType(ContentType.XML)
                .rootPath("Siri.ServiceDelivery.EstimatedTimetableDelivery.EstimatedJourneyVersionFrame")
                        .body("$", not(hasKey("EstimatedVehicleJourney")))
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

            StopPointRef stopPointRef = new StopPointRef();
            stopPointRef.setValue("NSR:TEST:" + i);
            EstimatedCall call = new EstimatedCall();
            call.setStopPointRef(stopPointRef);
            call.setAimedArrivalTime(time);
            call.setExpectedArrivalTime(time);
            call.setAimedDepartureTime(time);
            call.setExpectedDepartureTime(time);
            call.setOrder(BigInteger.valueOf(i));
            estimatedCalls.getEstimatedCalls().add(call);
        }

        element.setEstimatedCalls(estimatedCalls);
        element.setRecordedAtTime(ZonedDateTime.now());

        return element;
    }
}
