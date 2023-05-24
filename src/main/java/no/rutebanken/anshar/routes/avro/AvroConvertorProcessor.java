package no.rutebanken.anshar.routes.avro;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.component.kafka.KafkaConstants;
import org.entur.avro.realtime.siri.converter.Converter;
import org.entur.avro.realtime.siri.model.EstimatedJourneyVersionFrameRecord;
import org.entur.avro.realtime.siri.model.EstimatedTimetableDeliveryRecord;
import org.entur.avro.realtime.siri.model.EstimatedVehicleJourneyRecord;
import org.entur.avro.realtime.siri.model.MonitoredVehicleJourneyRecord;
import org.entur.avro.realtime.siri.model.PtSituationElementRecord;
import org.entur.avro.realtime.siri.model.ServiceDeliveryRecord;
import org.entur.avro.realtime.siri.model.SiriRecord;
import org.entur.avro.realtime.siri.model.SituationExchangeDeliveryRecord;
import org.entur.avro.realtime.siri.model.VehicleActivityRecord;
import org.entur.avro.realtime.siri.model.VehicleMonitoringDeliveryRecord;
import org.entur.siri21.util.SiriXml;
import org.springframework.stereotype.Component;

import java.util.Map;

import static no.rutebanken.anshar.routes.kafka.KafkaConfig.CODESPACE_ID_KAFKA_HEADER_NAME;

@Component
public class AvroConvertorProcessor implements Processor {

    private static final String HEADER_LINE_REF = "lineRef";

    @Override
    public void process(Exchange exchange) throws Exception {

        String body = exchange.getIn().getBody(String.class);

        String codespaceId = exchange.getIn().getHeader(CODESPACE_ID_KAFKA_HEADER_NAME, String.class);

        SiriRecord siriRecord = Converter.jaxb2Avro(SiriXml.parseXml(body));

        Message out = exchange.getMessage();
        if (siriRecord.getServiceDelivery() != null) {
            ServiceDeliveryRecord serviceDelivery = siriRecord.getServiceDelivery();
            if (!serviceDelivery.getSituationExchangeDeliveries().isEmpty()) {
                for (SituationExchangeDeliveryRecord delivery : serviceDelivery.getSituationExchangeDeliveries()) {
                    for (PtSituationElementRecord record : delivery.getSituations()) {
                        out.setBody(record);
                        out.setHeaders(createHeaders(record, codespaceId));
                        return;
                    }
                }
            } else if (!serviceDelivery.getVehicleMonitoringDeliveries().isEmpty()) {
                for (VehicleMonitoringDeliveryRecord delivery : serviceDelivery.getVehicleMonitoringDeliveries()) {
                    for (VehicleActivityRecord record : delivery.getVehicleActivities()) {
                        out.setBody(record);
                        out.setHeaders(createHeaders(record, codespaceId));
                        return;
                    }
                }
            } else if (!serviceDelivery.getEstimatedTimetableDeliveries().isEmpty()) {
                for (EstimatedTimetableDeliveryRecord delivery : serviceDelivery.getEstimatedTimetableDeliveries()) {
                    for (EstimatedJourneyVersionFrameRecord frame : delivery.getEstimatedJourneyVersionFrames()) {
                        for (EstimatedVehicleJourneyRecord record : frame.getEstimatedVehicleJourneys()) {
                            out.setBody(record);
                            out.setHeaders(createHeaders(record, codespaceId));
                            return;
                        }
                    }
                }
            }

        }
    }

    private Map<String, Object> createHeaders(EstimatedVehicleJourneyRecord record, String codespaceId) {
        return Map.of(
                KafkaConstants.KEY, resolveTripId(record),
                CODESPACE_ID_KAFKA_HEADER_NAME, codespaceId,
                HEADER_LINE_REF, record.getLineRef()
        );
    }
    private Map<String, Object> createHeaders(VehicleActivityRecord record, String codespaceId) {
        CharSequence lineRef = "";
        if (record.getMonitoredVehicleJourney() != null && record.getMonitoredVehicleJourney().getLineRef() != null) {
            lineRef = record.getMonitoredVehicleJourney().getLineRef();
        }
        return Map.of(
                KafkaConstants.KEY, resolveTripId(record),
                CODESPACE_ID_KAFKA_HEADER_NAME, codespaceId,
                HEADER_LINE_REF, lineRef
        );
    }

    private Map<String, Object> createHeaders(PtSituationElementRecord record, String codespaceId){
        return Map.of(
                KafkaConstants.KEY, record.getSituationNumber(),
                CODESPACE_ID_KAFKA_HEADER_NAME, codespaceId
        );
    }


    private String resolveTripId(EstimatedVehicleJourneyRecord record) {
        String tripId = "";
        if (record.getFramedVehicleJourneyRef() != null) { // ServiceJourneyId + date
            String dataFrameRef = (String) record.getFramedVehicleJourneyRef().getDataFrameRef();
            String serviceJourneyId = (String) record.getFramedVehicleJourneyRef().getDatedVehicleJourneyRef();
            tripId = serviceJourneyId + ":" + dataFrameRef;
        } else if (record.getDatedVehicleJourneyRef() != null) { // DatedServiceJourney
            tripId = (String) record.getDatedVehicleJourneyRef();
        } else if (record.getEstimatedVehicleJourneyCode() != null) { // ExtraJourney
            tripId = (String) record.getEstimatedVehicleJourneyCode();
        }
        return tripId;
    }

    private String resolveTripId(VehicleActivityRecord record) {
        String tripId = "";
        MonitoredVehicleJourneyRecord monitoredVehicleJourney = record.getMonitoredVehicleJourney();
        if (monitoredVehicleJourney != null) {
            if (monitoredVehicleJourney.getFramedVehicleJourneyRef() != null) {
                String dataFrameRef = (String) monitoredVehicleJourney.getFramedVehicleJourneyRef().getDataFrameRef();
                String serviceJourneyId = (String) monitoredVehicleJourney.getFramedVehicleJourneyRef().getDatedVehicleJourneyRef();
                tripId = serviceJourneyId + ":" + dataFrameRef;
            } else if (monitoredVehicleJourney.getVehicleJourneyRef() != null) {
                tripId = (String) monitoredVehicleJourney.getVehicleJourneyRef();
            }
        }

        return tripId;
    }
}