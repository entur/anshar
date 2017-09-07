package no.rutebanken.anshar.siri.processor;

import no.rutebanken.anshar.routes.siri.SiriObjectFactory;
import no.rutebanken.anshar.routes.siri.processor.RuterDatedVehicleRefPostProcessor;
import org.junit.Test;
import uk.org.siri.siri20.*;

import java.math.BigInteger;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.List;

import static junit.framework.TestCase.assertEquals;

public class TestRuterDatedVehicleRefPostProcessor {

    @Test
    public void testConvertDatedVehicleRef() {
        String originalDatedVehicleRef = "250:125:9-12510";
        String targetVehicleRef = "RUT:ServiceJourney:250-125";

        Siri siri = createEtServiceDelivery(originalDatedVehicleRef);


        new RuterDatedVehicleRefPostProcessor().process(siri);

        assertEquals(targetVehicleRef, siri.getServiceDelivery().getEstimatedTimetableDeliveries().get(0)
                .getEstimatedJourneyVersionFrames().get(0).getEstimatedVehicleJourneies().get(0)
                .getFramedVehicleJourneyRef().getDatedVehicleJourneyRef());

    }

    private Siri createEtServiceDelivery(String datedVehicleRef) {
        return new SiriObjectFactory(Instant.now()).createETServiceDelivery(createEstimatedVehicleJourney(datedVehicleRef));
    }

    private List<EstimatedVehicleJourney> createEstimatedVehicleJourney(String datedVehicleRef) {
        EstimatedVehicleJourney element = new EstimatedVehicleJourney();
        LineRef lineRef = new LineRef();
        lineRef.setValue("");
        element.setLineRef(lineRef);
        VehicleRef vehicleRef = new VehicleRef();
        vehicleRef.setValue("");
        element.setVehicleRef(vehicleRef);

        FramedVehicleJourneyRefStructure framedVehicleJourneyRefStructure = new FramedVehicleJourneyRefStructure();
        framedVehicleJourneyRefStructure.setDatedVehicleJourneyRef(datedVehicleRef);
        element.setFramedVehicleJourneyRef(framedVehicleJourneyRefStructure);

        EstimatedVehicleJourney.EstimatedCalls estimatedCalls = new EstimatedVehicleJourney.EstimatedCalls();
        for (int i = 0; i < 3; i++) {

            StopPointRef stopPointRef = new StopPointRef();
            stopPointRef.setValue("NSR:TEST:"+i);
            EstimatedCall call = new EstimatedCall();
            call.setStopPointRef(stopPointRef);
            call.setOrder(BigInteger.valueOf(i));
            estimatedCalls.getEstimatedCalls().add(call);
        }

        element.setEstimatedCalls(estimatedCalls);
        element.setRecordedAtTime(ZonedDateTime.now());

        return Arrays.asList(element);
    }
}
