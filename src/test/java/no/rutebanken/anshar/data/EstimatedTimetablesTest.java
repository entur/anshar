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

package no.rutebanken.anshar.data;

import junit.framework.TestCase;
import no.rutebanken.anshar.integration.SpringBootBaseTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import uk.org.siri.siri20.EstimatedCall;
import uk.org.siri.siri20.EstimatedVehicleJourney;
import uk.org.siri.siri20.Extensions;
import uk.org.siri.siri20.LineRef;
import uk.org.siri.siri20.NaturalLanguageStringStructure;
import uk.org.siri.siri20.RecordedCall;
import uk.org.siri.siri20.Siri;
import uk.org.siri.siri20.StopPointRef;
import uk.org.siri.siri20.VehicleRef;

import java.math.BigInteger;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import static junit.framework.TestCase.assertNotNull;
import static no.rutebanken.anshar.helpers.SleepUtil.sleep;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;


public class EstimatedTimetablesTest extends SpringBootBaseTest {

    @Autowired
    private EstimatedTimetables estimatedTimetables;

    @BeforeEach
    public void init() {
        estimatedTimetables.clearAll();
    }

    @Test
    public void testAddNull() {
        int previousSize = estimatedTimetables.getAll().size();
        estimatedTimetables.add("test", null);

        assertEquals(previousSize, estimatedTimetables.getAll().size());
    }

    @Test
    public void testAddJourney() {
        int previousSize = estimatedTimetables.getAll().size();
        EstimatedVehicleJourney element = createEstimatedVehicleJourney("1234-added", "4321", 0, 30, ZonedDateTime.now().plusMinutes(1), true);

        estimatedTimetables.add("test", element);

        assertTrue(estimatedTimetables.getAll().size() == previousSize + 1);
    }

    @Test
    public void testGetUpdatesOnly() {

        estimatedTimetables.add("test", createEstimatedVehicleJourney("1234-update", "4321", 0, 30, ZonedDateTime.now().plusHours(1), true));
        estimatedTimetables.add("test", createEstimatedVehicleJourney("2345-update", "4321", 0, 30, ZonedDateTime.now().plusHours(1), true));
        estimatedTimetables.add("test", createEstimatedVehicleJourney("3456-update", "4321", 0, 30, ZonedDateTime.now().plusHours(1), true));

        estimatedTimetables.commitChanges();
        sleep(250);

        // Added 3
        String requestorId = UUID.randomUUID().toString();

        assertEquals( 3, estimatedTimetables.getAllUpdates(requestorId, null).size());

        estimatedTimetables.add("test", createEstimatedVehicleJourney("4567-update", "4321", 0, 30, ZonedDateTime.now().plusHours(1), true));
        estimatedTimetables.commitChanges();

        sleep(250);
        //Added one
        assertEquals(1, estimatedTimetables.getAllUpdates(requestorId, null).size());


        //None added
        assertEquals("Returning partial updates when nothing has changed", 0, estimatedTimetables.getAllUpdates(requestorId, null).size());

        //Verify that all elements still exist
        assertEquals(4, estimatedTimetables.getAll().size());
    }

    @Test
    public void testGetPartialUpdatesOnly() {
        int previousSize = estimatedTimetables.getAll().size();
        estimatedTimetables.commitChanges();

        // Added 3
        String requestorId = "testGetPartialUpdatesOnly";

        //Get all existing
        assertEquals(previousSize, estimatedTimetables.getAllUpdates(requestorId, null).size());

        // Verify that no more updates exist
        assertEquals(0, estimatedTimetables.getAllUpdates(requestorId, null).size());

        String datasetId = UUID.randomUUID().toString();
        estimatedTimetables.add(datasetId, createEstimatedVehicleJourney("1234-partialupdate", "4321", 0, 30, ZonedDateTime.now().plusHours(1), true));
        estimatedTimetables.add(datasetId, createEstimatedVehicleJourney("2345-partialupdate", "4321", 0, 30, ZonedDateTime.now().plusHours(1), true));
        estimatedTimetables.add(datasetId, createEstimatedVehicleJourney("3456-partialupdate", "4321", 0, 30, ZonedDateTime.now().plusHours(1), true));

        sleep(250);

        estimatedTimetables.commitChanges();

        Siri siri = estimatedTimetables.createServiceDelivery(requestorId, null, 2);

        assertTrue(siri.getServiceDelivery().isMoreData());
        assertEquals(2, siri
                .getServiceDelivery().getEstimatedTimetableDeliveries().get(0)
                .getEstimatedJourneyVersionFrames().get(0)
                .getEstimatedVehicleJourneies().size());

        siri = estimatedTimetables.createServiceDelivery(requestorId, null, 2);

        assertFalse(siri.getServiceDelivery().isMoreData());
        assertEquals(1, siri
                .getServiceDelivery().getEstimatedTimetableDeliveries().get(0)
                .getEstimatedJourneyVersionFrames().get(0)
                .getEstimatedVehicleJourneies().size());

        siri = estimatedTimetables.createServiceDelivery(requestorId, null, 2);

        assertFalse(siri.getServiceDelivery().isMoreData());
        assertEquals(0, siri
                .getServiceDelivery().getEstimatedTimetableDeliveries().get(0)
                .getEstimatedJourneyVersionFrames().get(0)
                .getEstimatedVehicleJourneies().size());

        estimatedTimetables.add(datasetId, createEstimatedVehicleJourney("4567-partialupdate", "4321", 0, 30, ZonedDateTime.now().plusHours(1), true));

        sleep(250);
        estimatedTimetables.commitChanges();

        siri = estimatedTimetables.createServiceDelivery(requestorId, null, 2);

        assertFalse(siri.getServiceDelivery().isMoreData());
        assertEquals(1, siri
                .getServiceDelivery().getEstimatedTimetableDeliveries().get(0)
                .getEstimatedJourneyVersionFrames().get(0)
                .getEstimatedVehicleJourneies().size());

        siri = estimatedTimetables.createServiceDelivery(requestorId, null, 2);

        sleep(250);

        assertFalse(siri.getServiceDelivery().isMoreData());
        assertEquals(0, siri
                .getServiceDelivery().getEstimatedTimetableDeliveries().get(0)
                .getEstimatedJourneyVersionFrames().get(0)
                .getEstimatedVehicleJourneies().size());

        //Verify that all elements still exist
        assertEquals(previousSize+4, estimatedTimetables.getAll().size());
    }

    @Test
    public void testUpdatedJourney() {
        int previousSize = estimatedTimetables.getAll().size();

        ZonedDateTime departure = ZonedDateTime.now().plusHours(1);
        estimatedTimetables.add("test", createEstimatedVehicleJourney("12345", "4321", 0, 30, departure, true));
        int expectedSize = previousSize +1;
        assertTrue("Adding Journey did not add element.", estimatedTimetables.getAll().size() == expectedSize);

        estimatedTimetables.add("test", createEstimatedVehicleJourney("12345", "4321", 0, 30, departure, true));
        assertTrue("Updating Journey added element.", estimatedTimetables.getAll().size() == expectedSize);

        ZonedDateTime departure_2 = ZonedDateTime.now().plusHours(1);
        estimatedTimetables.add("test", createEstimatedVehicleJourney("54321", "4321", 0, 30, departure_2, true));
        expectedSize++;
        assertTrue("Adding Journey did not add element.", estimatedTimetables.getAll().size() == expectedSize);

        estimatedTimetables.add("test2", createEstimatedVehicleJourney("12345", "4321", 0, 30, departure_2, true));
        expectedSize++;
        assertEquals("Adding Journey for other vendor did not add element.", expectedSize, estimatedTimetables.getAll().size());
        assertEquals("Getting Journey for vendor did not return correct element-count.", previousSize+1, estimatedTimetables.getAll("test2").size());
        assertEquals("Getting Journey for vendor did not return correct element-count.", expectedSize-1, estimatedTimetables.getAll("test").size());

    }

    @Test
    public void testUpdatedJourneyWrongOrder() {
        int previousSize = estimatedTimetables.getAll().size();

        ZonedDateTime departure = ZonedDateTime.now().plusHours(1);
        String lineRefValue = "12345-wrongOrder";
        EstimatedVehicleJourney estimatedVehicleJourney = createEstimatedVehicleJourney(lineRefValue, "4321", 0, 20, departure, true);
        final ZonedDateTime firstRecordedAtTime = ZonedDateTime.now().plusMinutes(1);
        estimatedVehicleJourney.setRecordedAtTime(firstRecordedAtTime);

        estimatedTimetables.add("test", estimatedVehicleJourney);
        int expectedSize = previousSize +1;
        assertTrue("Adding Journey did not add element.", estimatedTimetables.getAll().size() == expectedSize);

        EstimatedVehicleJourney estimatedVehicleJourney1 = createEstimatedVehicleJourney(lineRefValue, "4321", 0, 20, departure, true);
        estimatedVehicleJourney1.setRecordedAtTime(ZonedDateTime.now());
        estimatedTimetables.add("test", estimatedVehicleJourney1);

        assertTrue("Updating Journey added element.", estimatedTimetables.getAll().size() == expectedSize);

        boolean checkedMatchingJourney = false;
        Collection<EstimatedVehicleJourney> all = estimatedTimetables.getAll();
        for (EstimatedVehicleJourney vehicleJourney : all) {
            if (lineRefValue.equals(vehicleJourney.getLineRef().getValue())) {
                assertEquals("Older request should have been ignored.", firstRecordedAtTime, vehicleJourney.getRecordedAtTime());
                checkedMatchingJourney = true;
            }
        }
        assertTrue("Did not check matching VehicleJourney", checkedMatchingJourney);
    }

    @Test
    public void testMapEstimatedToRecordedCall() {

        StopPointRef stopPoint = new StopPointRef();
        stopPoint.setValue("NSR:Stop:1234");

        NaturalLanguageStringStructure name = new NaturalLanguageStringStructure();
        name.setValue("Stop");

        NaturalLanguageStringStructure platform = new NaturalLanguageStringStructure();
        platform.setValue("19");

        ZonedDateTime aimedArrival = ZonedDateTime.now().plusMinutes(1);
        ZonedDateTime expectedArrival = ZonedDateTime.now().plusMinutes(2);
        ZonedDateTime aimedDeparture = ZonedDateTime.now().plusMinutes(4);
        ZonedDateTime expectedDeparture = ZonedDateTime.now().plusMinutes(5);

        Extensions extensions = new Extensions();

        EstimatedCall estimatedCall = new EstimatedCall();
        estimatedCall.setStopPointRef(stopPoint);
        estimatedCall.getStopPointNames().add(name);
        estimatedCall.setAimedArrivalTime(aimedArrival);
        estimatedCall.setExpectedArrivalTime(expectedArrival);
        estimatedCall.setAimedDepartureTime(aimedDeparture);
        estimatedCall.setExpectedDepartureTime(expectedDeparture);
        estimatedCall.setArrivalPlatformName(platform);
        estimatedCall.setDeparturePlatformName(platform);
        estimatedCall.setCancellation(Boolean.TRUE);
        estimatedCall.setExtraCall(Boolean.FALSE);
        estimatedCall.setOrder(BigInteger.ONE);
        estimatedCall.setExtensions(extensions);

        RecordedCall recordedCall = mapToRecordedCall(estimatedCall);

        assertEquals(stopPoint.getValue(), recordedCall.getStopPointRef().getValue());
        assertEquals(name.getValue(), recordedCall.getStopPointNames().get(0).getValue());
        assertEquals(platform.getValue(), recordedCall.getArrivalPlatformName().getValue());
        assertEquals(platform.getValue(), recordedCall.getDeparturePlatformName().getValue());

        assertEquals(aimedArrival, recordedCall.getAimedArrivalTime());
        assertEquals(expectedArrival, recordedCall.getExpectedArrivalTime());
        assertEquals(expectedArrival, recordedCall.getActualArrivalTime()); //estimated.expected is mapped to recorded.actual

        assertEquals(aimedDeparture, recordedCall.getAimedDepartureTime());
        assertEquals(expectedDeparture, recordedCall.getExpectedDepartureTime());
        assertEquals(expectedDeparture, recordedCall.getActualDepartureTime()); //estimated.expected is mapped to recorded.actual
        assertEquals(estimatedCall.isCancellation(), recordedCall.isCancellation());
        assertEquals(estimatedCall.isExtraCall(), recordedCall.isExtraCall());
        assertEquals(estimatedCall.getOrder(), recordedCall.getOrder());
        assertEquals(estimatedCall.getExtensions(), recordedCall.getExtensions());
    }


    public void testPartiallyUpdatedRecordedCalls() {
        int previousSize = estimatedTimetables.getAll().size();

        ZonedDateTime departure = ZonedDateTime.now().plusHours(1);
        //Adding ET-data with stops 0-20
        String lineRefValue = "12345-RecordedCalls";
        final int numberOfEstimatedCalls = 20;

        EstimatedVehicleJourney estimatedVehicleJourney = createEstimatedVehicleJourney(lineRefValue, "4321", 0, numberOfEstimatedCalls, departure, true);
        estimatedTimetables.add("test", estimatedVehicleJourney);
        assertEquals("Adding Journey did not add element.", previousSize+1, estimatedTimetables.getAll().size());

        List<EstimatedCall> estimatedCalls = new ArrayList<>(estimatedVehicleJourney.getEstimatedCalls().getEstimatedCalls());

        EstimatedVehicleJourney estimatedVehicleJourneyUpdate = createEstimatedVehicleJourney(lineRefValue, "4321", 0, 0, departure, false);
        estimatedVehicleJourneyUpdate.setEstimatedCalls(new EstimatedVehicleJourney.EstimatedCalls());
        estimatedVehicleJourneyUpdate.setRecordedCalls(new EstimatedVehicleJourney.RecordedCalls());

        // Updating journey with RecordedCalls for fourth and fifth stop
        estimatedVehicleJourneyUpdate.getRecordedCalls().getRecordedCalls().add(mapToRecordedCall(estimatedCalls.get(4)));
        estimatedVehicleJourneyUpdate.getRecordedCalls().getRecordedCalls().add(mapToRecordedCall(estimatedCalls.get(5)));

        EstimatedCall e = estimatedCalls.get(6);
        //Updating delay for first stop after RecordedCalls
        e.setExpectedArrivalTime(e.getAimedArrivalTime().plusSeconds(99));
        estimatedVehicleJourneyUpdate.getEstimatedCalls().getEstimatedCalls().add(e);

        final int expectedNumberOfRecordedCallsAfterUpdate = 6;

        estimatedTimetables.add("test", estimatedVehicleJourneyUpdate);

        Collection<EstimatedVehicleJourney> all = estimatedTimetables.getAll();
        for (EstimatedVehicleJourney vehicleJourney : all) {
            if (lineRefValue.equals(vehicleJourney.getLineRef().getValue())) {

                List<RecordedCall> recordedCallsList = vehicleJourney.getRecordedCalls().getRecordedCalls();
                List<EstimatedCall> estimatedCallsList = vehicleJourney.getEstimatedCalls().getEstimatedCalls();

                int rcSize = recordedCallsList.size();
                int etSize = estimatedCallsList.size();
                assertEquals("List of EstimatedCalls have not been merged as expected.", numberOfEstimatedCalls-expectedNumberOfRecordedCallsAfterUpdate, etSize);
                assertEquals("List of RecordedCalls have not been merged as expected.", expectedNumberOfRecordedCallsAfterUpdate, rcSize);

                EstimatedCall estimatedCall = estimatedCallsList.get(0);
                assertEquals(e.getStopPointRef().getValue(), estimatedCall.getStopPointRef().getValue());
                assertTrue(estimatedCall.getExpectedArrivalTime().minusSeconds(99).equals(estimatedCall.getAimedArrivalTime()));
            }
        }
    }


    @Test
    public void testCreateServiceDelivery() {
        String datasetId = "ServiceDeliveryTest";
        estimatedTimetables.add(datasetId, createEstimatedVehicleJourney("1234", "1", 0, 30, ZonedDateTime.now().plusHours(1), true));
        estimatedTimetables.add(datasetId, createEstimatedVehicleJourney("2345", "2", 0, 30, ZonedDateTime.now().plusHours(1), true));
        estimatedTimetables.add(datasetId, createEstimatedVehicleJourney("3456", "3", 0, 30, ZonedDateTime.now().plusHours(1), true));

        estimatedTimetables.commitChanges();

        // Added 3
        String requestorId = UUID.randomUUID().toString();

        Siri serviceDelivery_1 = estimatedTimetables.createServiceDelivery(requestorId, datasetId, 2, -1);

        assertNotNull(serviceDelivery_1);
        assertNotNull(serviceDelivery_1.getServiceDelivery());
        assertNotNull(serviceDelivery_1.getServiceDelivery().getEstimatedTimetableDeliveries());
        assertTrue(serviceDelivery_1.getServiceDelivery().getEstimatedTimetableDeliveries().get(0).getEstimatedJourneyVersionFrames().get(0).getEstimatedVehicleJourneies().size() == 2);
        assertTrue(serviceDelivery_1.getServiceDelivery().isMoreData());

        Siri serviceDelivery_2 = estimatedTimetables.createServiceDelivery(requestorId, datasetId, 2, -1);

        assertNotNull(serviceDelivery_2);
        assertNotNull(serviceDelivery_2.getServiceDelivery());
        assertNotNull(serviceDelivery_2.getServiceDelivery().getEstimatedTimetableDeliveries());
        assertTrue(serviceDelivery_2.getServiceDelivery().getEstimatedTimetableDeliveries().get(0).getEstimatedJourneyVersionFrames().get(0).getEstimatedVehicleJourneies().size() == 1);
        assertFalse(serviceDelivery_2.getServiceDelivery().isMoreData());

        Siri serviceDelivery_3 = estimatedTimetables.createServiceDelivery(requestorId, datasetId, 2, -1);

        assertNotNull(serviceDelivery_3);
        assertNotNull(serviceDelivery_3.getServiceDelivery());
        assertNotNull(serviceDelivery_3.getServiceDelivery().getEstimatedTimetableDeliveries());
        assertTrue(serviceDelivery_3.getServiceDelivery().getEstimatedTimetableDeliveries().get(0).getEstimatedJourneyVersionFrames().get(0).getEstimatedVehicleJourneies().size() == 0);
        assertFalse(serviceDelivery_3.getServiceDelivery().isMoreData());

    }

    @Test
    public void testServiceDeliveryWithPreviewInterval() {
        String datasetId = "PreviewIntervalTest";

        estimatedTimetables.add(datasetId, createEstimatedVehicleJourney("1234", "1", 0, 30, ZonedDateTime.now().plusMinutes(10), true));
        estimatedTimetables.add(datasetId, createEstimatedVehicleJourney("2345", "2", 0, 30, ZonedDateTime.now().plusMinutes(100), true));

        Siri serviceDelivery_1 = estimatedTimetables.createServiceDelivery(null, datasetId, 10, 20*60*1000);
        assertNotNull(serviceDelivery_1);
        assertNotNull(serviceDelivery_1.getServiceDelivery());
        assertNotNull(serviceDelivery_1.getServiceDelivery().getEstimatedTimetableDeliveries());
        assertTrue("Only first journey should have been returned", serviceDelivery_1.getServiceDelivery().getEstimatedTimetableDeliveries().get(0)
                                                    .getEstimatedJourneyVersionFrames().get(0).getEstimatedVehicleJourneies().size() == 1);
        assertFalse(serviceDelivery_1.getServiceDelivery().isMoreData());


        Siri serviceDelivery_10 = estimatedTimetables.createServiceDelivery(null, datasetId, 10, 110*60*1000);
        assertNotNull(serviceDelivery_10);
        assertNotNull(serviceDelivery_10.getServiceDelivery());
        assertNotNull(serviceDelivery_10.getServiceDelivery().getEstimatedTimetableDeliveries());
        assertTrue("Both journeys should have been returned", serviceDelivery_10.getServiceDelivery().getEstimatedTimetableDeliveries().get(0)
                .getEstimatedJourneyVersionFrames().get(0).getEstimatedVehicleJourneies().size() == 2);

        assertFalse(serviceDelivery_10.getServiceDelivery().isMoreData());


        EstimatedVehicleJourney estimatedVehicleJourneyWithCancellation = createEstimatedVehicleJourney("3456", "3", 0, 30, ZonedDateTime.now().plusMinutes(30), true);
        estimatedVehicleJourneyWithCancellation.setCancellation(Boolean.TRUE);
        estimatedTimetables.add(datasetId, estimatedVehicleJourneyWithCancellation);

        Siri serviceDelivery_30 = estimatedTimetables.createServiceDelivery(null, datasetId, 10, 110*60*1000);

        assertNotNull(serviceDelivery_30);
        assertNotNull(serviceDelivery_30.getServiceDelivery());
        assertNotNull(serviceDelivery_30.getServiceDelivery().getEstimatedTimetableDeliveries());
        assertTrue("Cancelled journey in the future should have been returned",serviceDelivery_30.getServiceDelivery().getEstimatedTimetableDeliveries().get(0)
                .getEstimatedJourneyVersionFrames().get(0).getEstimatedVehicleJourneies().size() == 3);
        assertFalse(serviceDelivery_30.getServiceDelivery().isMoreData());

        Siri serviceDelivery_3 = estimatedTimetables.createServiceDelivery(null, datasetId, 10, -1);

        assertNotNull(serviceDelivery_3);
        assertNotNull(serviceDelivery_3.getServiceDelivery());
        assertNotNull(serviceDelivery_3.getServiceDelivery().getEstimatedTimetableDeliveries());
        assertTrue("Default request should have returned all journeys",serviceDelivery_3.getServiceDelivery().getEstimatedTimetableDeliveries().get(0)
                .getEstimatedJourneyVersionFrames().get(0).getEstimatedVehicleJourneies().size() == 3);

        assertFalse(serviceDelivery_3.getServiceDelivery().isMoreData());


        String requestorId = UUID.randomUUID().toString();
        serviceDelivery_3 = estimatedTimetables.createServiceDelivery(requestorId, datasetId, 2, -1);

        assertNotNull(serviceDelivery_3);
        assertNotNull(serviceDelivery_3.getServiceDelivery());
        assertNotNull(serviceDelivery_3.getServiceDelivery().getEstimatedTimetableDeliveries());
        assertTrue("Default request should have returned all journeys",serviceDelivery_3.getServiceDelivery().getEstimatedTimetableDeliveries().get(0)
                .getEstimatedJourneyVersionFrames().get(0).getEstimatedVehicleJourneies().size() == 2);

        assertTrue(serviceDelivery_3.getServiceDelivery().isMoreData());

        serviceDelivery_3 = estimatedTimetables.createServiceDelivery(requestorId, datasetId, 2, -1);

        assertNotNull(serviceDelivery_3);
        assertNotNull(serviceDelivery_3.getServiceDelivery());
        assertNotNull(serviceDelivery_3.getServiceDelivery().getEstimatedTimetableDeliveries());
        assertTrue("Default request should have returned all journeys",serviceDelivery_3.getServiceDelivery().getEstimatedTimetableDeliveries().get(0)
                .getEstimatedJourneyVersionFrames().get(0).getEstimatedVehicleJourneies().size() == 1);

        assertFalse(serviceDelivery_3.getServiceDelivery().isMoreData());

    }

    @Test
    public void testExcludeDatasetIds() {

        String prefix = "excludedOnly-";

        EstimatedVehicleJourney journey_1 = createEstimatedVehicleJourney("1", "9", 0, 10, ZonedDateTime.now().plusHours(1), true);
        journey_1.setDataSource("test1");
        estimatedTimetables.add("test1", journey_1);

        EstimatedVehicleJourney journey_2 = createEstimatedVehicleJourney("2", "8", 0, 10, ZonedDateTime.now().plusHours(1), true);
        journey_2.setDataSource("test2");
        estimatedTimetables.add("test2", journey_2);

        EstimatedVehicleJourney journey_3 = createEstimatedVehicleJourney("3", "7", 0, 10, ZonedDateTime.now().plusHours(1), true);
        journey_3.setDataSource("test3");
        estimatedTimetables.add("test3", journey_3);

        assertExcludedId("test1");
        assertExcludedId("test2");
        assertExcludedId("test3");
    }

    @Test
    public void testGetAllMonitored() {
        String lineRefValue = "GetAll:Line:1";

        EstimatedVehicleJourney monitoredTarget = null;
        EstimatedVehicleJourney cancelledTarget = null;
        for (int i = 0; i < 10; i++) {
            EstimatedVehicleJourney estimatedVehicleJourney = createEstimatedVehicleJourney(lineRefValue, UUID.randomUUID() + " - " + i, 1, 20, ZonedDateTime.now().plusMinutes(2), true);
            if (i == 5) {
                estimatedVehicleJourney.setCancellation(false);
                estimatedVehicleJourney.setMonitored(true);
                monitoredTarget = estimatedVehicleJourney;
            }
            if (i == 6) {
                estimatedVehicleJourney.setCancellation(true);
                estimatedVehicleJourney.setMonitored(false);
                cancelledTarget = estimatedVehicleJourney;
            }
            estimatedTimetables.add("GetAll", estimatedVehicleJourney);
        }
        estimatedTimetables.commitChanges();

        assertNotNull(monitoredTarget);
        Collection<EstimatedVehicleJourney> allMonitored = estimatedTimetables.getAllMonitored();
        assertNotNull(allMonitored);
        TestCase.assertEquals(2, allMonitored.size());

        boolean monitoredMatch = false;
        boolean cancelledMatch = false;
        for (EstimatedVehicleJourney estimatedVehicleJourney : allMonitored) {
            if (monitoredTarget.getVehicleRef().getValue().equals(estimatedVehicleJourney.getVehicleRef().getValue())) {
                TestCase.assertFalse(monitoredTarget.isCancellation());
                monitoredMatch = true;
            }
            if (cancelledTarget.getVehicleRef().getValue().equals(estimatedVehicleJourney.getVehicleRef().getValue())) {
                TestCase.assertFalse(cancelledTarget.isMonitored());
                cancelledMatch = true;
            }
        }

        TestCase.assertTrue(monitoredMatch);
        TestCase.assertTrue(cancelledMatch);

    }



    private void assertExcludedId(String excludedDatasetId) {
        Siri serviceDelivery = estimatedTimetables.createServiceDelivery(null, null, null, Arrays.asList(excludedDatasetId), 100, -1);

        List<EstimatedVehicleJourney> journeys = serviceDelivery.getServiceDelivery().getEstimatedTimetableDeliveries().get(0).getEstimatedJourneyVersionFrames().get(0).getEstimatedVehicleJourneies();

        assertEquals(2, journeys.size());
        for (EstimatedVehicleJourney et : journeys) {
            assertFalse(et.getDataSource().equals(excludedDatasetId));
        }
    }


    private EstimatedVehicleJourney createEstimatedVehicleJourney(String lineRefValue, String vehicleRefValue, int startOrder, int callCount, ZonedDateTime arrival, Boolean isComplete) {
        return createEstimatedVehicleJourney(lineRefValue, vehicleRefValue, startOrder, callCount, arrival, arrival, isComplete);
    }

    private EstimatedVehicleJourney createEstimatedVehicleJourney(String lineRefValue, String vehicleRefValue, int startOrder, int callCount, ZonedDateTime arrival, ZonedDateTime departure, Boolean isComplete) {
        EstimatedVehicleJourney element = new EstimatedVehicleJourney();
        LineRef lineRef = new LineRef();
        lineRef.setValue(lineRefValue);
        element.setLineRef(lineRef);
        VehicleRef vehicleRef = new VehicleRef();
        vehicleRef.setValue(vehicleRefValue);
        element.setVehicleRef(vehicleRef);
        element.setIsCompleteStopSequence(isComplete);

        EstimatedVehicleJourney.EstimatedCalls estimatedCalls = new EstimatedVehicleJourney.EstimatedCalls();
        for (int i = startOrder; i < callCount; i++) {

            StopPointRef stopPointRef = new StopPointRef();
            stopPointRef.setValue("NSR:TEST:" + i);
            EstimatedCall call = new EstimatedCall();
                call.setStopPointRef(stopPointRef);
                call.setAimedArrivalTime(arrival);
                call.setExpectedArrivalTime(arrival);
                call.setAimedDepartureTime(departure);
                call.setExpectedDepartureTime(departure);
                call.setOrder(BigInteger.valueOf(i));
                call.setVisitNumber(BigInteger.valueOf(i));
            estimatedCalls.getEstimatedCalls().add(call);
        }

        element.setEstimatedCalls(estimatedCalls);
        element.setRecordedAtTime(ZonedDateTime.now());

        return element;
    }

    private EstimatedVehicleJourney createEstimatedVehicleJourneyWithRecordedCallsOnly(String lineRefValue, String vehicleRefValue, int startOrder, int callCount, ZonedDateTime time, Boolean isComplete) {
        EstimatedVehicleJourney element = new EstimatedVehicleJourney();
        LineRef lineRef = new LineRef();
        lineRef.setValue(lineRefValue);
        element.setLineRef(lineRef);
        VehicleRef vehicleRef = new VehicleRef();
        vehicleRef.setValue(vehicleRefValue);
        element.setVehicleRef(vehicleRef);
        element.setIsCompleteStopSequence(isComplete);

        EstimatedVehicleJourney.RecordedCalls recordedCallsCalls = new EstimatedVehicleJourney.RecordedCalls();
        for (int i = startOrder; i < callCount; i++) {

            StopPointRef stopPointRef = new StopPointRef();
            stopPointRef.setValue("NSR:TEST:"+i);

            RecordedCall call = new RecordedCall();
                call.setStopPointRef(stopPointRef);
                call.setAimedArrivalTime(time);
                call.setExpectedArrivalTime(time);
                call.setAimedDepartureTime(time);
                call.setExpectedDepartureTime(time);
                call.setOrder(BigInteger.valueOf(i));
            recordedCallsCalls.getRecordedCalls().add(call);
        }

        element.setRecordedCalls(recordedCallsCalls);
        element.setRecordedAtTime(ZonedDateTime.now());

        return element;
    }

    RecordedCall mapToRecordedCall(EstimatedCall call) {
        RecordedCall recordedCall = new RecordedCall();

        recordedCall.setStopPointRef(call.getStopPointRef());
        recordedCall.getStopPointNames().addAll(call.getStopPointNames());

        recordedCall.setOrder(call.getOrder());
        recordedCall.setVisitNumber(call.getVisitNumber());
        recordedCall.setCancellation(call.isCancellation());
        recordedCall.setExtraCall(call.isExtraCall());
        recordedCall.setExtensions(call.getExtensions());

        recordedCall.setAimedArrivalTime(call.getAimedArrivalTime());
        recordedCall.setExpectedArrivalTime(call.getExpectedArrivalTime());
        if (recordedCall.getExpectedArrivalTime() != null) {
            //Setting actual arrival from expected
            recordedCall.setActualArrivalTime(call.getExpectedArrivalTime());
        }
        recordedCall.setArrivalPlatformName(call.getArrivalPlatformName());

        recordedCall.setAimedDepartureTime(call.getAimedDepartureTime());
        recordedCall.setExpectedDepartureTime(call.getExpectedDepartureTime());
        if (recordedCall.getExpectedDepartureTime() != null) {
            //Setting actual departure from expected
            recordedCall.setActualDepartureTime(call.getExpectedDepartureTime());
        }
        recordedCall.setDeparturePlatformName(call.getDeparturePlatformName());
        return recordedCall;
    }
}
