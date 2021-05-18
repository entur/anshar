package no.rutebanken.anshar.siri.processor;

import no.rutebanken.anshar.routes.siri.helpers.SiriObjectFactory;
import no.rutebanken.anshar.routes.siri.processor.PredictionInaccurateDisablerPostProcessor;
import org.junit.jupiter.api.Test;
import uk.org.siri.siri20.EstimatedVehicleJourney;
import uk.org.siri.siri20.Siri;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class PredictionInaccurateDisablerPostProcessorTest {

    PredictionInaccurateDisablerPostProcessor processor = new PredictionInaccurateDisablerPostProcessor();

    @Test
    public void testOverrideWithTrueValue() {

        Siri siri = createSimpleEt(Boolean.TRUE);

        //Assert correctly set value
        assertEquals(Boolean.TRUE, siri.getServiceDelivery().getEstimatedTimetableDeliveries().get(0).getEstimatedJourneyVersionFrames().get(0).getEstimatedVehicleJourneies().get(0).isPredictionInaccurate());

        processor.process(siri);

        //Assert that value has been set to false
        assertEquals(Boolean.FALSE, siri.getServiceDelivery().getEstimatedTimetableDeliveries().get(0).getEstimatedJourneyVersionFrames().get(0).getEstimatedVehicleJourneies().get(0).isPredictionInaccurate());
    }

    @Test
    public void testOverrideWithFalseValue() {

        Siri siri = createSimpleEt(Boolean.FALSE);

        //Assert correctly set value
        assertEquals(Boolean.FALSE, siri.getServiceDelivery().getEstimatedTimetableDeliveries().get(0).getEstimatedJourneyVersionFrames().get(0).getEstimatedVehicleJourneies().get(0).isPredictionInaccurate());

        processor.process(siri);

        //Assert that value has been set to false
        assertEquals(Boolean.FALSE, siri.getServiceDelivery().getEstimatedTimetableDeliveries().get(0).getEstimatedJourneyVersionFrames().get(0).getEstimatedVehicleJourneies().get(0).isPredictionInaccurate());
    }

    @Test
    public void testOverrideWithNullValue() {

        Siri siri = createSimpleEt(null);

        //Assert correctly set value
        assertNull(siri.getServiceDelivery().getEstimatedTimetableDeliveries().get(0).getEstimatedJourneyVersionFrames().get(0).getEstimatedVehicleJourneies().get(0).isPredictionInaccurate());

        processor.process(siri);

        //Assert that value has been set to false
        assertNull(siri.getServiceDelivery().getEstimatedTimetableDeliveries().get(0).getEstimatedJourneyVersionFrames().get(0).getEstimatedVehicleJourneies().get(0).isPredictionInaccurate());
    }

    private Siri createSimpleEt(Boolean predictionInaccurate) {
        List<EstimatedVehicleJourney> etList = new ArrayList<>();
        EstimatedVehicleJourney et = new EstimatedVehicleJourney();
        et.setPredictionInaccurate(predictionInaccurate);
        etList.add(et);
        return new SiriObjectFactory(Instant.now()).createETServiceDelivery(etList);
    }
}
