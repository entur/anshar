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
import no.rutebanken.anshar.data.VehicleActivities;
import no.rutebanken.anshar.routes.siri.helpers.SiriObjectFactory;
import no.rutebanken.anshar.subscription.SiriDataType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.rutebanken.siri20.util.SiriXml;
import org.springframework.beans.factory.annotation.Autowired;
import uk.org.siri.siri20.CourseOfJourneyRefStructure;
import uk.org.siri.siri20.DirectionRefStructure;
import uk.org.siri.siri20.LineRef;
import uk.org.siri.siri20.LocationStructure;
import uk.org.siri.siri20.Siri;
import uk.org.siri.siri20.VehicleActivityStructure;
import uk.org.siri.siri20.VehicleRef;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

import static io.restassured.RestAssured.given;
import static no.rutebanken.anshar.helpers.SleepUtil.sleep;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;

public class VMRequestResponseTest extends BaseHttpTest {

    @Autowired
    private
    VehicleActivities repo;

    private final String vehicleReference = "1234";

    @BeforeEach
    public void addData() {
        super.init();
        repo.clearAll();
        repo.add(dataSource, createVehicleActivityStructure(ZonedDateTime.now(), vehicleReference, dataSource));
        sleep(250);
    }

    @Test
    public void testVMRequest() throws Exception {

        //Test SIRI Request
        Siri siriRequest = SiriObjectFactory.createServiceRequest(getSubscriptionSetup(SiriDataType.VEHICLE_MONITORING));
        given()
                .when()
                    .contentType(ContentType.XML)
                    .body(SiriXml.toXml(siriRequest))
                    .post("anshar/services")
                .then()
                    .statusCode(200)
                    .rootPath("Siri.ServiceDelivery.VehicleMonitoringDelivery.VehicleActivity.MonitoredVehicleJourney")
                        .body("VehicleRef", equalTo(vehicleReference))
                        .body("DataSource", equalTo(dataSource))
        ;
    }

    @Test
    public void testVMRequestExcludedIds() throws Exception {

        //Test SIRI Request
        Siri siriRequest = SiriObjectFactory.createServiceRequest(getSubscriptionSetup(SiriDataType.VEHICLE_MONITORING));
        given()
                .when()
                    .contentType(ContentType.XML)
                    .body(SiriXml.toXml(siriRequest))
                    .post("anshar/services?excludedDatasetIds=DUMMY")
                .then()
                    .statusCode(200)
                        .rootPath("Siri.ServiceDelivery.VehicleMonitoringDelivery.VehicleActivity.MonitoredVehicleJourney")
                        .body("VehicleRef", equalTo(vehicleReference))
                        .body("DataSource", equalTo(dataSource))
        ;

        given()
                .when()
                .contentType(ContentType.XML)
                .body(SiriXml.toXml(siriRequest))
                .post("anshar/services?excludedDatasetIds="+dataSource)
                .then()
                .statusCode(200)
                    .rootPath("Siri.ServiceDelivery.VehicleMonitoringDelivery")
                        .body("$", not(hasKey("VehicleActivity")))
        ;
    }

    @Test
    public void testLiteVMRequest() throws Exception {

        //Test SIRI Lite Request
        given()
                .when()
                .get("anshar/rest/vm")
                .then()
                .statusCode(200)
                .contentType(ContentType.XML)
                .rootPath("Siri.ServiceDelivery.VehicleMonitoringDelivery.VehicleActivity.MonitoredVehicleJourney")
                .body("VehicleRef", equalTo(vehicleReference))
                .body("DataSource", equalTo(dataSource))
        ;
    }


    @Test
    public void testLiteVMRequestWithExcludedDatasetIds() throws Exception {

        //Test SIRI Lite Request
        given()
                .when()
                    .get("anshar/rest/vm?excludedDatasetIds=DUMMY")
                .then()
                    .statusCode(200)
                .contentType(ContentType.XML)
                    .rootPath("Siri.ServiceDelivery.VehicleMonitoringDelivery.VehicleActivity.MonitoredVehicleJourney")
                        .body("VehicleRef", equalTo(vehicleReference))
                        .body("DataSource", equalTo(dataSource))
        ;

        given()
                .when()
                    .get("anshar/rest/vm?excludedDatasetIds="+dataSource)
                .then()
                    .statusCode(200)
                    .contentType(ContentType.XML)
                    .rootPath("Siri.ServiceDelivery.VehicleMonitoringDelivery")
                        .body("$", not(hasKey("VehicleActivity")))
        ;
    }

    private VehicleActivityStructure createVehicleActivityStructure(ZonedDateTime recordedAtTime, String vehicleReference, String dataSource) {
        VehicleActivityStructure element = new VehicleActivityStructure();
        element.setRecordedAtTime(recordedAtTime);
        element.setValidUntilTime(recordedAtTime.plusMinutes(10));

        VehicleActivityStructure.MonitoredVehicleJourney vehicleJourney = new VehicleActivityStructure.MonitoredVehicleJourney();

        VehicleRef vRef = new VehicleRef();
        vRef.setValue(vehicleReference);
        vehicleJourney.setVehicleRef(vRef);

        CourseOfJourneyRefStructure journeyRefStructure = new CourseOfJourneyRefStructure();
        journeyRefStructure.setValue("yadayada");
        vehicleJourney.setCourseOfJourneyRef(journeyRefStructure);

        DirectionRefStructure directionRef = new DirectionRefStructure();
        directionRef.setValue("1");
        vehicleJourney.setDirectionRef(directionRef);


        LineRef lineRef = new LineRef();
        lineRef.setValue("TEST:Line:1");
        vehicleJourney.setLineRef(lineRef);

        vehicleJourney.setDataSource(dataSource);

        //(lineRef == null && courseOfJourneyRef == null && directionRef == null) {


        LocationStructure location = new LocationStructure();
        location.setLatitude(BigDecimal.valueOf(10.63));
        location.setLongitude(BigDecimal.valueOf(63.10));
        vehicleJourney.setVehicleLocation(location);


        element.setMonitoredVehicleJourney(vehicleJourney);
        return element;
    }
}
