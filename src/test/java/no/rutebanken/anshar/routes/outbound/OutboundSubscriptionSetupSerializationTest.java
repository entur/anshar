package no.rutebanken.anshar.routes.outbound;

import no.rutebanken.anshar.subscription.SiriDataType;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OutboundSubscriptionSetupSerializationTest {

    private static OutboundSubscriptionSetup roundTrip(OutboundSubscriptionSetup setup) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(setup);
        }
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            return (OutboundSubscriptionSetup) in.readObject();
        }
    }

    @Test
    void serialVersionUidIsPinnedToPreBatchingValue() {
        assertEquals(-6386405378625792258L,
                ObjectStreamClass.lookup(OutboundSubscriptionSetup.class).getSerialVersionUID());
    }

    @Test
    void updateIntervalSurvivesJavaSerialization() throws Exception {
        OutboundSubscriptionSetup setup = new OutboundSubscriptionSetup(SiriDataType.VEHICLE_MONITORING,
                "http://localhost/x", 0, List.of(), "sub-1");
        setup.setUpdateInterval(2000);

        OutboundSubscriptionSetup copy = roundTrip(setup);

        assertEquals(2000, copy.getUpdateInterval());
    }
}
