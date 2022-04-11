package no.rutebanken.anshar.routes.siri.processor.routedata;

import java.time.ZonedDateTime;

public class TooFastException extends Throwable {

    private final String msg;

    public TooFastException(String fromStop, String toStop, ZonedDateTime fromTime, ZonedDateTime toTime) {

        double distance = StopsUtil.getDistance(fromStop, toStop);
        distance = Math.round(distance*100)/100; //distance rounded to 2 decimals

        long seconds = StopsUtil.getSeconds(fromTime, toTime);
        int kph = StopsUtil.calculateSpeedKph(distance, fromTime, toTime);

        this.msg = "Too fast (" + kph + " kph) between " + fromStop + " and " + toStop +" (" + distance + "meters in " + seconds + " s).";
    }

    @Override
        public String getMessage() {
            return msg;
        }
    }