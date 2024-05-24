package no.rutebanken.anshar.routes.pubsub;

import org.apache.avro.io.DatumWriter;
import org.apache.avro.io.Encoder;
import org.apache.avro.io.EncoderFactory;
import org.apache.avro.specific.SpecificDatumWriter;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.entur.avro.realtime.siri.model.EstimatedVehicleJourneyRecord;
import org.entur.avro.realtime.siri.model.PtSituationElementRecord;
import org.entur.avro.realtime.siri.model.VehicleActivityRecord;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

@Component
public class SiriAvroJsonSerializer implements Processor {
    private final DatumWriter<EstimatedVehicleJourneyRecord> etWriter = new SpecificDatumWriter<>(EstimatedVehicleJourneyRecord.class);
    private final DatumWriter<VehicleActivityRecord> vmWriter = new SpecificDatumWriter<>(VehicleActivityRecord.class);
    private final DatumWriter<PtSituationElementRecord> sxWriter = new SpecificDatumWriter<>(PtSituationElementRecord.class);

    @Override
    public void process(Exchange exchange) throws Exception {
        Object body = exchange.getMessage().getBody();
        if (body instanceof EstimatedVehicleJourneyRecord) {
            serializeEt(exchange);
        } else if (body instanceof VehicleActivityRecord) {
            serializeVm(exchange);
        } else if (body instanceof PtSituationElementRecord) {
            serializeSx(exchange);
        }
    }

    private void serializeEt(Exchange exchange) throws IOException {
        EstimatedVehicleJourneyRecord body = exchange.getMessage().getBody(EstimatedVehicleJourneyRecord.class);
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        Encoder jsonEncoder = EncoderFactory.get().jsonEncoder(
                EstimatedVehicleJourneyRecord.SCHEMA$, stream);
        etWriter.write(body, jsonEncoder);
        jsonEncoder.flush();

        exchange.getMessage().setBody(replaceAvroEnums(stream.toByteArray()));
    }


    /*
       Temporary legacy-hack to rewrite avro-data containing Enums to new schema-version where
       enums are replaced with string for future compatibility.
     */
    private static String replaceAvroEnums(byte[] byteArray) {
        String json = new String(byteArray);
        return json.replaceAll("\"org.entur.avro.realtime.siri.model.[a-zA-Z]*Enum\"", "\"string\"");
    }

    private void serializeVm(Exchange exchange) throws IOException {
        VehicleActivityRecord body = exchange.getMessage().getBody(VehicleActivityRecord.class);
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        Encoder jsonEncoder = EncoderFactory.get().jsonEncoder(
                VehicleActivityRecord.SCHEMA$, stream);
        vmWriter.write(body, jsonEncoder);
        jsonEncoder.flush();
        exchange.getMessage().setBody(replaceAvroEnums(stream.toByteArray()));
    }
    private void serializeSx(Exchange exchange) throws IOException {
        PtSituationElementRecord body = exchange.getMessage().getBody(PtSituationElementRecord.class);
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        Encoder jsonEncoder = EncoderFactory.get().jsonEncoder(
                PtSituationElementRecord.SCHEMA$, stream);
        sxWriter.write(body, jsonEncoder);
        jsonEncoder.flush();
        exchange.getMessage().setBody(replaceAvroEnums(stream.toByteArray()));
    }
}
