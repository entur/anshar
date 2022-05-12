package no.rutebanken.anshar.routes.siri.processor.routedata;

import uk.org.siri.siri20.EstimatedVehicleJourney;
import uk.org.siri.siri20.FramedVehicleJourneyRefStructure;

import java.time.ZonedDateTime;

public class TooFastException extends Throwable {

    private final String msg;

    public TooFastException(EstimatedVehicleJourney serviceJourneyId, String fromStop, String toStop, ZonedDateTime fromTime, ZonedDateTime toTime) {

        double distance = StopsUtil.getDistance(fromStop, toStop);

        long seconds = StopsUtil.getSeconds(fromTime, toTime);
        int kph = StopsUtil.calculateSpeedKph(distance, fromTime, toTime);

        this.msg = "Too fast (" + kph + " kph) between " + fromStop + " and " + toStop +" (" + Math.round(distance) + " meters in " + seconds + "s) SJ-id:" + resolveServiceJourneyId(serviceJourneyId) + ".";
    }

    private static String resolveServiceJourneyId(EstimatedVehicleJourney estimatedVehicleJourney) {
        FramedVehicleJourneyRefStructure framedVehicleJourneyRef = estimatedVehicleJourney.getFramedVehicleJourneyRef();
        if (framedVehicleJourneyRef != null) {
            return framedVehicleJourneyRef.getDatedVehicleJourneyRef();
        } else if (estimatedVehicleJourney.getDatedVehicleJourneyRef() != null) {
            return estimatedVehicleJourney.getDatedVehicleJourneyRef().getValue();
        }
        return null;
    }


    @Override
        public String getMessage() {
            return msg;
        }
    }