package no.rutebanken.anshar.routes.siri.processor.routedata;

import java.util.Objects;

public class StopTime {
    private final String stopId;
    private final int stopSequence;
    private final int arrivalTime;
    private final int departureTime;

    public StopTime(String stopId, int stopSequence, int arrivalTime, int departureTime) {
        this.stopId = stopId;
        this.stopSequence = stopSequence;
        this.arrivalTime = arrivalTime;
        this.departureTime = departureTime;
    }

    public String getStopId() {
        return stopId;
    }

    public int getStopSequence() {
        return stopSequence;
    }

    public int getArrivalTime() {
        return arrivalTime;
    }

    public int getDepartureTime() {
        return departureTime;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        StopTime that = (StopTime) o;
        return stopSequence == that.stopSequence &&
                arrivalTime == that.arrivalTime &&
                departureTime == that.departureTime &&
                Objects.equals(stopId, that.stopId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(stopId, stopSequence, arrivalTime, departureTime);
    }

    @Override
    public String toString() {
        return "StopTime{" +
                "stopId='" + stopId + '\'' +
                ", stopSequence=" + stopSequence +
                ", arrivalTime=" + arrivalTime +
                ", departureTime=" + departureTime +
                '}';
    }
}
