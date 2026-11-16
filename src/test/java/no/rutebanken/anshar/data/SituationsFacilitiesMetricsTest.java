package no.rutebanken.anshar.data;

import io.micrometer.core.instrument.Counter;
import no.rutebanken.anshar.integration.SpringBootBaseTest;
import no.rutebanken.anshar.metrics.PrometheusMetricsService;
import no.rutebanken.anshar.routes.siri.helpers.SiriObjectFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import uk.org.siri.siri21.FacilityConditionStructure;
import uk.org.siri.siri21.FacilityRef;
import uk.org.siri.siri21.HalfOpenTimestampOutputRangeStructure;
import uk.org.siri.siri21.PtSituationElement;
import uk.org.siri.siri21.SituationNumber;
import uk.org.siri.siri21.SituationVersion;
import uk.org.siri.siri21.WorkflowStatusEnumeration;

import java.math.BigInteger;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Every SX and FM element in a delivery is accounted for exactly once: stored (success), expired
 * or ignored, and every element not stored is counted in app.anshar.data.dropped with its reason.
 * Each test uses its own codespace, since the metrics registry is shared by the Spring context.
 */
public class SituationsFacilitiesMetricsTest extends SpringBootBaseTest {

    private static final String SX = "SITUATION_EXCHANGE";
    private static final String FM = "FACILITY_MONITORING";

    @Autowired
    private Situations situations;

    @Autowired
    private Facilities facilities;

    @Autowired
    private PrometheusMetricsService metrics;

    private String codespace;

    @BeforeEach
    public void init() {
        situations.clearAll();
        facilities.clearAll();
        codespace = "S" + UUID.randomUUID().toString().substring(0, 8);
    }

    private double counter(String name, String... tags) {
        Counter counter = metrics.find(name).tags(tags).counter();
        return counter == null ? 0 : counter.count();
    }

    private double dropped(String dataType, String reason) {
        return counter("app.anshar.data.dropped", "dataType", dataType, "codespace", codespace, "reason", reason);
    }

    private double total(String dataType) {
        return counter("app.anshar.data.total", "dataType", dataType, "agency", codespace);
    }

    private double accounted(String dataType) {
        return counter("app.anshar.data.success", "dataType", dataType, "agency", codespace)
                + counter("app.anshar.data.expired", "dataType", dataType, "agency", codespace)
                + counter("app.anshar.data.ignored", "dataType", dataType, "agency", codespace);
    }

    private PtSituationElement situation(String number, ZonedDateTime endTime) {
        PtSituationElement element = new PtSituationElement();
        element.setCreationTime(ZonedDateTime.now());
        element.setParticipantRef(SiriObjectFactory.createRequestorRef(codespace));
        if (number != null) {
            SituationNumber situationNumber = new SituationNumber();
            situationNumber.setValue(number);
            element.setSituationNumber(situationNumber);
        }
        HalfOpenTimestampOutputRangeStructure period = new HalfOpenTimestampOutputRangeStructure();
        period.setStartTime(ZonedDateTime.now().minusHours(1));
        period.setEndTime(endTime);
        element.getValidityPeriods().add(period);
        return element;
    }

    private PtSituationElement situation(String number) {
        return situation(number, ZonedDateTime.now().plusHours(1));
    }

    private static SituationVersion version(int value) {
        SituationVersion version = new SituationVersion();
        version.setValue(BigInteger.valueOf(value));
        return version;
    }

    private static FacilityConditionStructure facility(String ref, ZonedDateTime endTime) {
        FacilityConditionStructure element = new FacilityConditionStructure();
        if (ref != null) {
            FacilityRef facilityRef = new FacilityRef();
            facilityRef.setValue(ref);
            element.setFacilityRef(facilityRef);
        }
        element.setValidityPeriod(new HalfOpenTimestampOutputRangeStructure());
        element.getValidityPeriod().setStartTime(ZonedDateTime.now().minusHours(1));
        element.getValidityPeriod().setEndTime(endTime);
        return element;
    }

    private static FacilityConditionStructure facility(String ref) {
        return facility(ref, ZonedDateTime.now().plusHours(1));
    }

    // ---- Situations

    @Test
    public void situationWithoutNumberIsCountedAsInvalidStructure() {
        situations.addAll(codespace, List.of(situation(null)));

        assertEquals(1, dropped(SX, "invalid_structure"));
        assertEquals(1, total(SX));
        assertEquals(total(SX), accounted(SX));
    }

    @Test
    public void unchangedSituationIsCountedAsNotUpdated() {
        PtSituationElement situation = situation("TST:SituationNumber:1");
        situations.addAll(codespace, List.of(situation));

        situations.addAll(codespace, List.of(situation));

        assertEquals(1, dropped(SX, "not_updated"));
        assertEquals(total(SX), accounted(SX));
    }

    @Test
    public void olderSituationVersionIsCountedAsOutdated() {
        PtSituationElement newer = situation("TST:SituationNumber:1");
        newer.setVersion(version(2));
        situations.addAll(codespace, List.of(newer));

        PtSituationElement older = situation("TST:SituationNumber:1");
        older.setVersion(version(1));
        situations.addAll(codespace, List.of(older));

        assertEquals(1, dropped(SX, "outdated"));
        assertEquals(total(SX), accounted(SX));
    }

    @Test
    public void draftSituationIsCountedAsDraft() {
        PtSituationElement draft = situation("TST:SituationNumber:1");
        draft.setProgress(WorkflowStatusEnumeration.DRAFT);

        situations.addAll(codespace, List.of(draft));

        assertEquals(1, dropped(SX, "draft"));
        assertEquals(total(SX), accounted(SX));
    }

    @Test
    public void expiredSituationIsCountedAsExpired() {
        situations.addAll(codespace, List.of(situation("TST:SituationNumber:1", ZonedDateTime.now().minusDays(2))));

        assertEquals(1, dropped(SX, "expired"));
        assertEquals(total(SX), accounted(SX));
    }

    @Test
    public void sameSituationTwiceInOneDeliveryIsCountedAsDuplicate() {
        situations.addAll(codespace, List.of(
                situation("TST:SituationNumber:1"),
                situation("TST:SituationNumber:1", ZonedDateTime.now().plusHours(2))));

        assertEquals(1, dropped(SX, "duplicate"));
        assertEquals(total(SX), accounted(SX));
    }

    // ---- Facilities

    @Test
    public void facilityWithoutRefIsCountedAsInvalidStructure() {
        facilities.addAll(codespace, List.of(facility(null)));

        assertEquals(1, dropped(FM, "invalid_structure"));
        assertEquals(1, total(FM));
        assertEquals(total(FM), accounted(FM));
    }

    @Test
    public void unchangedFacilityIsCountedAsNotUpdated() {
        FacilityConditionStructure facility = facility("TST:Facility:1");
        facilities.addAll(codespace, List.of(facility));

        facilities.addAll(codespace, List.of(facility));

        assertEquals(1, dropped(FM, "not_updated"));
        assertEquals(total(FM), accounted(FM));
    }

    @Test
    public void expiredFacilityIsCountedAsExpired() {
        facilities.addAll(codespace, List.of(facility("TST:Facility:1", ZonedDateTime.now().minusDays(2))));

        assertEquals(1, dropped(FM, "expired"));
        assertEquals(total(FM), accounted(FM));
    }

    @Test
    public void sameFacilityTwiceInOneDeliveryIsCountedAsDuplicate() {
        facilities.addAll(codespace, List.of(
                facility("TST:Facility:1"),
                facility("TST:Facility:1", ZonedDateTime.now().plusHours(2))));

        assertEquals(1, dropped(FM, "duplicate"));
        assertEquals(total(FM), accounted(FM));
    }
}
