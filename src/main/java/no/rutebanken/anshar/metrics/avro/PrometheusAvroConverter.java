package no.rutebanken.anshar.metrics.avro;

import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

@Component
public class PrometheusAvroConverter {


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
        MetricRecord metricRecord = new MetricRecord();
        metricRecord.setRecordedAtTime(recordedAtTime.toString());
        metricRecord.setHostname(hostname);
        String name = line.substring(0, line.indexOf("{"));
        String labelString = line.substring(line.indexOf("{")+1, line.indexOf("}"));

        List<LabelRecord> labels = new ArrayList<>();
        String[] keyValues = labelString.split(",");
        for (String keyValue : keyValues) {
            String[] split = keyValue.split("=");
            LabelRecord labelRecord = new LabelRecord();
            labelRecord.setName(cleanup(split, 0));
            labelRecord.setValue(cleanup(split, 1));
            labels.add(labelRecord);
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
