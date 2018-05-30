package no.rutebanken.anshar.routes.export.gtfsrt;

import com.google.transit.realtime.GtfsRealtime;
import org.onebusway.gtfs_realtime.exporter.GtfsRealtimeLibrary;
import org.springframework.stereotype.Component;
import uk.org.siri.siri20.*;

import javax.xml.datatype.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.List;

@Component
public class TripUpdateFactory {


    public GtfsRealtime.TripUpdate createTripUpdateFromEstimatedVehicleJourney(EstimatedVehicleJourney journey) {

        GtfsRealtime.TripUpdate.Builder tripUpdate = GtfsRealtime.TripUpdate.newBuilder();
        GtfsRealtime.TripDescriptor td = this.getMonitoredVehicleJourneyAsTripDescriptor(journey);
        tripUpdate.setTrip(td);

        GtfsRealtime.VehicleDescriptor vd = this.getMonitoredVehicleJourneyAsVehicleDescriptor(journey.getVehicleRef());
        if(vd != null) {
            tripUpdate.setVehicle(vd);
        }

        if (journey.getEstimatedCalls() != null && journey.getEstimatedCalls().getEstimatedCalls() != null) {
            List<EstimatedCall> estimatedCalls = journey.getEstimatedCalls().getEstimatedCalls();
            for (EstimatedCall estimatedCall : estimatedCalls) {

                GtfsRealtime.TripUpdate.StopTimeUpdate.Builder stoptimeUpdate = GtfsRealtime.TripUpdate.StopTimeUpdate.newBuilder();
                stoptimeUpdate.setStopId(estimatedCall.getStopPointRef().getValue());

                if (estimatedCall.getExpectedArrivalTime() != null) {
                    stoptimeUpdate.setArrival(getStopTimeEvent(estimatedCall.getAimedArrivalTime(), estimatedCall.getExpectedArrivalTime()));
                }

                if (estimatedCall.getExpectedDepartureTime() != null) {
                    stoptimeUpdate.setDeparture(getStopTimeEvent(estimatedCall.getAimedDepartureTime(), estimatedCall.getExpectedDepartureTime()));
                }

                tripUpdate.addStopTimeUpdate(stoptimeUpdate);
            }
        }


        return tripUpdate.build();
    }

    private GtfsRealtime.TripUpdate.StopTimeEvent getStopTimeEvent(ZonedDateTime aimedTime, ZonedDateTime expectedTime) {
        GtfsRealtime.TripUpdate.StopTimeEvent.Builder steBuilder = GtfsRealtime.TripUpdate.StopTimeEvent.newBuilder();
        steBuilder.setTime(expectedTime.toEpochSecond());
        steBuilder.setDelay((int) (expectedTime.toEpochSecond()-aimedTime.toEpochSecond()));
        steBuilder.setUncertainty(0);
        return steBuilder.build();
    }

    public GtfsRealtime.TripUpdate createTripUpdateFromVehicleMonitoring(VehicleActivityStructure activity) {

        GtfsRealtime.FeedMessage.Builder feedMessageBuilder = GtfsRealtimeLibrary.createFeedMessageBuilder();
        long feedTimestampMilli = feedMessageBuilder.getHeader().getTimestamp();
        Date durationOffset = new Date(feedTimestampMilli);
        VehicleActivityStructure.MonitoredVehicleJourney mvj = activity.getMonitoredVehicleJourney();

        GtfsRealtime.TripUpdate.Builder tripUpdate = GtfsRealtime.TripUpdate.newBuilder();
        GtfsRealtime.TripDescriptor td = this.getMonitoredVehicleJourneyAsTripDescriptor(mvj);
        tripUpdate.setTrip(td);
        GtfsRealtime.VehicleDescriptor vd = this.getMonitoredVehicleJourneyAsVehicleDescriptor(mvj);
        if(vd != null) {
            tripUpdate.setVehicle(vd);
        }

        ZonedDateTime time = activity.getRecordedAtTime();
        if(time == null) {
            time = ZonedDateTime.ofInstant(Instant.ofEpochMilli(feedTimestampMilli), ZonedDateTime.now().getZone());
        }

        Duration delayDuration = mvj.getDelay();
        int delayInSeconds = 0;
        if(delayDuration != null) {
            delayInSeconds = (int) (delayDuration.getTimeInMillis(durationOffset) / 1000L);
        }

        this.applyStopSpecificDelayToTripUpdateIfApplicable(mvj, delayInSeconds, tripUpdate);


        GtfsRealtime.FeedEntity.Builder entity = GtfsRealtime.FeedEntity.newBuilder();

        entity.setId(this.getTripIdForMonitoredVehicleJourney(mvj));

        return entity.getTripUpdate();
    }


    private GtfsRealtime.TripDescriptor getMonitoredVehicleJourneyAsTripDescriptor(VehicleActivityStructure.MonitoredVehicleJourney mvj) {
        com.google.transit.realtime.GtfsRealtime.TripDescriptor.Builder td = GtfsRealtime.TripDescriptor.newBuilder();
        FramedVehicleJourneyRefStructure fvjRef = mvj.getFramedVehicleJourneyRef();
        td.setTripId(fvjRef.getDatedVehicleJourneyRef());
        return td.build();
    }


    private GtfsRealtime.TripDescriptor getMonitoredVehicleJourneyAsTripDescriptor(EstimatedVehicleJourney journey) {
        com.google.transit.realtime.GtfsRealtime.TripDescriptor.Builder td = GtfsRealtime.TripDescriptor.newBuilder();
        String tripId = getTripId(journey);
        if (tripId != null) {
            td.setTripId(tripId);
        }
        return td.build();
    }

    private String getTripId(EstimatedVehicleJourney journey) {

        if (journey.getFramedVehicleJourneyRef() != null) {
            return journey.getFramedVehicleJourneyRef().getDatedVehicleJourneyRef();
        } else {
            return journey.getDatedVehicleJourneyRef().getValue();
        }

    }


    private GtfsRealtime.VehicleDescriptor getMonitoredVehicleJourneyAsVehicleDescriptor(VehicleActivityStructure.MonitoredVehicleJourney mvj) {
        return getMonitoredVehicleJourneyAsVehicleDescriptor(mvj.getVehicleRef());
    }


    private GtfsRealtime.VehicleDescriptor getMonitoredVehicleJourneyAsVehicleDescriptor(VehicleRef vehicleRef) {
        if(vehicleRef != null && vehicleRef.getValue() != null) {
            com.google.transit.realtime.GtfsRealtime.VehicleDescriptor.Builder vd = GtfsRealtime.VehicleDescriptor.newBuilder();
            vd.setId(vehicleRef.getValue());
            return vd.build();
        } else {
            return null;
        }
    }

    private void applyStopSpecificDelayToTripUpdateIfApplicable(VehicleActivityStructure.MonitoredVehicleJourney mvj, int delayInSeconds, com.google.transit.realtime.GtfsRealtime.TripUpdate.Builder tripUpdate) {
        MonitoredCallStructure mc = mvj.getMonitoredCall();
        if(mc != null) {
            StopPointRef stopPointRef = mc.getStopPointRef();
            if(stopPointRef != null && stopPointRef.getValue() != null) {
                GtfsRealtime.TripUpdate.StopTimeEvent.Builder stopTimeEvent = GtfsRealtime.TripUpdate.StopTimeEvent.newBuilder();
                stopTimeEvent.setDelay(delayInSeconds);
                GtfsRealtime.TripUpdate.StopTimeUpdate.Builder stopTimeUpdate = GtfsRealtime.TripUpdate.StopTimeUpdate.newBuilder();
                stopTimeUpdate.setDeparture(stopTimeEvent);
                stopTimeUpdate.setStopId(stopPointRef.getValue());
                tripUpdate.addStopTimeUpdate(stopTimeUpdate);
            }
        }
    }

    private String getTripIdForMonitoredVehicleJourney(VehicleActivityStructure.MonitoredVehicleJourney mvj) {
        StringBuilder b = new StringBuilder();
        FramedVehicleJourneyRefStructure fvjRef = mvj.getFramedVehicleJourneyRef();
        b.append(fvjRef.getDatedVehicleJourneyRef());
        b.append('-');
        b.append(fvjRef.getDataFrameRef().getValue());
        if(mvj.getVehicleRef() != null && mvj.getVehicleRef().getValue() != null) {
            b.append('-');
            b.append((mvj.getVehicleRef().getValue()));
        }

        return b.toString();
    }
}
