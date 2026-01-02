package no.rutebanken.anshar.data;

import org.junit.jupiter.api.Test;
import uk.org.siri.siri21.OccupancyEnumeration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SiriRepositoryTest {

    @Test
    public void testMeaningfulOccupancy() {
        assertTrue(SiriRepository.isMeaningfulOccupancy(OccupancyEnumeration.EMPTY));
        assertTrue(SiriRepository.isMeaningfulOccupancy(OccupancyEnumeration.MANY_SEATS_AVAILABLE));
        assertTrue(SiriRepository.isMeaningfulOccupancy(OccupancyEnumeration.SEATS_AVAILABLE));
        assertTrue(SiriRepository.isMeaningfulOccupancy(OccupancyEnumeration.FEW_SEATS_AVAILABLE));
        assertTrue(SiriRepository.isMeaningfulOccupancy(OccupancyEnumeration.STANDING_AVAILABLE));
        assertTrue(SiriRepository.isMeaningfulOccupancy(OccupancyEnumeration.STANDING_ROOM_ONLY));
        assertTrue(SiriRepository.isMeaningfulOccupancy(OccupancyEnumeration.CRUSHED_STANDING_ROOM_ONLY));
        assertTrue(SiriRepository.isMeaningfulOccupancy(OccupancyEnumeration.NOT_ACCEPTING_PASSENGERS));
        assertTrue(SiriRepository.isMeaningfulOccupancy(OccupancyEnumeration.FULL));

        assertFalse(SiriRepository.isMeaningfulOccupancy(OccupancyEnumeration.UNKNOWN));
        assertFalse(SiriRepository.isMeaningfulOccupancy(OccupancyEnumeration.UNDEFINED));
        assertFalse(SiriRepository.isMeaningfulOccupancy(null));
    }
}
