package no.rutebanken.anshar.routes.pubsub.publisher;

import com.google.pubsub.v1.PubsubMessage;
import org.apache.avro.specific.SpecificRecord;

import java.util.Map;

/**
 * What is published for one ET journey or SX situation.
 *
 * @param elementId    the journey's DatedVehicleJourneyRef or the situation's SituationNumber
 * @param xml          for the XML topic
 * @param avroJson     for the Avro-JSON topic
 * @param avroRecord   for Kafka
 * @param kafkaHeaders the headers the Kafka route expects with the record
 */
public record ElementMessages(String elementId, PubsubMessage xml, PubsubMessage avroJson, SpecificRecord avroRecord,
                              Map<String, Object> kafkaHeaders) {
}
