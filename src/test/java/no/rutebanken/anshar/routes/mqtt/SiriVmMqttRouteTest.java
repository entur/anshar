package no.rutebanken.anshar.routes.mqtt;

import javafx.util.Pair;
import org.junit.Test;
import uk.org.siri.siri20.*;

import java.math.BigDecimal;
import javax.xml.datatype.DatatypeConstants;
import javax.xml.datatype.Duration;
import java.math.BigInteger;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Calendar;

import static org.junit.Assert.*;

public class SiriVmMqttRouteTest {

    //
    //

    @Test
    public void testTopicFormat() {

        ZonedDateTime dateTime = ZonedDateTime.of(2017, 12, 24, 10, 23, 4, 0, ZoneId.of("GMT"));

        VehicleActivityStructure vehicle = createVehicle(dateTime, "veh123", "RUT:Line:7890", "Nettbus",
                1, "NSR:Quay:6201", 59.10234567, 10.98765421, 123, "Helsfyr T",
                "hest", "Nydalen T", 18);

        String datasetId = "RUT";
        Pair<String, String> message = new SiriVmMqttRoute().getMessage(datasetId, vehicle);
        String topic = message.getKey();

        assertEquals("/hfp/journey/bus/RUTveh123/RUT:Line:7890/1/Helsfyr T/1023/NSR:Quay:6201/59;10/91/08/27/", topic);
    }

    private VehicleActivityStructure createVehicle(ZonedDateTime dateTime, String vehicleRef, String line,
                                                   String operator, int direction,
                                                   String nextStop, // -> onwardscalls[0]
                                                   double lat, double lng, int delay,
                                                   String destinationName, // -> destinationNames[0]
                                                   String publishedName, // -> publishedLineNames[0]
                                                   String origin, // -> orginNames[0]
                                                   int stopIndex) { // -> monitoredCalls[0]
        VehicleActivityStructure element = new VehicleActivityStructure();
        element.setRecordedAtTime(dateTime);
        element.setValidUntilTime(dateTime.plusMinutes(10));

        VehicleActivityStructure.MonitoredVehicleJourney vehicleJourney = new VehicleActivityStructure.MonitoredVehicleJourney();
        VehicleRef vRef = new VehicleRef();
        vRef.setValue(vehicleRef);
        vehicleJourney.setVehicleRef(vRef);

        LineRef lineRef = new LineRef();
        lineRef.setValue(line);
        vehicleJourney.setLineRef(lineRef);

        OperatorRefStructure operatorRef = new OperationalUnitRefStructure();
        operatorRef.setValue(operator);
        vehicleJourney.setOperatorRef(operatorRef);

        DirectionRefStructure directionRef = new DirectionRefStructure();
        directionRef.setValue("" + direction);
        vehicleJourney.setDirectionRef(directionRef);

        vehicleJourney.setOriginAimedDepartureTime(dateTime.minusMinutes(23));

        StopPointRef stopPointRef = new StopPointRef();
        stopPointRef.setValue(nextStop);
        OnwardCallStructure onwardCall = new OnwardCallStructure();
        onwardCall.setStopPointRef(stopPointRef);
        OnwardCallsStructure onwardCalls = new OnwardCallsStructure();
        onwardCalls.getOnwardCalls().add(onwardCall);
        vehicleJourney.setOnwardCalls(onwardCalls);

        LocationStructure location = new LocationStructure();
        location.setLatitude(BigDecimal.valueOf(lat));
        location.setLongitude(BigDecimal.valueOf(lng));
        vehicleJourney.setVehicleLocation(location);

        vehicleJourney.setDelay(new Duration() {
            @Override
            public int getSeconds() {
                return delay;
            }

            public int getSign() { return 0; }
            public Number getField(DatatypeConstants.Field field) { return null; }
            public boolean isSet(DatatypeConstants.Field field) { return false; }
            public Duration add(Duration rhs) { return null; }
            public void addTo(Calendar calendar) { }
            public Duration multiply(BigDecimal factor) { return null; }
            public Duration negate() { return null; }
            public Duration normalizeWith(Calendar startTimeInstant) { return null; }
            public int compare(Duration duration) { return 0; }
            public int hashCode() { return 0; }
        });

        NaturalLanguageStringStructure destinationNames = new NaturalLanguageStringStructure();
        destinationNames.setValue(destinationName);
        vehicleJourney.getDestinationNames().add(destinationNames);

        NaturalLanguageStringStructure publishedLineNames = new NaturalLanguageStringStructure();
        publishedLineNames.setValue(publishedName);
        vehicleJourney.getPublishedLineNames().add(publishedLineNames);

        NaturalLanguagePlaceNameStructure originNames = new NaturalLanguagePlaceNameStructure();
        originNames.setValue(origin);
        vehicleJourney.getOriginNames().add(originNames);

        MonitoredCallStructure monitoredCall = new MonitoredCallStructure();
        monitoredCall.setVisitNumber(BigInteger.valueOf(stopIndex));
        vehicleJourney.setMonitoredCall(monitoredCall);

        /*
        CourseOfJourneyRefStructure journeyRefStructure = new CourseOfJourneyRefStructure();
        journeyRefStructure.setValue("yadayada");
        vehicleJourney.setCourseOfJourneyRef(journeyRefStructure);
        */

        element.setMonitoredVehicleJourney(vehicleJourney);
        return element;
    }
}
