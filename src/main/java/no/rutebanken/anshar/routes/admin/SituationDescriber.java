/*
 * Licensed under the EUPL, Version 1.2 or – as soon they will be approved by
 * the European Commission - subsequent versions of the EUPL (the "Licence");
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 *
 *   https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and
 * limitations under the Licence.
 */

package no.rutebanken.anshar.routes.admin;

import uk.org.siri.siri21.AffectedLineStructure;
import uk.org.siri.siri21.AffectedOperatorStructure;
import uk.org.siri.siri21.AffectedPlaceStructure;
import uk.org.siri.siri21.AffectedRoadsStructure;
import uk.org.siri.siri21.AffectedRouteStructure;
import uk.org.siri.siri21.AffectedStopPlaceStructure;
import uk.org.siri.siri21.AffectedStopPointStructure;
import uk.org.siri.siri21.AffectedVehicleJourneyStructure;
import uk.org.siri.siri21.AffectedVehicleStructure;
import uk.org.siri.siri21.AffectsScopeStructure;
import uk.org.siri.siri21.AlertCauseEnumeration;
import uk.org.siri.siri21.HalfOpenTimestampOutputRangeStructure;
import uk.org.siri.siri21.NaturalLanguageStringStructure;
import uk.org.siri.siri21.PtConsequenceStructure;
import uk.org.siri.siri21.PtConsequencesStructure;
import uk.org.siri.siri21.PtSituationElement;
import uk.org.siri.siri21.RoutePointTypeEnumeration;
import uk.org.siri.siri21.ServiceConditionEnumeration;
import uk.org.siri.siri21.SituationSourceStructure;
import uk.org.siri.siri21.WorkflowStatusEnumeration;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns a PtSituationElement into a single readable English text block
 * for the /anshar/situations/{datasetId} debug endpoint.
 *
 * Pure function. No I/O. No external lookups. Refs rendered raw.
 *
 */
public final class SituationDescriber {

    static final int LIST_LIMIT = 3;
    static final String OPEN_ENDED = "open-ended";

    private SituationDescriber() {}

    public static String describe(PtSituationElement element) {
        if (element == null) return "";
        StringBuilder sb = new StringBuilder();
        appendHeader(sb, element);
        if (element.getAffects() == null) {
            if (element.getProgress() == WorkflowStatusEnumeration.CLOSED) {
                sb.append("\nAffects: (none — situation closed)\n");
            }
        } else {
            appendAffects(sb, element.getAffects(), 1);
        }
        appendConsequences(sb, element.getConsequences(), 1);
        return sb.toString().stripTrailing();
    }

    static void appendHeader(StringBuilder sb, PtSituationElement element) {
        sb.append("Cause: ").append(buildCause(element)).append('\n');

        // Single-line meta — fixed order: Source, Planned, Severity, ReportType, ScopeType,
        // Audience, Verification, QualityIndex, Reality, Likelihood, Sensitivity, Priority.
        appendMetaLine(sb, "Source",       sourceLabel(element.getSource()));
        appendMetaLine(sb, "Planned",      element.isPlanned() != null ? (element.isPlanned() ? "yes" : "no") : null);
        appendMetaLine(sb, "Severity",     enumValue(element.getSeverity()));
        appendMetaLine(sb, "ReportType",   enumValue(element.getReportType()));
        appendMetaLine(sb, "ScopeType",    enumValue(element.getScopeType()));
        appendMetaLine(sb, "Audience",     enumValue(element.getAudience()));
        appendMetaLine(sb, "Verification", enumValue(element.getVerification()));
        appendMetaLine(sb, "QualityIndex", enumValue(element.getQualityIndex()));
        appendMetaLine(sb, "Reality",      enumValue(element.getReality()));
        appendMetaLine(sb, "Likelihood",   enumValue(element.getLikelihood()));
        appendMetaLine(sb, "Sensitivity",  enumValue(element.getSensitivity()));
        appendMetaLine(sb, "Priority",     element.getPriority() != null ? element.getPriority().toString() : null);

        appendPeriodList(sb, "PublicationWindow", element.getPublicationWindows());

        appendReasonNames(sb, element.getReasonNames());

        appendCsvLine(sb, "Keywords",     element.getKeywords());
        appendCsvLine(sb, "Publications", element.getPublications());
    }

    static String sourceLabel(SituationSourceStructure source) {
        if (source == null) {
            return null;
        }
        if (source.getSourceType() != null) {
            return source.getSourceType().value();
        }
        if (source.getName() != null && !source.getName().getValue().isBlank()) {
            return source.getName().getValue();
        }
        return null;
    }

    static String buildCause(PtSituationElement element) {
        List<String> primaries = new ArrayList<>();
        addReason(primaries, alertCauseValue(element.getAlertCause()), null);
        addReason(primaries, element.getEnvironmentReason(),   element.getEnvironmentSubReason());
        addReason(primaries, element.getEquipmentReason(),     element.getEquipmentSubReason());
        addReason(primaries, element.getPersonnelReason(),     element.getPersonnelSubReason());
        addReason(primaries, element.getMiscellaneousReason(), element.getMiscellaneousSubReason());
        addReason(primaries, element.getUndefinedReason(),     null);
        addReason(primaries, element.getUnknownReason(),       null);
        if (primaries.isEmpty()) {
            return "unknown";
        }
        return String.join(", ", primaries);
    }

    private static String alertCauseValue(AlertCauseEnumeration cause) {
        return cause != null ? cause.value() : null;
    }

    private static void addReason(List<String> out, String primary, String subReason) {
        if (primary == null || primary.isBlank()) {
            return;
        }
        if (subReason != null && !subReason.isBlank()) {
            out.add(primary + " / " + subReason);
        } else {
            out.add(primary);
        }
    }

    private static String enumValue(Enum<?> e) {
        if (e == null) return null;
        // siri-java-model enums expose .value() returning the lowerCamel XML token
        try {
            java.lang.reflect.Method m = e.getClass().getMethod("value");
            Object v = m.invoke(e);
            return v != null ? v.toString() : e.name().toLowerCase();
        } catch (ReflectiveOperationException ex) {
            return e.name().toLowerCase();
        }
    }

    private static void appendMetaLine(StringBuilder sb, String label, String value) {
        if (value == null || value.isBlank()) return;
        sb.append(label).append(": ").append(value).append('\n');
    }

    private static void appendPeriodList(StringBuilder sb, String label,
            List<HalfOpenTimestampOutputRangeStructure> periods) {
        if (periods == null || periods.isEmpty()) return;
        sb.append(label).append(":\n");
        for (HalfOpenTimestampOutputRangeStructure p : periods) {
            sb.append("  - ").append(p.getStartTime()).append(" -> ");
            sb.append(p.getEndTime() != null ? p.getEndTime() : OPEN_ENDED).append('\n');
        }
    }

    private static void appendReasonNames(StringBuilder sb,
            List<NaturalLanguageStringStructure> reasonNames) {
        if (reasonNames == null || reasonNames.isEmpty()) return;
        List<String> parts = new ArrayList<>();
        for (NaturalLanguageStringStructure r : reasonNames) {
            if (r == null || r.getValue() == null) continue;
            StringBuilder one = new StringBuilder().append('"').append(r.getValue()).append('"');
            if (r.getLang() != null) one.append(" (").append(r.getLang()).append(')');
            parts.add(one.toString());
        }
        if (!parts.isEmpty()) {
            sb.append("ReasonNames: ").append(String.join(", ", parts)).append('\n');
        }
    }

    private static void appendCsvLine(StringBuilder sb, String label, List<String> values) {
        if (values == null || values.isEmpty()) return;
        sb.append(label).append(": ").append(String.join(", ", values)).append('\n');
    }

    static void appendAffects(StringBuilder sb, AffectsScopeStructure affects, int indent) {
        if (affects == null) return;
        sb.append("\nAffects:\n");
        appendAffectsBody(sb, affects, indent);
    }

    // Extract the body of appendAffects so consequences can reuse it without
    // re-emitting the "Affects:" header.
    static void appendAffectsBody(StringBuilder sb, AffectsScopeStructure affects, int indent) {
        if (affects == null) return;
        appendAffectedOperators(sb, affects.getOperators(), indent);
        appendAffectedNetworks(sb, affects.getNetworks(), indent);
        appendAffectedStopPlaces(sb, affects.getStopPlaces(), indent);
        appendAffectedStopPoints(sb, affects.getStopPoints(), indent);
        appendAffectedVehicleJourneys(sb, affects.getVehicleJourneys(), indent);
        appendAffectedVehicles(sb,        affects.getVehicles(),        indent);
        appendAffectedPlaces(sb,          affects.getPlaces(),          indent);
        appendAffectedRoads(sb,           affects.getRoads(),            indent);
    }

    static void appendConsequences(StringBuilder sb, PtConsequencesStructure cs, int indent) {
        if (cs == null || cs.getConsequences().isEmpty()) return;
        sb.append("\nConsequences (").append(cs.getConsequences().size()).append("):\n");
        int idx = 1;
        for (PtConsequenceStructure c : cs.getConsequences()) {
            if (c == null) continue;
            appendConsequence(sb, c, idx++, indent);
        }
    }

    private static void appendConsequence(StringBuilder sb, PtConsequenceStructure c, int idx, int indent) {
        sb.append("  ".repeat(indent)).append('[').append(idx).append("] ");
        if (!c.getPeriods().isEmpty()) {
            java.util.List<String> ranges = new java.util.ArrayList<>();
            for (HalfOpenTimestampOutputRangeStructure p : c.getPeriods()) {
                ranges.add("period=" + p.getStartTime() + " -> "
                        + (p.getEndTime() != null ? p.getEndTime() : OPEN_ENDED));
            }
            sb.append(String.join(", ", ranges));
        } else {
            sb.append("(no period)");
        }
        sb.append('\n');

        int detailIndent = indent + 2;
        if (!c.getConditions().isEmpty()) {
            java.util.List<String> conds = new java.util.ArrayList<>();
            for (ServiceConditionEnumeration v : c.getConditions()) {
                if (v != null) conds.add(v.value());
            }
            if (!conds.isEmpty()) {
                sb.append("  ".repeat(detailIndent)).append("Condition: ").append(String.join(", ", conds)).append('\n');
            }
        }
        if (c.getSeverity() != null) {
            sb.append("  ".repeat(detailIndent)).append("Severity: ").append(c.getSeverity().value()).append('\n');
        }
        if (c.getBoarding() != null) {
            java.util.List<String> parts = new java.util.ArrayList<>();
            if (c.getBoarding().getArrivalBoardingActivity() != null) {
                parts.add("arrival=" + c.getBoarding().getArrivalBoardingActivity().value());
            }
            if (c.getBoarding().getDepartureBoardingActivity() != null) {
                parts.add("departure=" + c.getBoarding().getDepartureBoardingActivity().value());
            }
            if (!parts.isEmpty()) {
                sb.append("  ".repeat(detailIndent)).append("Boarding: ").append(String.join(", ", parts)).append('\n');
            }
        }
        if (c.getBlocking() != null && c.getBlocking().isJourneyPlanner() != null) {
            sb.append("  ".repeat(detailIndent)).append("Blocking: journeyPlanner=").append(c.getBlocking().isJourneyPlanner()).append('\n');
        }
        if (c.getDelays() != null && c.getDelays().getDelay() != null) {
            sb.append("  ".repeat(detailIndent)).append("Delay: ").append(c.getDelays().getDelay()).append('\n');
        }
        if (c.getCasualties() != null) {
            sb.append("  ".repeat(detailIndent)).append("Casualties: (details suppressed)\n");
        }
        if (c.getAffects() != null) {
            sb.append("  ".repeat(detailIndent)).append("Affects:\n");
            // appendAffects emits its own "Affects:\n" header — render the inner sub-trees only
            appendAffectsBody(sb, c.getAffects(), detailIndent + 1);
        }
    }

    private static void appendAffectedOperators(StringBuilder sb,
            AffectsScopeStructure.Operators ops, int indent) {
        if (ops == null) return;
        if (ops.getAllOperators() != null) {
            sb.append("  ".repeat(indent)).append("Operators: allOperators=true\n");
            return;
        }
        List<String> refs = new ArrayList<>();
        for (AffectedOperatorStructure op : ops.getAffectedOperators()) {
            if (op != null && op.getOperatorRef() != null) {
                refs.add(op.getOperatorRef().getValue());
            }
        }
        String line = summarise(refs, "Operators", indent);
        if (line != null) sb.append(line).append('\n');
    }

    private static void appendAffectedNetworks(StringBuilder sb,
            AffectsScopeStructure.Networks nets, int indent) {
        if (nets == null || nets.getAffectedNetworks().isEmpty()) return;
        // The AllLines flag is per-AffectedNetwork, but we surface a roll-up at the Networks header.
        // For clarity, surface allLines from the first affected network.
        AffectsScopeStructure.Networks.AffectedNetwork firstNet = nets.getAffectedNetworks().get(0);
        boolean allLines = firstNet != null && firstNet.getAllLines() != null;
        sb.append("  ".repeat(indent)).append("Networks: allLines=").append(allLines).append('\n');

        for (AffectsScopeStructure.Networks.AffectedNetwork net : nets.getAffectedNetworks()) {
            if (net == null) continue;
            String netRef = net.getNetworkRef() != null ? net.getNetworkRef().getValue() : "(no NetworkRef)";
            sb.append("  ".repeat(indent + 1)).append("Network ").append(netRef).append('\n');

            List<AffectedLineStructure> lines = net.getAffectedLines();
            List<String> lineRefs = new ArrayList<>();
            for (AffectedLineStructure l : lines) {
                if (l != null && l.getLineRef() != null) lineRefs.add(l.getLineRef().getValue());
            }
            String linesLine = summarise(lineRefs, "Lines", indent + 2);
            if (linesLine != null) sb.append(linesLine).append('\n');

            // Detail block for each shown line (up to LIST_LIMIT)
            int shown = Math.min(LIST_LIMIT, lines.size());
            for (int i = 0; i < shown; i++) {
                AffectedLineStructure l = lines.get(i);
                if (l == null || l.getLineRef() == null) continue;
                sb.append("  ".repeat(indent + 3)).append("Line ").append(l.getLineRef().getValue()).append('\n');
                appendAffectedLineDetail(sb, l, indent + 4);
            }
        }
    }

    private static void appendAffectedLineDetail(StringBuilder sb, AffectedLineStructure line, int indent) {
        if (line.getRoutes() != null) {
            List<String> routeRefs = new ArrayList<>();
            for (AffectedRouteStructure r : line.getRoutes().getAffectedRoutes()) {
                if (r != null && r.getRouteRef() != null) routeRefs.add(r.getRouteRef().getValue());
            }
            String l = summarise(routeRefs, "Routes", indent);
            if (l != null) sb.append(l).append('\n');
        }
        if (line.getStopPoints() != null) {
            List<String> stopRefs = new ArrayList<>();
            for (AffectedStopPointStructure sp : line.getStopPoints().getAffectedStopPoints()) {
                if (sp != null && sp.getStopPointRef() != null) stopRefs.add(sp.getStopPointRef().getValue());
            }
            String l = summarise(stopRefs, "Stops", indent);
            if (l != null) sb.append(l).append('\n');
        }
    }

    private static void appendAffectedStopPlaces(StringBuilder sb,
            AffectsScopeStructure.StopPlaces sps, int indent) {
        if (sps == null) return;
        List<String> refs = new ArrayList<>();
        for (AffectedStopPlaceStructure sp : sps.getAffectedStopPlaces()) {
            if (sp != null && sp.getStopPlaceRef() != null) refs.add(sp.getStopPlaceRef().getValue());
        }
        String line = summarise(refs, "StopPlaces", indent);
        if (line != null) sb.append(line).append('\n');
    }

    private static void appendAffectedStopPoints(StringBuilder sb,
            AffectsScopeStructure.StopPoints sps, int indent) {
        if (sps == null) return;
        List<String> refs = new ArrayList<>();
        for (AffectedStopPointStructure sp : sps.getAffectedStopPoints()) {
            if (sp != null && sp.getStopPointRef() != null) refs.add(sp.getStopPointRef().getValue());
        }
        String line = summarise(refs, "StopPoints", indent);
        if (line != null) sb.append(line).append('\n');
    }

    private static void appendAffectedVehicleJourneys(StringBuilder sb,
            AffectsScopeStructure.VehicleJourneys vjs, int indent) {
        if (vjs == null || vjs.getAffectedVehicleJourneies().isEmpty()) return;
        int total = vjs.getAffectedVehicleJourneies().size();
        sb.append("  ".repeat(indent)).append("VehicleJourneys (").append(total).append("):\n");
        int shown = Math.min(LIST_LIMIT, total);
        for (int i = 0; i < shown; i++) {
            appendVehicleJourneyDetail(sb, vjs.getAffectedVehicleJourneies().get(i), indent + 1);
        }
        if (total > shown) {
            sb.append("  ".repeat(indent + 1))
              .append("(and ").append(total - shown).append(" more)\n");
        }
    }

    private static void appendVehicleJourneyDetail(StringBuilder sb,
            AffectedVehicleJourneyStructure vj, int indent) {
        if (vj == null) return;
        String journeyRef = journeyRef(vj);
        sb.append("  ".repeat(indent)).append("Journey ").append(journeyRef);
        if (vj.getLineRef() != null) sb.append("  line=").append(vj.getLineRef().getValue());
        String origin = firstStopPointRef(vj.getOrigins());
        String dest   = firstStopPointRef(vj.getDestinations());
        if (origin != null || dest != null) {
            sb.append("  ");
            if (origin != null) sb.append("origin=").append(origin);
            if (origin != null && dest != null) sb.append(' ');
            if (dest != null)   sb.append("dest=").append(dest);
        }
        sb.append('\n');

        List<String> calls = new ArrayList<>();
        for (AffectedRouteStructure route : vj.getRoutes()) {
            if (route == null || route.getStopPoints() == null) continue;
            for (Object o : route.getStopPoints().getAffectedStopPointsAndLinkProjectionToNextStopPoints()) {
                if (o instanceof AffectedStopPointStructure asp && asp.getStopPointRef() != null) {
                    calls.add(callLabel(asp));
                }
            }
        }
        appendVerticalSummary(sb, calls, "Calls", indent + 1);
    }

    private static String firstStopPointRef(List<AffectedStopPointStructure> list) {
        if (list == null) return null;
        for (AffectedStopPointStructure sp : list) {
            if (sp != null && sp.getStopPointRef() != null) return sp.getStopPointRef().getValue();
        }
        return null;
    }

    private static String journeyRef(AffectedVehicleJourneyStructure vj) {
        if (!vj.getVehicleJourneyReves().isEmpty() && vj.getVehicleJourneyReves().get(0) != null) {
            return vj.getVehicleJourneyReves().get(0).getValue();
        }
        if (!vj.getDatedVehicleJourneyReves().isEmpty() && vj.getDatedVehicleJourneyReves().get(0) != null) {
            return vj.getDatedVehicleJourneyReves().get(0).getValue();
        }
        return "(no journey ref)";
    }

    private static String callLabel(AffectedStopPointStructure asp) {
        String ref = asp.getStopPointRef().getValue();
        java.util.List<RoutePointTypeEnumeration> conds = asp.getStopConditions();
        if (conds == null || conds.isEmpty()) return ref;
        java.util.List<String> condValues = new java.util.ArrayList<>();
        for (RoutePointTypeEnumeration c : conds) {
            if (c != null) condValues.add(c.value());
        }
        if (condValues.isEmpty()) return ref;
        return ref + " [" + String.join(",", condValues) + "]";
    }

    private static void appendAffectedVehicles(StringBuilder sb,
            AffectsScopeStructure.Vehicles vehicles, int indent) {
        if (vehicles == null) return;
        List<String> refs = new ArrayList<>();
        for (AffectedVehicleStructure v : vehicles.getAffectedVehicles()) {
            if (v != null && v.getVehicleRef() != null) refs.add(v.getVehicleRef().getValue());
        }
        String line = summarise(refs, "Vehicles", indent);
        if (line != null) sb.append(line).append('\n');
    }

    private static void appendAffectedPlaces(StringBuilder sb,
            AffectsScopeStructure.Places places, int indent) {
        if (places == null) return;
        List<String> refs = new ArrayList<>();
        for (AffectedPlaceStructure p : places.getAffectedPlaces()) {
            if (p != null && p.getPlaceRef() != null) refs.add(p.getPlaceRef());
        }
        String line = summarise(refs, "Places", indent);
        if (line != null) sb.append(line).append('\n');
    }

    private static void appendAffectedRoads(StringBuilder sb,
            AffectedRoadsStructure roads, int indent) {
        if (roads == null || roads.getAffectedRoads().isEmpty()) return;
        int total = roads.getAffectedRoads().size();
        sb.append("  ".repeat(indent))
          .append("Roads (").append(total).append("): (Datex2 details suppressed)\n");
    }

    /**
     * Renders a list as a header-then-vertical-block, with one item per line,
     * truncated to {@link #LIST_LIMIT} entries followed by an "...and X more"
     * marker. Used for Calls where each item carries inline annotations
     * (StopCondition values) that would make a comma-separated line unreadable.
     *
     * @param indent indent level of the "<Label> (N):" header line; items are
     *               indented one level deeper.
     */
    private static void appendVerticalSummary(StringBuilder sb, java.util.List<String> items, String label, int indent) {
        if (items == null || items.isEmpty()) return;
        int total = items.size();
        int shown = Math.min(LIST_LIMIT, total);
        sb.append("  ".repeat(indent)).append(label).append(" (").append(total).append("):\n");
        for (int i = 0; i < shown; i++) {
            sb.append("  ".repeat(indent + 1)).append(items.get(i)).append('\n');
        }
        if (total > shown) {
            sb.append("  ".repeat(indent + 1)).append("...and ").append(total - shown).append(" more\n");
        }
    }

    /**
     * Produces "<Label> (N): a, b, c and X more" — single source of truth
     * for the truncation rule. Returns null for an empty/null list.
     *
     * @param indent number of two-space indents to prefix
     */
    static String summarise(List<String> refs, String label, int indent) {
        if (refs == null || refs.isEmpty()) return null;
        int total = refs.size();
        int shown = Math.min(LIST_LIMIT, total);
        String head = String.join(", ", refs.subList(0, shown));
        StringBuilder sb = new StringBuilder();
        sb.append("  ".repeat(indent));
        sb.append(label).append(" (").append(total).append("): ").append(head);
        if (total > shown) {
            sb.append(" and ").append(total - shown).append(" more");
        }
        return sb.toString();
    }
}