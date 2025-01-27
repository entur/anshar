package no.rutebanken.anshar.siri.processor;

import no.rutebanken.anshar.integration.SpringBootBaseTest;
import no.rutebanken.anshar.routes.siri.helpers.SiriObjectFactory;
import no.rutebanken.anshar.routes.siri.processor.CodespaceWhiteListProcessor;
import org.junit.jupiter.api.Test;
import uk.org.siri.siri21.EstimatedVehicleJourney;
import uk.org.siri.siri21.FramedVehicleJourneyRefStructure;
import uk.org.siri.siri21.Siri;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CodespaceWhiteListProcessorTest extends SpringBootBaseTest {

    CodespaceWhiteListProcessor processor = new CodespaceWhiteListProcessor("testing-testing", List.of("RUT", "ATB"));

    @Test
    public void testValidCodespaceIsNotRemoved() {
        Siri siri = createEt("RUT");
        assertEquals(1, getEstimatedVehicleJourneies(siri).size());
        processor.process(siri);
        assertFalse(getEstimatedVehicleJourneies(siri).isEmpty());
    }

    @Test
    public void testInvalidCodespaceIsRemoved() {

        Siri siri = createEt("TST");
        assertEquals(1, getEstimatedVehicleJourneies(siri).size());
        processor.process(siri);
        assertTrue(getEstimatedVehicleJourneies(siri).isEmpty());

    }
    @Test
    public void testInvalidCodespaceIsRemovedAndValidIsKept() {

        Siri siri = createEt("RUT", "TST");
        assertEquals(2, getEstimatedVehicleJourneies(siri).size());
        processor.process(siri);
        assertFalse(getEstimatedVehicleJourneies(siri).isEmpty());
        assertEquals(1, getEstimatedVehicleJourneies(siri).size());
    }

    private static List<EstimatedVehicleJourney> getEstimatedVehicleJourneies(Siri tst) {
        return tst.getServiceDelivery().getEstimatedTimetableDeliveries().get(0).getEstimatedJourneyVersionFrames().get(0).getEstimatedVehicleJourneies();
    }

    private Siri createEt(String... codespacePrefix) {

        List<EstimatedVehicleJourney> etList = new ArrayList<>();
        for (String prefix : codespacePrefix) {
            EstimatedVehicleJourney et = new EstimatedVehicleJourney();
            FramedVehicleJourneyRefStructure framedVehicleJourneyRef = new FramedVehicleJourneyRefStructure();
            framedVehicleJourneyRef.setDatedVehicleJourneyRef(prefix + ":ServiceJourney:1234");
            et.setFramedVehicleJourneyRef(framedVehicleJourneyRef);
            etList.add(et);
        }

        return new SiriObjectFactory(Instant.now()).createETServiceDelivery(etList);
    }
}
