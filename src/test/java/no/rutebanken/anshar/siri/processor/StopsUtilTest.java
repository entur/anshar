package no.rutebanken.anshar.siri.processor;

import no.rutebanken.anshar.routes.siri.processor.routedata.NetexUpdaterService;
import no.rutebanken.anshar.routes.siri.processor.routedata.StopsUtil;
import org.junit.jupiter.api.Test;
import org.rutebanken.netex.model.LocationStructure;
import org.rutebanken.netex.model.VehicleModeEnumeration;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static uk.org.siri.siri21.VehicleModesEnumeration.BUS;
import static uk.org.siri.siri21.VehicleModesEnumeration.COACH;
import static uk.org.siri.siri21.VehicleModesEnumeration.RAIL;
import static uk.org.siri.siri21.VehicleModesEnumeration.TRAM;

public class StopsUtilTest {



    @Test
    public void testSpeedBetweenStops() {

        String fromRef = "NSR:Quay:47719"; // Mento
        String toRef = "NSR:Quay:44786";   // Risavika utenriksterminal

        BigDecimal fromLon = new BigDecimal(5.584158);
        BigDecimal fromLat = new BigDecimal(58.917911);

        BigDecimal toLon = new BigDecimal(5.582071);
        BigDecimal toLat = new BigDecimal(58.921065);

        NetexUpdaterService.locations.put(fromRef,
            new LocationStructure().withLatitude(fromLat).withLongitude(fromLon)
        );
        NetexUpdaterService.locations.put(toRef,
            new LocationStructure().withLatitude(toLat).withLongitude(toLon)
        );

        final double distance = StopsUtil.getDistance(fromRef, toRef);
        assertTrue(((int)distance) == 371); // Verifying approximate distance


        final int speedKph = StopsUtil.calculateSpeedKph(
            fromRef,
            toRef,
            ZonedDateTime.now(),
            ZonedDateTime.now().plusSeconds(7)
        );

        assertEquals(190,  speedKph);

    }

    @Test
    public void testInfiniteSpeed() {
        String fromRef = "NSR:Quay:47719"; // Mento
        String toRef = "NSR:Quay:44786";   // Risavika utenriksterminal

        BigDecimal fromLon = new BigDecimal(5.584158);
        BigDecimal fromLat = new BigDecimal(58.917911);

        BigDecimal toLon = new BigDecimal(5.582071);
        BigDecimal toLat = new BigDecimal(58.921065);

        NetexUpdaterService.locations.put(fromRef,
                new LocationStructure().withLatitude(fromLat).withLongitude(fromLon)
        );
        NetexUpdaterService.locations.put(toRef,
                new LocationStructure().withLatitude(toLat).withLongitude(toLon)
        );

        final double distance = StopsUtil.getDistance(fromRef, toRef);
        assertTrue(((int)distance) == 371); // Verifying approximate distance


        // Verifying cornercase when arrival-/departure-times are equal
        ZonedDateTime now = ZonedDateTime.now();
        final int speedKph = StopsUtil.calculateSpeedKph(fromRef, toRef, now, now );

        assertEquals(Integer.MAX_VALUE,  speedKph);
    }

    @Test
    public void testSimpleSpeedCalculation() {

        final int tenMetersPerSecond = StopsUtil.calculateSpeedKph(100,
            ZonedDateTime.now(),
            ZonedDateTime.now().plusSeconds(10)
        );

        assertEquals(36, tenMetersPerSecond);

        final int hundredMetersPerSecond = StopsUtil.calculateSpeedKph(100,
            ZonedDateTime.now(),
            ZonedDateTime.now().plusSeconds(1)
        );

        assertEquals(360, hundredMetersPerSecond);
    }

    @Test
    public void testModeVerification() {
        final String osloS = "NSR:StopPlace:337";
        NetexUpdaterService.modes.put(osloS, VehicleModeEnumeration.RAIL);

        assertFalse(StopsUtil.doesVehicleModeMatchStopMode(List.of(BUS), osloS));
        assertFalse(StopsUtil.doesVehicleModeMatchStopMode(List.of(COACH), osloS));
        assertFalse(StopsUtil.doesVehicleModeMatchStopMode(List.of(TRAM), osloS));

        assertTrue(StopsUtil.doesVehicleModeMatchStopMode(List.of(RAIL), osloS));
        assertTrue(StopsUtil.doesVehicleModeMatchStopMode(List.of(BUS, RAIL), osloS));
    }
}
