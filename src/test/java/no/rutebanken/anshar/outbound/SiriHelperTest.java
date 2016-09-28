package no.rutebanken.anshar.outbound;

import no.rutebanken.anshar.routes.outbound.SiriHelper;
import no.rutebanken.anshar.routes.siri.SiriObjectFactory;
import org.junit.Test;
import uk.org.siri.siri20.*;

import java.util.*;

import static org.junit.Assert.*;

public class SiriHelperTest {
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

        Siri serviceDelivery = SiriObjectFactory.createVMServiceDelivery(vmElements);


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

        assertTrue("Non-matching element has not been removed",serviceDelivery.getServiceDelivery().getVehicleMonitoringDeliveries().get(0).getVehicleActivities().size() == 3);

        for (VehicleActivityStructure activityStructure : serviceDelivery.getServiceDelivery().getVehicleMonitoringDeliveries().get(0).getVehicleActivities()) {
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

        assertTrue("Non-matching element has not been removed",serviceDelivery.getServiceDelivery().getVehicleMonitoringDeliveries().get(0).getVehicleActivities().size() == 1);

        for (VehicleActivityStructure activityStructure : serviceDelivery.getServiceDelivery().getVehicleMonitoringDeliveries().get(0).getVehicleActivities()) {
            assertNotNull(activityStructure.getMonitoredVehicleJourney());
            assertNotNull(activityStructure.getMonitoredVehicleJourney().getLineRef());
            assertNotNull(activityStructure.getMonitoredVehicleJourney().getVehicleRef());

            assertTrue("Filtered LineRef does not match",
                    activityStructure.getMonitoredVehicleJourney().getLineRef().getValue().equals(filterMatchingLineRef_1));
            assertTrue("Filtered VehicleRef does not match",
                    activityStructure.getMonitoredVehicleJourney().getVehicleRef().getValue().equals(filterMatchingVehicleRef_1));
        }
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
