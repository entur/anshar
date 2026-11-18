package no.rutebanken.anshar.routes.pubsub.publisher;

import com.google.pubsub.v1.PubsubMessage;
import org.apache.avro.specific.SpecificRecord;

import java.util.Map;

/**
 * What is published for one ET journey or SX situation. The XML message and the Avro messages are built
 * independently, so a failure in one still leaves the other to be published.
 *
 * @param elementId the journey's DatedVehicleJourneyRef or the situation's SituationNumber
 * @param xml       for the XML topic, or null when it could not be built
 * @param xmlError  why the XML message could not be built, or null
 * @param avro      for the Avro-JSON topic and Kafka, or null when they could not be built
 * @param avroError why the Avro messages could not be built, or null
 */
public record ElementMessages(String elementId, PubsubMessage xml, Exception xmlError, Avro avro, Exception avroError) {

    /**
     * @param json         for the Avro-JSON topic
     * @param avroRecord   for Kafka
     * @param kafkaHeaders the headers the Kafka route expects with the record
     */
    public record Avro(PubsubMessage json, SpecificRecord avroRecord, Map<String, Object> kafkaHeaders) {
    }
}
