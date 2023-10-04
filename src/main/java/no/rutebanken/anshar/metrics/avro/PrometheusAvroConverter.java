package no.rutebanken.anshar.metrics.avro;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Component
public class PrometheusAvroConverter {

    private static final Logger logger = LoggerFactory.getLogger(PrometheusAvroConverter.class);

    private static final Set<String> labelsToIgnore = Set.of("mappingName", "siriContentLabel");
    private static final Set<String> metricsToIgnore = Set.of(
            "app_anshar_data_outbound_total",
            "app_anshar_data_kafka_total",
            "app_anshar_data_counter",
            "app_anshar_siri_content_total",
            "app_anshar_data_validation_total",
            "app_anshar_data_validation_result_total"
    );

    public static String convertMetrics(String metrics, String hostname) {
        ZonedDateTime recordedAtTime = ZonedDateTime.now().withZoneSameInstant(ZoneId.of("UTC"));
            if (metrics != null) {
                String[] lines = metrics.split("\n");
                for (String line : lines) {
                    return createMetricRecord(recordedAtTime, line, hostname);
                }
            }
            return null;
        }

    /**
     * [name] {[label.name, label.value]...} [metricValue]
     *
     * @param recordedAtTime
     * @param line
     * @param hostname
     * @return
     */
    private static String createMetricRecord(ZonedDateTime recordedAtTime, String line, String hostname) {
        String name = line.substring(0, line.indexOf("{"));
        if (metricsToIgnore.contains(name)) {
            return null;
        }

        MetricRecord metricRecord = new MetricRecord();
        metricRecord.setRecordedAtTime(recordedAtTime.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        metricRecord.setHostname(hostname);
        String labelString = line.substring(line.indexOf("{")+1, line.indexOf("}"));

        List<LabelRecord> labels = new ArrayList<>();
        String[] keyValues = labelString.split(",");
        for (String keyValue : keyValues) {
            String[] split = keyValue.split("=");
            if (split.length == 2) {
                LabelRecord labelRecord = new LabelRecord();
                labelRecord.setName(cleanup(split, 0));
                labelRecord.setValue(cleanup(split, 1));
                if (!labelsToIgnore.contains(labelRecord.getName())) {
                    //mappingName should be ignored
                    labels.add(labelRecord);
                }
            } else {
                logger.info("Label ignored: {}", keyValue);
            }
        }

        metricRecord.setLabels(labels);

        String valueString = line.substring(line.indexOf("}")+1).trim();
        metricRecord.setName(name);
        metricRecord.setValue(Double.parseDouble(valueString));
        return metricRecord.toString();
    }

    private static String cleanup(String[] split, int x) {
        return split[x].replace("\"", "");
    }
}
