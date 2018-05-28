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

package no.rutebanken.anshar.outbound;

import no.rutebanken.anshar.routes.outbound.SiriHelper;
import no.rutebanken.anshar.routes.siri.helpers.SiriObjectFactory;
import org.junit.Before;
import org.junit.Test;
import uk.org.siri.siri20.LineRef;
import uk.org.siri.siri20.Siri;
import uk.org.siri.siri20.VehicleActivityStructure;
import uk.org.siri.siri20.VehicleRef;

import java.time.Instant;
import java.util.*;

import static org.junit.Assert.*;

public class SiriHelperTest {

    private SiriHelper siriHelper;
    private SiriObjectFactory siriObjectFactory;


    @Before
    public void setUp() {
        siriObjectFactory = new SiriObjectFactory(Instant.now());
        siriHelper = new SiriHelper(siriObjectFactory);
    }

    @Test
    public void testFilterVmDelivery() throws Exception {
        List<VehicleActivityStructure> vmElements = new ArrayList<>();
        String filterMatchingLineRef_1 = "1234";
        String filterMatchingVehicleRef_1 = "22";
        String filterMatchingLineRef_2 = "2345";

        vmElements.add(createVehicleActivity(filterMatchingLineRef_1, filterMatchingVehicleRef_1));
        vmElements.add(createVehicleActivity(filterMatchingLineRef_1, "3333"));
        vmElements.add(createVehicleActivity(filterMatchingLineRef_2, "222"));
        vmElements.add(createVehicleActivity("342435", "33"));
        vmElements.add(createVehicleActivity("66666", "33"));
        vmElements.add(createVehicleActivity("5444", "44"));


        assertFalse("Filters are not unique", filterMatchingLineRef_1.equals(filterMatchingLineRef_2));

        Siri serviceDelivery = siriObjectFactory.createVMServiceDelivery(vmElements);

        assertNotNull(serviceDelivery);
        assertNotNull(serviceDelivery.getServiceDelivery());
        assertNotNull(serviceDelivery.getServiceDelivery().getVehicleMonitoringDeliveries());
        assertTrue(serviceDelivery.getServiceDelivery().getVehicleMonitoringDeliveries().size() == 1);
        assertNotNull(serviceDelivery.getServiceDelivery().getVehicleMonitoringDeliveries().get(0).getVehicleActivities());
        assertTrue(serviceDelivery.getServiceDelivery().getVehicleMonitoringDeliveries().get(0).getVehicleActivities().size() == vmElements.size());

        Map<Class, Set<String>> filter = new HashMap<>();
        Set<String> matchingValues = new HashSet<>();
        matchingValues.add(filterMatchingLineRef_1);
        matchingValues.add(filterMatchingLineRef_2);
        filter.put(LineRef.class, matchingValues);

        Siri filtered = SiriHelper.filterSiriPayload(serviceDelivery, filter);


        assertNotNull(filtered);
        assertNotNull(filtered.getServiceDelivery());
        assertNotNull(filtered.getServiceDelivery().getVehicleMonitoringDeliveries());
        assertTrue(filtered.getServiceDelivery().getVehicleMonitoringDeliveries().size() == 1);
        assertNotNull(filtered.getServiceDelivery().getVehicleMonitoringDeliveries().get(0).getVehicleActivities());

        assertEquals("Non-matching element has not been removed", 3, filtered.getServiceDelivery().getVehicleMonitoringDeliveries().get(0).getVehicleActivities().size());
        assertEquals("Original object has been altered", 6, serviceDelivery.getServiceDelivery().getVehicleMonitoringDeliveries().get(0).getVehicleActivities().size());

        for (VehicleActivityStructure activityStructure : filtered.getServiceDelivery().getVehicleMonitoringDeliveries().get(0).getVehicleActivities()) {
            assertNotNull(activityStructure.getMonitoredVehicleJourney());
            assertNotNull(activityStructure.getMonitoredVehicleJourney().getLineRef());

            assertTrue("Filtered LineRef does not match",
                    matchingValues.contains(activityStructure.getMonitoredVehicleJourney().getLineRef().getValue()));
        }

        Map<Class, Set<String>> doublefilter = new HashMap<>();
        doublefilter.put(LineRef.class, new HashSet<>(Arrays.asList(filterMatchingLineRef_1)));
        doublefilter.put(VehicleRef.class, new HashSet<>(Arrays.asList(filterMatchingVehicleRef_1)));

        Siri doubleFiltered = SiriHelper.filterSiriPayload(serviceDelivery, doublefilter);


        assertNotNull(doubleFiltered);
        assertNotNull(doubleFiltered.getServiceDelivery());
        assertNotNull(doubleFiltered.getServiceDelivery().getVehicleMonitoringDeliveries());
        assertTrue(doubleFiltered.getServiceDelivery().getVehicleMonitoringDeliveries().size() == 1);
        assertNotNull(doubleFiltered.getServiceDelivery().getVehicleMonitoringDeliveries().get(0).getVehicleActivities());

        assertEquals("Non-matching element has not been removed", 1, doubleFiltered.getServiceDelivery().getVehicleMonitoringDeliveries().get(0).getVehicleActivities().size());
        assertEquals("Original object has been altered", 6, serviceDelivery.getServiceDelivery().getVehicleMonitoringDeliveries().get(0).getVehicleActivities().size());

        for (VehicleActivityStructure activityStructure : doubleFiltered.getServiceDelivery().getVehicleMonitoringDeliveries().get(0).getVehicleActivities()) {
            assertNotNull(activityStructure.getMonitoredVehicleJourney());
            assertNotNull(activityStructure.getMonitoredVehicleJourney().getLineRef());
            assertNotNull(activityStructure.getMonitoredVehicleJourney().getVehicleRef());

            assertTrue("Filtered LineRef does not match",
                    activityStructure.getMonitoredVehicleJourney().getLineRef().getValue().equals(filterMatchingLineRef_1));
            assertTrue("Filtered VehicleRef does not match",
                    activityStructure.getMonitoredVehicleJourney().getVehicleRef().getValue().equals(filterMatchingVehicleRef_1));
        }
    }

    @Test
    public void testImmutability() {
        List<VehicleActivityStructure> vmElements = new ArrayList<>();
        String filterMatchingLineRef_1 = "1234";
        String filterMatchingLineRef_2 = "2345";

        vmElements.add(createVehicleActivity(filterMatchingLineRef_1, "3333"));
        vmElements.add(createVehicleActivity(filterMatchingLineRef_2, "3333"));
        vmElements.add(createVehicleActivity("342435", "33"));

        Siri siri = siriObjectFactory.createVMServiceDelivery(vmElements);
        int sizeBefore = siri.getServiceDelivery().getVehicleMonitoringDeliveries().get(0).getVehicleActivities().size();

        Map<Class, Set<String>> filter = new HashMap<>();
        Set<String> matchingValues = new HashSet<>();
        matchingValues.add(filterMatchingLineRef_1);
        filter.put(LineRef.class, matchingValues);

        Siri filtered = SiriHelper.filterSiriPayload(siri, filter);

        int sizeAfter = siri.getServiceDelivery().getVehicleMonitoringDeliveries().get(0).getVehicleActivities().size();
        int filteredSizeAfter = filtered.getServiceDelivery().getVehicleMonitoringDeliveries().get(0).getVehicleActivities().size();

        assertEquals("Original object has been modified", sizeBefore, sizeAfter);

        assertTrue("VM-elements have not been filtered", sizeAfter > filteredSizeAfter);

        Map<Class, Set<String>> filter2 = new HashMap<>();
        Set<String> matchingValues2 = new HashSet<>();
        matchingValues2.add(filterMatchingLineRef_2);
        filter2.put(LineRef.class, matchingValues2);

        // Filtering original SIRI-data with different filter
        Siri filtered2 = SiriHelper.filterSiriPayload(siri, filter2);

        int sizeAfter2 = siri.getServiceDelivery().getVehicleMonitoringDeliveries().get(0).getVehicleActivities().size();
        int filteredSizeAfter2 = filtered.getServiceDelivery().getVehicleMonitoringDeliveries().get(0).getVehicleActivities().size();

        assertFalse("", filtered.getServiceDelivery().getVehicleMonitoringDeliveries().get(0).getVehicleActivities().get(0).getMonitoredVehicleJourney().getLineRef().getValue()
                .equals(filtered2.getServiceDelivery().getVehicleMonitoringDeliveries().get(0).getVehicleActivities().get(0).getMonitoredVehicleJourney().getLineRef().getValue()));
        assertEquals("Original size does not match", sizeAfter, sizeAfter2);
        assertEquals("Filtered size does not match", filteredSizeAfter, filteredSizeAfter2);
    }

    @Test
    public void testSplitDelivery(){

        List<VehicleActivityStructure> vmElements = new ArrayList<>();
        int elementCount = 1010;
        for (int i = 0; i < elementCount; i++) {

            vmElements.add(createVehicleActivity("" + i, "3333"));
        }

        Siri siri = siriObjectFactory.createVMServiceDelivery(vmElements);

        assertEquals(elementCount, siri.getServiceDelivery().getVehicleMonitoringDeliveries().get(0).getVehicleActivities().size());

        List<Siri> splitDeliveries = siriHelper.splitDeliveries(siri, 500);
        assertEquals(3, splitDeliveries.size());

        int splitElementCount = 0;
        for (Siri splitDelivery : splitDeliveries) {
            splitElementCount += splitDelivery.getServiceDelivery().getVehicleMonitoringDeliveries().get(0).getVehicleActivities().size();
        }

        assertEquals(elementCount, splitElementCount);
    }


    @Test
    public void testNotSplitNonDelivery(){


        Siri siri = siriObjectFactory.createHeartbeatNotification("ref");

        List<Siri> splitDeliveries = siriHelper.splitDeliveries(siri, 500);
        assertEquals(1, splitDeliveries.size());

        assertNotNull(splitDeliveries.get(0));
        assertEquals(siri, splitDeliveries.get(0));
    }

    private VehicleActivityStructure createVehicleActivity(String lineRefValue, String vehicleRefValue) {
        VehicleActivityStructure v = new VehicleActivityStructure();
        VehicleActivityStructure.MonitoredVehicleJourney mvj = new VehicleActivityStructure.MonitoredVehicleJourney();
        LineRef lineRef = new LineRef();
        lineRef.setValue(lineRefValue);
        VehicleRef vehicleRef = new VehicleRef();
        vehicleRef.setValue(vehicleRefValue);
        mvj.setLineRef(lineRef);
        mvj.setVehicleRef(vehicleRef);
        v.setMonitoredVehicleJourney(mvj);
        return v;
    }
}
