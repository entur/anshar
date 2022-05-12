package no.rutebanken.anshar.routes.siri.processor.routedata;

import uk.org.siri.siri20.EstimatedVehicleJourney;

import java.time.ZonedDateTime;

import static no.rutebanken.anshar.routes.siri.processor.routedata.ExceptionUtils.resolveServiceJourneyId;

public class TooFastException extends Throwable {

    private final String msg;

    public TooFastException(EstimatedVehicleJourney serviceJourneyId, String fromStop, String toStop, ZonedDateTime fromTime, ZonedDateTime toTime) {

        double distance = StopsUtil.getDistance(fromStop, toStop);

        long seconds = StopsUtil.getSeconds(fromTime, toTime);
        int kph = StopsUtil.calculateSpeedKph(distance, fromTime, toTime);

        this.msg = "Too fast (" + kph + " kph) between " + fromStop + " and " + toStop +" (" + Math.round(distance) + " meters in " + seconds + "s) [" + resolveServiceJourneyId(serviceJourneyId) + "].";
    }


    @Override
        public String getMessage() {
            return msg;
        }
    }