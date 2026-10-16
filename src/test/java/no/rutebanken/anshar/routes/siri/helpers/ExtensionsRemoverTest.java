package no.rutebanken.anshar.routes.siri.helpers;

import org.junit.jupiter.api.Test;
import uk.org.siri.siri21.Extensions;
import uk.org.siri.siri21.FacilityConditionStructure;
import uk.org.siri.siri21.LineRef;
import uk.org.siri.siri21.MonitoredCallStructure;
import uk.org.siri.siri21.OnwardCallStructure;
import uk.org.siri.siri21.OnwardCallsStructure;
import uk.org.siri.siri21.StopPointRefStructure;
import uk.org.siri.siri21.VehicleActivityStructure;

import javax.xml.parsers.DocumentBuilderFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ExtensionsRemoverTest {

    @Test
    public void testExtensionsAreRemovedOnAllLevels() throws Exception {
        VehicleActivityStructure activity = new VehicleActivityStructure();
        activity.setExtensions(createExtensions());

        VehicleActivityStructure.MonitoredVehicleJourney journey = new VehicleActivityStructure.MonitoredVehicleJourney();
        LineRef lineRef = new LineRef();
        lineRef.setValue("TST:Line:1");
        journey.setLineRef(lineRef);

        MonitoredCallStructure monitoredCall = new MonitoredCallStructure();
        StopPointRefStructure stopPointRef = new StopPointRefStructure();
        stopPointRef.setValue("NSR:Quay:1");
        monitoredCall.setStopPointRef(stopPointRef);
        monitoredCall.setExtensions(createExtensions());
        journey.setMonitoredCall(monitoredCall);

        OnwardCallsStructure onwardCalls = new OnwardCallsStructure();
        OnwardCallStructure first = new OnwardCallStructure();
        first.setExtensions(createExtensions());
        OnwardCallStructure second = new OnwardCallStructure();
        second.setExtensions(createExtensions());
        onwardCalls.getOnwardCalls().add(first);
        onwardCalls.getOnwardCalls().add(second);
        journey.setOnwardCalls(onwardCalls);

        FacilityConditionStructure facilityCondition = new FacilityConditionStructure();
        facilityCondition.setExtensions(createExtensions());
        journey.getFacilityConditionElements().add(facilityCondition);

        activity.setMonitoredVehicleJourney(journey);

        assertTrue(ExtensionsRemover.removeExtensions(activity));

        assertNull(activity.getExtensions());
        assertNull(monitoredCall.getExtensions());
        assertNull(first.getExtensions());
        assertNull(second.getExtensions());
        assertNull(facilityCondition.getExtensions());

        // Everything else is kept
        assertEquals("TST:Line:1", activity.getMonitoredVehicleJourney().getLineRef().getValue());
        assertEquals("NSR:Quay:1", activity.getMonitoredVehicleJourney().getMonitoredCall().getStopPointRef().getValue());
        assertEquals(2, activity.getMonitoredVehicleJourney().getOnwardCalls().getOnwardCalls().size());
    }

    @Test
    public void testNothingIsReportedWhenThereAreNoExtensions() {
        VehicleActivityStructure activity = new VehicleActivityStructure();
        activity.setMonitoredVehicleJourney(new VehicleActivityStructure.MonitoredVehicleJourney());

        assertFalse(ExtensionsRemover.removeExtensions(activity));
        assertFalse(ExtensionsRemover.removeExtensions(null));
    }

    private static Extensions createExtensions() throws Exception {
        Extensions extensions = new Extensions();
        extensions.getAnies().add(DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument()
                .createElementNS("http://example.com/extension", "Extension"));
        return extensions;
    }
}
