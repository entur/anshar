package no.rutebanken.anshar.routes.kafka;

import io.confluent.kafka.schemaregistry.client.MockSchemaRegistryClient;
import io.confluent.kafka.serializers.KafkaAvroSerializer;
import no.rutebanken.anshar.routes.avro.AvroClassSecurity;
import org.apache.avro.util.ClassSecurityValidator;
import org.entur.avro.realtime.siri.converter.Converter;
import org.entur.avro.realtime.siri.model.EstimatedVehicleJourneyRecord;
import org.entur.avro.realtime.siri.model.PtSituationElementRecord;
import org.entur.avro.realtime.siri.model.SiriRecord;
import org.entur.avro.realtime.siri.model.VehicleActivityRecord;
import org.entur.siri21.util.SiriXml;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Avro 1.12.2+ refuses to load specific-record classes that are not explicitly trusted,
 * which makes KafkaAvroSerializer fail for every SIRI Avro record.
 */
public class KafkaAvroSerializationTest {

    @BeforeAll
    public static void trustSiriAvroModel() {
        AvroClassSecurity.trustSiriAvroModel();
    }

    @Test
    public void testEstimatedVehicleJourneyIsSerializable() throws Exception {
        String xml = Files.readString(Path.of("src/test/resources/siri-et-from-bnr.xml"));
        SiriRecord siriRecord = Converter.jaxb2Avro(SiriXml.parseXml(xml));
        EstimatedVehicleJourneyRecord record = siriRecord.getServiceDelivery()
                .getEstimatedTimetableDeliveries().get(0)
                .getEstimatedJourneyVersionFrames().get(0)
                .getEstimatedVehicleJourneys().get(0);

        try (KafkaAvroSerializer serializer = new KafkaAvroSerializer(new MockSchemaRegistryClient())) {
            serializer.configure(Map.of("schema.registry.url", "mock://anshar", "auto.register.schemas", true), false);

            byte[] serialized = serializer.serialize("siri-et", record);

            assertTrue(serialized.length > 0);
        }
    }

    @Test
    public void testVmAndSxRecordsAreTrusted() {
        assertTrue(ClassSecurityValidator.getGlobal().isTrusted(VehicleActivityRecord.class));
        assertTrue(ClassSecurityValidator.getGlobal().isTrusted(PtSituationElementRecord.class));
    }
}
