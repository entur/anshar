package no.rutebanken.anshar;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

/**
 * Not really related to anshar.
 *
 * Processes specific json-results from journeyplanner to verify that trips have gotten
 * realtime-data appended.
 *
 */
public class CheckOtpResults {

    public static void main(String[] args) throws Exception{
        ResultLogger resultLogger = new ResultLogger();

        /*
            Download all result-files:
            gsutil -m cp -R gs://ror-anshar-dev/realtime_monitor src/test/tmp
         */

        String folder = "src/test/tmp/realtime_monitor";
        File directory = new File(folder);
        final File[] files = directory.listFiles();
        for (File file : files) {
            processFile(file.getAbsolutePath(), resultLogger);
        }

        System.out.println("Files : " + files.length);
        System.out.println("OK    : " + resultLogger.success.size());
        System.out.println("NOT OK: " + resultLogger.errors.size());
        if (resultLogger.errors.size() > 0) {
            for (Cause cause : resultLogger.errors.keySet()) {
                final List<String> paths = resultLogger.errors.get(cause);
                System.err.println(cause);
                for (String s : paths) {
                    System.err.println("\t" + s);
                }
            }
        }

    }

    private static void processFile(String filePath, ResultLogger resultLogger) throws IOException {

        ObjectMapper om = new ObjectMapper();
        final File file = new File(filePath);
        Root root = om.readValue(file, Root.class);

        for (TripPattern tripPattern : root.data.trip.tripPatterns) {
            for (Leg leg : tripPattern.legs) {
                if (!leg.mode.equals("rail")) {
                    continue;
                }
                boolean hasRealtime = false;
                if (leg.fromEstimatedCall.realtime) {
                    hasRealtime = true;
                }
                for (IntermediateEstimatedCall call : leg.intermediateEstimatedCalls) {
                    if (!call.realtime) {
                        hasRealtime = true;
                    }
                }
                if (!leg.toEstimatedCall.realtime) {
                    hasRealtime = true;
                }

                if (hasRealtime) {
                    resultLogger.addSuccess(file);
                } else {
                    resultLogger.addError(file, Cause.REALTIME_FALSE);
                }
            }
        }
    }
}
enum Cause {
    REALTIME_FALSE
}
class ResultLogger {
    List<String> success = new ArrayList<>();
    Map<Cause, List<String>> errors = new HashMap<>();
    void addSuccess(File f) {success.add(f.getName());}
    void addError(File f, Cause cause) {
        final List<String> errorList = errors.getOrDefault(cause, new ArrayList<>());
        errorList.add(f.getName());
        errors.put(cause, errorList);
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", ResultLogger.class.getSimpleName() + "[", "]")
            .add("success=" + success)
            .add("errors=" + errors)
            .toString();
    }
}

class ServiceJourney{
    public String privateCode;
}

class Line{
    public String id;
}

class Quay{
    public String name;
    public String publicCode;
}

class FromEstimatedCall{
    public Quay quay;
    public Date aimedArrivalTime;
    public Date expectedArrivalTime;
    public Date aimedDepartureTime;
    public Date expectedDepartureTime;
    public boolean realtime;
}

class IntermediateEstimatedCall{
    public Quay quay;
    public Date aimedArrivalTime;
    public Date expectedArrivalTime;
    public Date aimedDepartureTime;
    public Date expectedDepartureTime;
    public boolean realtime;
}

class ToEstimatedCall{
    public Quay quay;
    public Date aimedArrivalTime;
    public Date expectedArrivalTime;
    public Date aimedDepartureTime;
    public Date expectedDepartureTime;
    public boolean realtime;
}

class Leg{
    public Date aimedStartTime;
    public Date expectedStartTime;
    public Date aimedEndTime;
    public Date expectedEndTime;
    public String mode;
    public ServiceJourney serviceJourney;
    public Line line;
    public FromEstimatedCall fromEstimatedCall;
    public List<IntermediateEstimatedCall> intermediateEstimatedCalls;
    public ToEstimatedCall toEstimatedCall;
    public Quay quay;
    public Date aimedArrivalTime;
    public Date expectedArrivalTime;
    public Date aimedDepartureTime;
    public Date expectedDepartureTime;
}

class TripPattern{
    public Date expectedStartTime;
    public int duration;
    public double walkDistance;
    public List<Leg> legs;
}

class Trip{
    public List<TripPattern> tripPatterns;
}

class Data{
    public Trip trip;
}

class Root{
    public Data data;
}


