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
package no.rutebanken.anshar.routes.pubsub;

import org.apache.avro.Schema;
import org.apache.avro.io.DatumWriter;
import org.apache.avro.io.Encoder;
import org.apache.avro.io.EncoderFactory;
import org.apache.avro.specific.SpecificDatumWriter;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.entur.avro.realtime.siri.model.EstimatedVehicleJourneyRecord;
import org.entur.avro.realtime.siri.model.PtSituationElementRecord;
import org.entur.avro.realtime.siri.model.VehicleActivityRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

@Component
public class SiriAvroJsonSerializer implements Processor {
    private static final Logger logger = LoggerFactory.getLogger(SiriAvroJsonSerializer.class);
    // Shared, as the Camel routes shared one serializer instance before
    private static final DatumWriter<EstimatedVehicleJourneyRecord> etWriter = new SpecificDatumWriter<>(EstimatedVehicleJourneyRecord.class);
    private static final DatumWriter<VehicleActivityRecord> vmWriter = new SpecificDatumWriter<>(VehicleActivityRecord.class);
    private static final DatumWriter<PtSituationElementRecord> sxWriter = new SpecificDatumWriter<>(PtSituationElementRecord.class);

    @Override
    public void process(Exchange exchange) throws Exception {
        Object body = exchange.getMessage().getBody();
        if (body instanceof EstimatedVehicleJourneyRecord journey) {
            exchange.getMessage().setBody(toJson(journey));
        } else if (body instanceof VehicleActivityRecord activity) {
            exchange.getMessage().setBody(toJson(activity));
        } else if (body instanceof PtSituationElementRecord situation) {
            exchange.getMessage().setBody(toJson(situation));
        }
    }

    /** The JSON published to the Avro-JSON topic for a journey */
    public static String toJson(EstimatedVehicleJourneyRecord journey) throws IOException {
        return toJson(journey, EstimatedVehicleJourneyRecord.SCHEMA$, etWriter);
    }

    /** The JSON published to the Avro-JSON topic for a vehicle activity */
    public static String toJson(VehicleActivityRecord activity) throws IOException {
        return toJson(activity, VehicleActivityRecord.SCHEMA$, vmWriter);
    }

    /** The JSON published to the Avro-JSON topic for a situation */
    public static String toJson(PtSituationElementRecord situation) throws IOException {
        return toJson(situation, PtSituationElementRecord.SCHEMA$, sxWriter);
    }

    private static <T> String toJson(T avroRecord, Schema schema, DatumWriter<T> writer) throws IOException {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        Encoder jsonEncoder = EncoderFactory.get().jsonEncoder(schema, stream);
        writer.write(avroRecord, jsonEncoder);
        jsonEncoder.flush();
        return replaceAvroEnums(stream.toByteArray());
    }

    /*
       Temporary legacy-hack to rewrite avro-data containing Enums to new schema-version where
       enums are replaced with string for future compatibility.
     */
    private static String replaceAvroEnums(byte[] byteArray) {
        String json = new String(byteArray);
        if (json.contains("\"org.entur.avro.realtime.siri.model.[a-zA-Z]*Enum\"")) {
            logger.warn("Avro data still contains legacy enums!!!");
            return json.replaceAll("\"org.entur.avro.realtime.siri.model.[a-zA-Z]*Enum\"", "\"string\"");
        }
        return json;
    }
}
