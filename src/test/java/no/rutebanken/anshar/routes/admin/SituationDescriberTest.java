package no.rutebanken.anshar.routes.admin;

import org.junit.jupiter.api.Test;
import uk.org.ifopt.siri21.StopPlaceRef;
import uk.org.siri.siri21.AffectedLineStructure;
import uk.org.siri.siri21.AffectedOperatorStructure;
import uk.org.siri.siri21.AffectedPlaceStructure;
import uk.org.siri.siri21.AffectedRouteStructure;
import uk.org.siri.siri21.AffectedStopPlaceStructure;
import uk.org.siri.siri21.AffectedStopPointStructure;
import uk.org.siri.siri21.AffectedVehicleJourneyStructure;
import uk.org.siri.siri21.AffectedVehicleStructure;
import uk.org.siri.siri21.AffectsScopeStructure;
import uk.org.siri.siri21.AlertCauseEnumeration;
import uk.org.siri.siri21.ArrivalBoardingActivityEnumeration;
import uk.org.siri.siri21.AudienceEnumeration;
import uk.org.siri.siri21.BlockingStructure;
import uk.org.siri.siri21.BoardingStructure;
import uk.org.siri.siri21.DatedVehicleJourneyRef;
import uk.org.siri.siri21.DelaysStructure;
import uk.org.siri.siri21.DepartureBoardingActivityEnumeration;
import uk.org.siri.siri21.HalfOpenTimestampOutputRangeStructure;
import uk.org.siri.siri21.LineRef;
import uk.org.siri.siri21.NaturalLanguageStringStructure;
import uk.org.siri.siri21.NetworkRefStructure;
import uk.org.siri.siri21.OperatorRefStructure;
import uk.org.siri.siri21.PtConsequenceStructure;
import uk.org.siri.siri21.PtConsequencesStructure;
import uk.org.siri.siri21.PtSituationElement;
import uk.org.siri.siri21.ReportTypeEnumeration;
import uk.org.siri.siri21.RoutePointTypeEnumeration;
import uk.org.siri.siri21.RouteRefStructure;
import uk.org.siri.siri21.ServiceConditionEnumeration;
import uk.org.siri.siri21.SeverityEnumeration;
import uk.org.siri.siri21.SituationNumber;
import uk.org.siri.siri21.SituationSourceStructure;
import uk.org.siri.siri21.SituationSourceTypeEnumeration;
import uk.org.siri.siri21.StopPointRefStructure;
import uk.org.siri.siri21.VehicleJourneyRef;
import uk.org.siri.siri21.VehicleRef;
import uk.org.siri.siri21.WorkflowStatusEnumeration;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SituationDescriberTest {

    @Test
    void describe_nullElement_returnsEmptyString() {
        assertEquals("", SituationDescriber.describe(null));
    }

    @Test
    void describe_minimalElement_doesNotThrow_andContainsCauseUnknown() {
        PtSituationElement element = new PtSituationElement();
        SituationNumber sn = new SituationNumber();
        sn.setValue("NSR:Situation:42");
        element.setSituationNumber(sn);

        String out = SituationDescriber.describe(element);

        assertTrue(out != null,
                "output should be non-null");
        assertTrue(out.contains("Cause: unknown"),
                "fallback cause should be 'unknown' when nothing is set, was: " + out);
    }

    @Test
    void describe_source_rendersAsMetaLine() {
        PtSituationElement element = new PtSituationElement();
        SituationNumber sn = new SituationNumber();
        sn.setValue("S1");
        element.setSituationNumber(sn);

        SituationSourceStructure src = new SituationSourceStructure();
        src.setSourceType(SituationSourceTypeEnumeration.FEED);
        element.setSource(src);

        String out = SituationDescriber.describe(element);

        assertTrue(out.contains("Source: feed"), "Source should render as meta line, was: " + out);
    }

    @Test
    void describe_planned_rendersAsMetaLine() {
        PtSituationElement element = new PtSituationElement();
        SituationNumber sn = new SituationNumber();
        sn.setValue("S1");
        element.setSituationNumber(sn);

        element.setPlanned(Boolean.FALSE);
        String out = SituationDescriber.describe(element);
        assertTrue(out.contains("Planned: no"), "Planned=false should render as 'Planned: no', was: " + out);

        element.setPlanned(Boolean.TRUE);
        String outTrue = SituationDescriber.describe(element);
        assertTrue(outTrue.contains("Planned: yes"), "Planned=true should render as 'Planned: yes', was: " + outTrue);
    }

    @Test
    void describe_cause_combinesAlertCauseAndSubReasons() {
        PtSituationElement element = new PtSituationElement();
        SituationNumber sn = new SituationNumber();
        sn.setValue("S1");
        element.setSituationNumber(sn);

        element.setEquipmentReason("equipmentFailure");
        element.setEquipmentSubReason("signalFailure");

        String out = SituationDescriber.describe(element);

        assertTrue(out.contains("Cause: equipmentFailure / signalFailure"),
                "should pair primary reason with sub-reason, was: " + out);
    }

    @Test
    void describe_cause_multiplePrimariesCommaSeparated() {
        PtSituationElement element = new PtSituationElement();
        SituationNumber sn = new SituationNumber();
        sn.setValue("S1");
        element.setSituationNumber(sn);

        element.setEnvironmentReason("fog");
        element.setEquipmentReason("equipmentFailure");
        element.setEquipmentSubReason("signalFailure");

        String out = SituationDescriber.describe(element);

        assertTrue(out.contains("Cause: fog, equipmentFailure / signalFailure"),
                "should comma-separate primaries with sub-reasons attached, was: " + out);
    }

    @Test
    void describe_cause_alertCauseEnumValueIsLowerCamel() {
        PtSituationElement element = new PtSituationElement();
        SituationNumber sn = new SituationNumber();
        sn.setValue("S1");
        element.setSituationNumber(sn);
        element.setAlertCause(AlertCauseEnumeration.MAINTENANCE_WORK);

        String out = SituationDescriber.describe(element);

        assertTrue(out.contains("Cause: maintenanceWork"), out);
    }

    @Test
    void describe_singleLineMeta_skipsNullsAndUsesFixedOrder() {
        PtSituationElement element = new PtSituationElement();
        SituationNumber sn = new SituationNumber();
        sn.setValue("S1");
        element.setSituationNumber(sn);

        SituationSourceStructure src = new SituationSourceStructure();
        src.setSourceType(SituationSourceTypeEnumeration.FEED);
        element.setSource(src);
        element.setPlanned(Boolean.FALSE);
        element.setSeverity(SeverityEnumeration.SEVERE);
        element.setReportType(ReportTypeEnumeration.INCIDENT);
        element.setAudience(AudienceEnumeration.PUBLIC);
        // ScopeType, Verification, etc. left null

        String out = SituationDescriber.describe(element);

        int srcIdx = out.indexOf("Source: feed");
        int plnIdx = out.indexOf("Planned: no");
        int sevIdx = out.indexOf("Severity: severe");
        int repIdx = out.indexOf("ReportType: incident");
        int audIdx = out.indexOf("Audience: public");
        assertTrue(srcIdx >= 0 && plnIdx > srcIdx && sevIdx > plnIdx,
                "Source/Planned must appear before Severity, was: " + out);
        assertTrue(sevIdx >= 0 && repIdx > sevIdx && audIdx > repIdx,
                "Severity/ReportType/Audience must appear in that fixed order, was: " + out);
        assertTrue(!out.contains("ScopeType:"), "absent ScopeType must be skipped: " + out);
    }

    @Test
    void describe_publicationWindow_rendersWhenPresent_skipsWhenAbsent() {
        PtSituationElement element = new PtSituationElement();
        SituationNumber sn = new SituationNumber();
        sn.setValue("S1");
        element.setSituationNumber(sn);
        HalfOpenTimestampOutputRangeStructure pw = new HalfOpenTimestampOutputRangeStructure();
        pw.setStartTime(ZonedDateTime.parse("2026-05-04T20:00Z"));
        pw.setEndTime(ZonedDateTime.parse("2026-05-06T12:00Z"));
        element.getPublicationWindows().add(pw);

        String out = SituationDescriber.describe(element);

        assertTrue(out.contains("PublicationWindow:"), out);
        assertTrue(out.contains("  - 2026-05-04T20:00Z -> 2026-05-06T12:00Z"), out);
    }

    @Test
    void describe_reasonNames_rendersTextWithLang_omitsLangSuffixWhenNull() {
        PtSituationElement element = new PtSituationElement();
        SituationNumber sn = new SituationNumber();
        sn.setValue("S1");
        element.setSituationNumber(sn);
        NaturalLanguageStringStructure nb = new NaturalLanguageStringStructure();
        nb.setValue("Signalfeil ved Lillestrøm"); nb.setLang("nb");
        NaturalLanguageStringStructure noLang = new NaturalLanguageStringStructure();
        noLang.setValue("Signal failure"); // lang null
        element.getReasonNames().add(nb);
        element.getReasonNames().add(noLang);

        String out = SituationDescriber.describe(element);

        assertTrue(out.contains("ReasonNames: \"Signalfeil ved Lillestrøm\" (nb), \"Signal failure\""), out);
    }

    @Test
    void describe_reasonNames_preservesInternalWhitespace() {
        PtSituationElement element = new PtSituationElement();
        SituationNumber sn = new SituationNumber();
        sn.setValue("S1");
        element.setSituationNumber(sn);
        NaturalLanguageStringStructure r = new NaturalLanguageStringStructure();
        r.setValue("two   spaces and\ttab");
        r.setLang("nb");
        element.getReasonNames().add(r);

        String out = SituationDescriber.describe(element);

        assertTrue(out.contains("\"two   spaces and\ttab\" (nb)"),
                "internal whitespace must be preserved verbatim, was: " + out);
    }

    @Test
    void describe_keywordsAndPublications_renderedAsCommaList() {
        PtSituationElement element = new PtSituationElement();
        SituationNumber sn = new SituationNumber();
        sn.setValue("S1");
        element.setSituationNumber(sn);
        element.getKeywords().add("rail");
        element.getKeywords().add("oslo");
        element.getPublications().add("public");
        element.getPublications().add("internal");

        String out = SituationDescriber.describe(element);

        assertTrue(out.contains("Keywords: rail, oslo"), out);
        assertTrue(out.contains("Publications: public, internal"), out);
    }

    @Test
    void summarise_nullList_returnsNull() {
        assertNull(SituationDescriber.summarise(null, "Lines", 0));
    }

    @Test
    void summarise_emptyList_returnsNull() {
        assertNull(SituationDescriber.summarise(List.of(), "Lines", 0));
    }

    @Test
    void summarise_oneElement_noSuffix() {
        assertEquals("Lines (1): A", SituationDescriber.summarise(List.of("A"), "Lines", 0));
    }

    @Test
    void summarise_threeElements_allShown_noSuffix() {
        assertEquals("Lines (3): A, B, C",
                SituationDescriber.summarise(List.of("A", "B", "C"), "Lines", 0));
    }

    @Test
    void summarise_fourElements_threeShownPlusAnd1More() {
        assertEquals("Lines (4): A, B, C and 1 more",
                SituationDescriber.summarise(List.of("A", "B", "C", "D"), "Lines", 0));
    }

    @Test
    void summarise_hundredElements_truncatedToThreePlusAnd97More() {
        java.util.List<String> refs = new java.util.ArrayList<>();
        for (int i = 0; i < 100; i++) refs.add("R" + i);
        assertEquals("Lines (100): R0, R1, R2 and 97 more",
                SituationDescriber.summarise(refs, "Lines", 0));
    }

    @Test
    void summarise_indentLevelTwo_addsFourSpaces() {
        assertEquals("    Lines (1): A",
                SituationDescriber.summarise(List.of("A"), "Lines", 2));
    }

    @Test
    void describe_affects_operators_truncatedAndCounted() {
        PtSituationElement element = new PtSituationElement();
        SituationNumber sn = new SituationNumber();
        sn.setValue("S1");
        element.setSituationNumber(sn);

        AffectsScopeStructure affects = new AffectsScopeStructure();
        AffectsScopeStructure.Operators ops = new AffectsScopeStructure.Operators();
        for (String code : List.of("NSB", "RUT", "ATB", "AKT")) {
            AffectedOperatorStructure op = new AffectedOperatorStructure();
            OperatorRefStructure ref = new OperatorRefStructure();
            ref.setValue(code);
            op.setOperatorRef(ref);
            ops.getAffectedOperators().add(op);
        }
        affects.setOperators(ops);
        element.setAffects(affects);

        String out = SituationDescriber.describe(element);

        assertTrue(out.contains("Affects:"), out);
        assertTrue(out.contains("  Operators (4): NSB, RUT, ATB and 1 more"), out);
    }

    @Test
    void describe_affects_operators_allOperatorsFlag() {
        PtSituationElement element = new PtSituationElement();
        SituationNumber sn = new SituationNumber();
        sn.setValue("S1");
        element.setSituationNumber(sn);
        AffectsScopeStructure affects = new AffectsScopeStructure();
        AffectsScopeStructure.Operators ops = new AffectsScopeStructure.Operators();
        ops.setAllOperators(""); // presence == flag set; SIRI uses an empty-element marker
        affects.setOperators(ops);
        element.setAffects(affects);

        String out = SituationDescriber.describe(element);

        assertTrue(out.contains("Operators: allOperators=true"), out);
    }

    @Test
    void describe_affects_networksWithAllLinesTrueAndNoLines() {
        PtSituationElement element = new PtSituationElement();
        SituationNumber sn = new SituationNumber();
        sn.setValue("S1");
        element.setSituationNumber(sn);

        AffectsScopeStructure affects = new AffectsScopeStructure();
        AffectsScopeStructure.Networks nets = new AffectsScopeStructure.Networks();
        AffectsScopeStructure.Networks.AffectedNetwork net =
                new AffectsScopeStructure.Networks.AffectedNetwork();
        NetworkRefStructure netRef = new NetworkRefStructure();
        netRef.setValue("NSB:Network:Rail");
        net.setNetworkRef(netRef);
        net.setAllLines(""); // SIRI marker — empty element means "all lines"
        nets.getAffectedNetworks().add(net);
        affects.setNetworks(nets);
        element.setAffects(affects);

        String out = SituationDescriber.describe(element);

        assertTrue(out.contains("  Networks: allLines=true"), out);
        assertTrue(out.contains("    Network NSB:Network:Rail"), out);
        assertTrue(!out.contains("Lines ("), "no Lines line when AllLines is set with empty list: " + out);
    }

    @Test
    void describe_affects_networksWithNestedLinesAndRoutesAndStops() {
        PtSituationElement element = new PtSituationElement();
        SituationNumber sn = new SituationNumber();
        sn.setValue("S1");
        element.setSituationNumber(sn);

        AffectsScopeStructure affects = new AffectsScopeStructure();
        AffectsScopeStructure.Networks nets = new AffectsScopeStructure.Networks();

        AffectsScopeStructure.Networks.AffectedNetwork net =
                new AffectsScopeStructure.Networks.AffectedNetwork();
        NetworkRefStructure netRef = new NetworkRefStructure();
        netRef.setValue("NSB:Network:Rail");
        net.setNetworkRef(netRef);

        AffectedLineStructure line = new AffectedLineStructure();
        LineRef lineRef = new LineRef();
        lineRef.setValue("NSB:Line:R10");
        line.setLineRef(lineRef);

        // Routes (1)
        AffectedLineStructure.Routes routes = new AffectedLineStructure.Routes();
        AffectedRouteStructure route = new AffectedRouteStructure();
        RouteRefStructure routeRef = new RouteRefStructure();
        routeRef.setValue("NSB:Route:R10:1");
        route.setRouteRef(routeRef);
        routes.getAffectedRoutes().add(route);
        line.setRoutes(routes);

        // StopPoints (5)
        AffectedLineStructure.StopPoints stops = new AffectedLineStructure.StopPoints();
        for (int i = 1; i <= 5; i++) {
            AffectedStopPointStructure sp = new AffectedStopPointStructure();
            StopPointRefStructure spRef = new StopPointRefStructure();
            spRef.setValue("NSR:Quay:" + i);
            sp.setStopPointRef(spRef);
            stops.getAffectedStopPoints().add(sp);
        }
        line.setStopPoints(stops);

        net.getAffectedLines().add(line);
        nets.getAffectedNetworks().add(net);
        affects.setNetworks(nets);
        element.setAffects(affects);

        String out = SituationDescriber.describe(element);

        assertTrue(out.contains("  Networks: allLines=false"), out);
        assertTrue(out.contains("    Network NSB:Network:Rail"), out);
        assertTrue(out.contains("      Lines (1): NSB:Line:R10"), out);
        assertTrue(out.contains("        Line NSB:Line:R10"), out);
        assertTrue(out.contains("          Routes (1): NSB:Route:R10:1"), out);
        assertTrue(out.contains("          Stops (5): NSR:Quay:1, NSR:Quay:2, NSR:Quay:3 and 2 more"), out);
    }

    @Test
    void describe_affects_stopPlaces_truncated() {
        PtSituationElement element = new PtSituationElement();
        SituationNumber sn = new SituationNumber();
        sn.setValue("S1");
        element.setSituationNumber(sn);

        AffectsScopeStructure affects = new AffectsScopeStructure();
        AffectsScopeStructure.StopPlaces sps = new AffectsScopeStructure.StopPlaces();
        for (int i = 0; i < 5; i++) {
            AffectedStopPlaceStructure sp = new AffectedStopPlaceStructure();
            StopPlaceRef spr = new StopPlaceRef();
            spr.setValue("NSR:StopPlace:" + (5980 + i));
            sp.setStopPlaceRef(spr);
            sps.getAffectedStopPlaces().add(sp);
        }
        affects.setStopPlaces(sps);
        element.setAffects(affects);

        String out = SituationDescriber.describe(element);

        assertTrue(out.contains("  StopPlaces (5): NSR:StopPlace:5980, NSR:StopPlace:5981, NSR:StopPlace:5982 and 2 more"), out);
    }

    @Test
    void describe_affects_stopPoints_renderedAsList() {
        PtSituationElement element = new PtSituationElement();
        SituationNumber sn = new SituationNumber();
        sn.setValue("S1");
        element.setSituationNumber(sn);

        AffectsScopeStructure affects = new AffectsScopeStructure();
        AffectsScopeStructure.StopPoints sps = new AffectsScopeStructure.StopPoints();
        AffectedStopPointStructure sp1 = new AffectedStopPointStructure();
        StopPointRefStructure spr1 = new StopPointRefStructure();
        spr1.setValue("NSR:Quay:11023");
        sp1.setStopPointRef(spr1);
        sps.getAffectedStopPoints().add(sp1);
        AffectedStopPointStructure sp2 = new AffectedStopPointStructure();
        StopPointRefStructure spr2 = new StopPointRefStructure();
        spr2.setValue("NSR:Quay:11024");
        sp2.setStopPointRef(spr2);
        sps.getAffectedStopPoints().add(sp2);
        affects.setStopPoints(sps);
        element.setAffects(affects);

        String out = SituationDescriber.describe(element);

        assertTrue(out.contains("  StopPoints (2): NSR:Quay:11023, NSR:Quay:11024"), out);
    }

    @Test
    void describe_affects_vehicleJourneysWithCallsTruncated() {
        PtSituationElement element = new PtSituationElement();
        SituationNumber sn = new SituationNumber();
        sn.setValue("S1");
        element.setSituationNumber(sn);

        AffectsScopeStructure affects = new AffectsScopeStructure();
        AffectsScopeStructure.VehicleJourneys vjs = new AffectsScopeStructure.VehicleJourneys();
        AffectedVehicleJourneyStructure vj = new AffectedVehicleJourneyStructure();

        VehicleJourneyRef vjRef = new VehicleJourneyRef();
        vjRef.setValue("NSB:ServiceJourney:1234");
        vj.getVehicleJourneyReves().add(vjRef);

        LineRef lineRef = new LineRef();
        lineRef.setValue("NSB:Line:R10");
        vj.setLineRef(lineRef);

        AffectedStopPointStructure origin = new AffectedStopPointStructure();
        StopPointRefStructure originRef = new StopPointRefStructure();
        originRef.setValue("NSR:StopPlace:1");
        origin.setStopPointRef(originRef);
        vj.getOrigins().add(origin);

        AffectedStopPointStructure dest = new AffectedStopPointStructure();
        StopPointRefStructure destRef = new StopPointRefStructure();
        destRef.setValue("NSR:StopPlace:99");
        dest.setStopPointRef(destRef);
        vj.getDestinations().add(dest);

        AffectedRouteStructure route = new AffectedRouteStructure();
        AffectedRouteStructure.StopPoints calls = new AffectedRouteStructure.StopPoints();
        for (int i = 1; i <= 15; i++) {
            AffectedStopPointStructure call = new AffectedStopPointStructure();
            StopPointRefStructure cRef = new StopPointRefStructure();
            cRef.setValue("NSR:Quay:" + i);
            call.setStopPointRef(cRef);
            calls.getAffectedStopPointsAndLinkProjectionToNextStopPoints().add(call);
        }
        route.setStopPoints(calls);
        vj.getRoutes().add(route);

        vjs.getAffectedVehicleJourneies().add(vj);
        affects.setVehicleJourneys(vjs);
        element.setAffects(affects);

        String out = SituationDescriber.describe(element);

        assertTrue(out.contains("  VehicleJourneys (1):"), out);
        assertTrue(out.contains("    Journey NSB:ServiceJourney:1234  line=NSB:Line:R10  origin=NSR:StopPlace:1 dest=NSR:StopPlace:99"),
                "expected journey detail line, was: " + out);
        assertTrue(out.contains("      Calls (15):"), out);
        assertTrue(out.contains("        NSR:Quay:1\n"), out);
        assertTrue(out.contains("        NSR:Quay:2\n"), out);
        assertTrue(out.contains("        NSR:Quay:3\n"), out);
        assertTrue(out.contains("        ...and 12 more"), out);
    }

    @Test
    void describe_affects_vehiclesPlacesRoads_renderedAsRefLists() {
        PtSituationElement element = new PtSituationElement();
        SituationNumber sn = new SituationNumber();
        sn.setValue("S1");
        element.setSituationNumber(sn);
        AffectsScopeStructure affects = new AffectsScopeStructure();

        AffectsScopeStructure.Vehicles vehicles = new AffectsScopeStructure.Vehicles();
        AffectedVehicleStructure veh = new AffectedVehicleStructure();
        VehicleRef vRef = new VehicleRef();
        vRef.setValue("NSB:Vehicle:7");
        veh.setVehicleRef(vRef);
        vehicles.getAffectedVehicles().add(veh);
        affects.setVehicles(vehicles);

        AffectsScopeStructure.Places places = new AffectsScopeStructure.Places();
        AffectedPlaceStructure pl = new AffectedPlaceStructure();
        pl.setPlaceRef("NSR:Place:Sentrum");
        places.getAffectedPlaces().add(pl);
        affects.setPlaces(places);

        element.setAffects(affects);

        String out = SituationDescriber.describe(element);

        assertTrue(out.contains("  Vehicles (1): NSB:Vehicle:7"), out);
        assertTrue(out.contains("  Places (1): NSR:Place:Sentrum"), out);
    }

    @Test
    void describe_closed_withoutAffects_rendersExplicitNote() {
        PtSituationElement element = new PtSituationElement();
        SituationNumber sn = new SituationNumber();
        sn.setValue("S1");
        element.setSituationNumber(sn);
        element.setProgress(WorkflowStatusEnumeration.CLOSED);
        // affects intentionally null

        String out = SituationDescriber.describe(element);

        assertTrue(out.contains("Affects: (none — situation closed)"), out);
    }

    @Test
    void describe_open_withoutAffects_omitsAffectsSection() {
        PtSituationElement element = new PtSituationElement();
        SituationNumber sn = new SituationNumber();
        sn.setValue("S1");
        element.setSituationNumber(sn);
        element.setProgress(WorkflowStatusEnumeration.OPEN);

        String out = SituationDescriber.describe(element);

        assertTrue(!out.contains("Affects:"), "open situation with null Affects should not emit Affects header: " + out);
    }

    @Test
    void describe_consequence_minimal_periodOnly() {
        PtSituationElement element = new PtSituationElement();
        SituationNumber sn = new SituationNumber();
        sn.setValue("S1");
        element.setSituationNumber(sn);

        PtConsequencesStructure cs = new PtConsequencesStructure();
        PtConsequenceStructure c = new PtConsequenceStructure();
        HalfOpenTimestampOutputRangeStructure p = new HalfOpenTimestampOutputRangeStructure();
        p.setStartTime(ZonedDateTime.parse("2026-05-05T06:00Z"));
        p.setEndTime(ZonedDateTime.parse("2026-05-05T09:00Z"));
        c.getPeriods().add(p);
        cs.getConsequences().add(c);
        element.setConsequences(cs);

        String out = SituationDescriber.describe(element);

        assertTrue(out.contains("Consequences (1):"), out);
        assertTrue(out.contains("  [1] period=2026-05-05T06:00Z -> 2026-05-05T09:00Z"), out);
        assertTrue(!out.contains("Condition:"), out);
        assertTrue(!out.contains("Boarding:"), out);
    }

    @Test
    void describe_consequence_multiplePeriods_commaSeparated() {
        PtSituationElement element = new PtSituationElement();
        SituationNumber sn = new SituationNumber();
        sn.setValue("S1");
        element.setSituationNumber(sn);

        PtConsequencesStructure cs = new PtConsequencesStructure();
        PtConsequenceStructure c = new PtConsequenceStructure();
        HalfOpenTimestampOutputRangeStructure p1 = new HalfOpenTimestampOutputRangeStructure();
        p1.setStartTime(ZonedDateTime.parse("2026-05-05T06:00Z"));
        p1.setEndTime(ZonedDateTime.parse("2026-05-05T09:00Z"));
        HalfOpenTimestampOutputRangeStructure p2 = new HalfOpenTimestampOutputRangeStructure();
        p2.setStartTime(ZonedDateTime.parse("2026-05-06T06:00Z"));
        c.getPeriods().add(p1);
        c.getPeriods().add(p2);
        cs.getConsequences().add(c);
        element.setConsequences(cs);

        String out = SituationDescriber.describe(element);

        assertTrue(out.contains("  [1] period=2026-05-05T06:00Z -> 2026-05-05T09:00Z, period=2026-05-06T06:00Z -> open-ended"),
                "multiple periods must be comma-separated, was: " + out);
    }

    @Test
    void describe_consequence_fullyPopulatedWithNestedAffects() {
        PtSituationElement element = new PtSituationElement();
        SituationNumber sn = new SituationNumber();
        sn.setValue("S1");
        element.setSituationNumber(sn);

        PtConsequencesStructure cs = new PtConsequencesStructure();
        PtConsequenceStructure c = new PtConsequenceStructure();

        HalfOpenTimestampOutputRangeStructure p = new HalfOpenTimestampOutputRangeStructure();
        p.setStartTime(ZonedDateTime.parse("2026-05-05T06:00Z"));
        p.setEndTime(ZonedDateTime.parse("2026-05-05T09:00Z"));
        c.getPeriods().add(p);

        c.getConditions().add(ServiceConditionEnumeration.CANCELLED);
        c.getConditions().add(ServiceConditionEnumeration.NO_SERVICE);
        c.setSeverity(SeverityEnumeration.SEVERE);

        BoardingStructure boarding = new BoardingStructure();
        boarding.setArrivalBoardingActivity(ArrivalBoardingActivityEnumeration.ALIGHTING);
        boarding.setDepartureBoardingActivity(DepartureBoardingActivityEnumeration.NO_BOARDING);
        c.setBoarding(boarding);

        BlockingStructure blocking = new BlockingStructure();
        blocking.setJourneyPlanner(true);
        c.setBlocking(blocking);

        DelaysStructure delays = new DelaysStructure();
        delays.setDelay(Duration.parse("PT15M"));
        c.setDelays(delays);

        AffectsScopeStructure innerAffects = new AffectsScopeStructure();
        AffectsScopeStructure.Networks nets = new AffectsScopeStructure.Networks();
        AffectsScopeStructure.Networks.AffectedNetwork net = new AffectsScopeStructure.Networks.AffectedNetwork();
        AffectedLineStructure aline = new AffectedLineStructure();
        LineRef alineRef = new LineRef();
        alineRef.setValue("NSB:Line:R10");
        aline.setLineRef(alineRef);
        net.getAffectedLines().add(aline);
        nets.getAffectedNetworks().add(net);
        innerAffects.setNetworks(nets);
        c.setAffects(innerAffects);

        cs.getConsequences().add(c);
        element.setConsequences(cs);

        String out = SituationDescriber.describe(element);

        assertTrue(out.contains("Consequences (1):"), out);
        assertTrue(out.contains("  [1] period=2026-05-05T06:00Z -> 2026-05-05T09:00Z"), out);
        assertTrue(out.contains("      Condition: cancelled, noService"), out);
        assertTrue(out.contains("      Severity: severe"), out);
        assertTrue(out.contains("      Boarding: arrival=alighting, departure=noBoarding"), out);
        assertTrue(out.contains("      Blocking: journeyPlanner=true"), out);
        assertTrue(out.contains("      Delay: PT15M"), out);
        assertTrue(out.contains("      Affects:"), out);
        assertTrue(out.contains("        Networks: allLines=false"), out);
        assertTrue(out.contains("          Network "), out);
    }

    @Test
    void describe_kitchenSink_matchesExpected() {
        PtSituationElement element = new PtSituationElement();
        SituationNumber sn = new SituationNumber();
        sn.setValue("NSR:Situation:1234");
        element.setSituationNumber(sn);
        element.setCreationTime(ZonedDateTime.parse("2026-05-05T08:12:00Z"));
        element.setProgress(WorkflowStatusEnumeration.OPEN);
        element.setPlanned(Boolean.FALSE);
        SituationSourceStructure src = new SituationSourceStructure();
        src.setSourceType(SituationSourceTypeEnumeration.FEED);
        element.setSource(src);
        element.setEquipmentReason("equipmentFailure");
        element.setEquipmentSubReason("signalFailure");
        element.setSeverity(SeverityEnumeration.SEVERE);
        element.setReportType(ReportTypeEnumeration.INCIDENT);
        element.setAudience(AudienceEnumeration.PUBLIC);
        // Validity no longer populated — field is not rendered

        // Affects: one operator
        AffectsScopeStructure affects = new AffectsScopeStructure();
        AffectsScopeStructure.Operators ops = new AffectsScopeStructure.Operators();
        AffectedOperatorStructure op = new AffectedOperatorStructure();
        OperatorRefStructure opr = new OperatorRefStructure();
        opr.setValue("NSB");
        op.setOperatorRef(opr);
        ops.getAffectedOperators().add(op);
        affects.setOperators(ops);
        element.setAffects(affects);

        // Consequences: one delayed
        PtConsequencesStructure cs = new PtConsequencesStructure();
        PtConsequenceStructure c = new PtConsequenceStructure();
        HalfOpenTimestampOutputRangeStructure cp = new HalfOpenTimestampOutputRangeStructure();
        cp.setStartTime(ZonedDateTime.parse("2026-05-05T09:00Z"));
        c.getPeriods().add(cp);
        c.getConditions().add(ServiceConditionEnumeration.DELAYED);
        cs.getConsequences().add(c);
        element.setConsequences(cs);

        String expected = String.join("\n",
            "Cause: equipmentFailure / signalFailure",
            "Source: feed",
            "Planned: no",
            "Severity: severe",
            "ReportType: incident",
            "Audience: public",
            "",
            "Affects:",
            "  Operators (1): NSB",
            "",
            "Consequences (1):",
            "  [1] period=2026-05-05T09:00Z -> open-ended",
            "      Condition: delayed"
        );

        assertEquals(expected, SituationDescriber.describe(element));
    }

    @Test
    void describe_affects_vehicleJourneyWithDatedRefAndStopConditions() {
        PtSituationElement element = new PtSituationElement();
        SituationNumber sn = new SituationNumber();
        sn.setValue("S1");
        element.setSituationNumber(sn);

        AffectsScopeStructure affects = new AffectsScopeStructure();
        AffectsScopeStructure.VehicleJourneys vjs = new AffectsScopeStructure.VehicleJourneys();
        AffectedVehicleJourneyStructure vj = new AffectedVehicleJourneyStructure();

        DatedVehicleJourneyRef dRef = new DatedVehicleJourneyRef();
        dRef.setValue("VYG:DatedServiceJourney:1624_DAL-DRM_26-03-26");
        vj.getDatedVehicleJourneyReves().add(dRef);

        AffectedRouteStructure route = new AffectedRouteStructure();
        AffectedRouteStructure.StopPoints calls = new AffectedRouteStructure.StopPoints();
        String[] stopRefs = {"NSR:StopPlace:621", "NSR:StopPlace:268", "NSR:StopPlace:238"};
        for (String ref : stopRefs) {
            AffectedStopPointStructure call = new AffectedStopPointStructure();
            StopPointRefStructure spr = new StopPointRefStructure();
            spr.setValue(ref);
            call.setStopPointRef(spr);
            call.getStopConditions().add(RoutePointTypeEnumeration.START_POINT);
            call.getStopConditions().add(RoutePointTypeEnumeration.NOT_STOPPING);
            calls.getAffectedStopPointsAndLinkProjectionToNextStopPoints().add(call);
        }
        route.setStopPoints(calls);
        vj.getRoutes().add(route);

        vjs.getAffectedVehicleJourneies().add(vj);
        affects.setVehicleJourneys(vjs);
        element.setAffects(affects);

        String out = SituationDescriber.describe(element);

        assertTrue(out.contains("    Journey VYG:DatedServiceJourney:1624_DAL-DRM_26-03-26"),
                "should fall back to DatedVehicleJourneyRef when VehicleJourneyRef is absent, was: " + out);
        assertTrue(out.contains("      Calls (3):"), out);
        assertTrue(out.contains("        NSR:StopPlace:621 [startPoint,notStopping]\n"), out);
        assertTrue(out.contains("        NSR:StopPlace:268 [startPoint,notStopping]\n"), out);
        assertTrue(out.contains("        NSR:StopPlace:238 [startPoint,notStopping]"),
                "should render StopCondition values per-line in [..,..] brackets, was: " + out);
    }
}