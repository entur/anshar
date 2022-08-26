package no.rutebanken.anshar.siri.processor;

import no.rutebanken.anshar.integration.SpringBootBaseTest;
import no.rutebanken.anshar.routes.siri.helpers.SiriObjectFactory;
import no.rutebanken.anshar.routes.siri.processor.ExtraJourneyDestinationDisplayPostProcessor;
import org.junit.jupiter.api.Test;
import uk.org.siri.siri21.EstimatedCall;
import uk.org.siri.siri21.EstimatedVehicleJourney;
import uk.org.siri.siri21.NaturalLanguageStringStructure;
import uk.org.siri.siri21.Siri;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static no.rutebanken.anshar.routes.siri.processor.ExtraJourneyDestinationDisplayPostProcessor.DUMMY_DESTINATION_DISPLAY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ExtraJourneyDestinationDisplayPostProcessorTest extends SpringBootBaseTest {

    private ExtraJourneyDestinationDisplayPostProcessor processor = new ExtraJourneyDestinationDisplayPostProcessor("TST");

    @Test
    public void testSetDestinationDisplay() {
        Siri extraJourney = createEt("Oslo", true);

        processor.process(extraJourney);

        final List<NaturalLanguageStringStructure> extraJourneyDisplays = getFirstDestinationDisplays(
            extraJourney);

        assertFalse(extraJourneyDisplays.isEmpty());
        assertEquals("Oslo", extraJourneyDisplays.get(0).getValue());
    }

    @Test
    public void testEmptyDestinationDisplay() {
        Siri extraJourney = createEt("", true);

        processor.process(extraJourney);

        final List<NaturalLanguageStringStructure> extraJourneyDisplays = getFirstDestinationDisplays(
            extraJourney);

        assertFalse(extraJourneyDisplays.isEmpty());
        assertEquals(DUMMY_DESTINATION_DISPLAY, extraJourneyDisplays.get(0).getValue());
    }

    @Test
    public void testNullDestinationDisplay() {
        Siri extraJourney = createEt(null, true);

        processor.process(extraJourney);

        final List<NaturalLanguageStringStructure> extraJourneyDisplays = getFirstDestinationDisplays(
            extraJourney);

        assertFalse(extraJourneyDisplays.isEmpty());
        assertEquals(DUMMY_DESTINATION_DISPLAY, extraJourneyDisplays.get(0).getValue());

    }

    @Test
    public void testNullDestinationDisplayNonExtraJourney() {
        Siri nonExtraJourney = createEt(null, false);

        processor.process(nonExtraJourney);

        final List<NaturalLanguageStringStructure> nonExtraDisplays = getFirstDestinationDisplays(
            nonExtraJourney);

        assertTrue(
            nonExtraDisplays.isEmpty(),
            "Dummy DestinationDisplay should NOT be set on non-extraJourneys"
        );
    }

    private List<NaturalLanguageStringStructure> getFirstDestinationDisplays(Siri nonExtraJourney) {
        return nonExtraJourney
            .getServiceDelivery()
            .getEstimatedTimetableDeliveries()
            .get(0)
            .getEstimatedJourneyVersionFrames()
            .get(0)
            .getEstimatedVehicleJourneies()
            .get(0)
            .getEstimatedCalls()
            .getEstimatedCalls()
            .get(0)
            .getDestinationDisplaies();
    }

    private Siri createEt(String destinationDisplay, boolean extraJourney) {
        List<EstimatedVehicleJourney> etList = new ArrayList<>();
        EstimatedVehicleJourney et = new EstimatedVehicleJourney();
        et.setExtraJourney(extraJourney);

        EstimatedVehicleJourney.EstimatedCalls estimatedCalls = new EstimatedVehicleJourney.EstimatedCalls();
        EstimatedCall call = new EstimatedCall();
        if (destinationDisplay != null) {
            NaturalLanguageStringStructure dd = new NaturalLanguageStringStructure();
            dd.setValue(destinationDisplay);
            call.getDestinationDisplaies().add(dd);
        }
        estimatedCalls.getEstimatedCalls().add(call);
        et.setEstimatedCalls(estimatedCalls);
        etList.add(et);
        return new SiriObjectFactory(Instant.now()).createETServiceDelivery(etList);
    }

}
