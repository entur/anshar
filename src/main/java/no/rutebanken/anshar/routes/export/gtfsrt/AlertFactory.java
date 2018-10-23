/*
 * Copyright (C) 2011 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package no.rutebanken.anshar.routes.export.gtfsrt;

import com.google.transit.realtime.GtfsRealtime.*;
import com.google.transit.realtime.GtfsRealtime.Alert.Cause;
import com.google.transit.realtime.GtfsRealtime.Alert.Effect;
import com.google.transit.realtime.GtfsRealtime.TranslatedString.Translation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import uk.org.siri.siri20.*;
import uk.org.siri.siri20.AffectedVehicleJourneyStructure.Calls;
import uk.org.siri.siri20.AffectsScopeStructure.Operators;
import uk.org.siri.siri20.AffectsScopeStructure.StopPoints;
import uk.org.siri.siri20.AffectsScopeStructure.VehicleJourneys;

import java.util.List;

@Component
public class AlertFactory {

    private static final Logger _log = LoggerFactory.getLogger(AlertFactory.class);

    public Alert createAlertFromSituation(PtSituationElement ptSituation) {

        Alert.Builder alert = Alert.newBuilder();

        handleDescriptions(ptSituation, alert);
        handleOtherFields(ptSituation, alert);
        handleReasons(ptSituation, alert);
        handleAffects(ptSituation, alert);
        handleConsequences(ptSituation, alert);

        return alert.build();
    }

    private void handleDescriptions(PtSituationElement ptSituation,
                                    Alert.Builder serviceAlert) {

        TranslatedString summary = translation(ptSituation.getSummaries());
        if (summary != null)
            serviceAlert.setHeaderText(summary);

        TranslatedString description = translation(ptSituation.getDescriptions());
        if (description != null)
            serviceAlert.setDescriptionText(description);
    }

    private void handleOtherFields(PtSituationElement ptSituation,
                                   Alert.Builder serviceAlert) {

        HalfOpenTimestampOutputRangeStructure window = ptSituation.getPublicationWindow();
        if (window != null) {
            TimeRange.Builder range = TimeRange.newBuilder();
            if (window.getStartTime() != null)
                range.setStart(window.getStartTime().toInstant().getEpochSecond());
            if (window.getEndTime() != null)
                range.setEnd(window.getEndTime().toInstant().getEpochSecond());
            if (range.hasStart() || range.hasEnd())
                serviceAlert.addActivePeriod(range);
        }
    }

    private void handleReasons(PtSituationElement ptSituation,
                               Alert.Builder serviceAlert) {

        Cause cause = getReasonAsCause(ptSituation);
        if (cause != null)
            serviceAlert.setCause(cause);
    }

    private Cause getReasonAsCause(PtSituationElement ptSituation) {
        if (ptSituation.getEnvironmentReason() != null)
            return Cause.WEATHER;
        if (ptSituation.getEquipmentReason() != null) {
            switch (ptSituation.getEquipmentReason()) {
                case CONSTRUCTION_WORK:
                    return Cause.CONSTRUCTION;
                case CLOSED_FOR_MAINTENANCE:
                case MAINTENANCE_WORK:
                case EMERGENCY_ENGINEERING_WORK:
                case LATE_FINISH_TO_ENGINEERING_WORK:
                case REPAIR_WORK:
                    return Cause.MAINTENANCE;
                default:
                    return Cause.TECHNICAL_PROBLEM;
            }
        }
        if (ptSituation.getPersonnelReason() != null) {
            switch (ptSituation.getPersonnelReason()) {
                case INDUSTRIAL_ACTION:
                case UNOFFICIAL_INDUSTRIAL_ACTION:
                    return Cause.STRIKE;
            }
            return Cause.OTHER_CAUSE;
        }

        /*
         * There are really so many possibilities here that it's tricky to translate
         * them all
         */
        if (ptSituation.getMiscellaneousReason() != null) {
            switch (ptSituation.getMiscellaneousReason()) {
                case ACCIDENT:
                case COLLISION:
                    return Cause.ACCIDENT;
                case DEMONSTRATION:
                case MARCH:
                    return Cause.DEMONSTRATION;
                case PERSON_ILL_ON_VEHICLE:
                case FATALITY:
                    return Cause.MEDICAL_EMERGENCY;
                case POLICE_REQUEST:
                case BOMB_ALERT:
                case CIVIL_EMERGENCY:
                case EMERGENCY_SERVICES:
                case EMERGENCY_SERVICES_CALL:
                    return Cause.POLICE_ACTIVITY;
            }
        }

        return null;
    }

    /****
     * Affects
     ****/

    private void handleAffects(PtSituationElement ptSituation,
                               Alert.Builder serviceAlert) {

        AffectsScopeStructure affectsStructure = ptSituation.getAffects();

        if (affectsStructure == null)
            return;

        Operators operators = affectsStructure.getOperators();

        if (operators != null && !operators.getAffectedOperators().isEmpty()) {

            for (AffectedOperatorStructure operator : operators.getAffectedOperators()) {
                OperatorRefStructure operatorRef = operator.getOperatorRef();
                if (operatorRef == null || operatorRef.getValue() == null)
                    continue;
                String agencyId = operatorRef.getValue();
                EntitySelector.Builder selector = EntitySelector.newBuilder();
                selector.setAgencyId(agencyId);
                serviceAlert.addInformedEntity(selector);
            }
        }

        StopPoints stopPoints = affectsStructure.getStopPoints();

        if (stopPoints != null && !stopPoints.getAffectedStopPoints().isEmpty()) {

            for (AffectedStopPointStructure stopPoint : stopPoints.getAffectedStopPoints()) {
                StopPointRef stopRef = stopPoint.getStopPointRef();
                if (stopRef == null || stopRef.getValue() == null)
                    continue;
                String stopId = stopRef.getValue();
                EntitySelector.Builder selector = EntitySelector.newBuilder();
                selector.setStopId(stopId);
                serviceAlert.addInformedEntity(selector);
            }
        }

        VehicleJourneys vjs = affectsStructure.getVehicleJourneys();
        if (vjs != null
                && !vjs.getAffectedVehicleJourneies().isEmpty()) {

            for (AffectedVehicleJourneyStructure vj : vjs.getAffectedVehicleJourneies()) {

                EntitySelector.Builder selector = EntitySelector.newBuilder();

                if (vj.getLineRef() != null) {
                    String routeId = vj.getLineRef().getValue();
                    selector.setRouteId(routeId);
                }

                List<VehicleJourneyRef> tripRefs = vj.getVehicleJourneyReves();
                Calls stopRefs = vj.getCalls();

                boolean hasTripRefs = !tripRefs.isEmpty();
                boolean hasStopRefs = stopRefs != null && !stopRefs.getCalls().isEmpty();

                if (!(hasTripRefs || hasStopRefs)) {
                    if (selector.hasRouteId())
                        serviceAlert.addInformedEntity(selector);
                } else if (hasTripRefs && hasStopRefs) {
                    for (VehicleJourneyRef vjRef : vj.getVehicleJourneyReves()) {
                        String tripId = vjRef.getValue();
                        TripDescriptor.Builder tripDescriptor = TripDescriptor.newBuilder();
                        tripDescriptor.setTripId(tripId);
                        selector.setTrip(tripDescriptor);
                        for (AffectedCallStructure call : stopRefs.getCalls()) {
                            String stopId = call.getStopPointRef().getValue();
                            selector.setStopId(stopId);
                            serviceAlert.addInformedEntity(selector);
                        }
                    }
                } else if (hasTripRefs) {
                    for (VehicleJourneyRef vjRef : vj.getVehicleJourneyReves()) {
                        String tripId = vjRef.getValue();
                        TripDescriptor.Builder tripDescriptor = TripDescriptor.newBuilder();
                        tripDescriptor.setTripId(tripId);
                        selector.setTrip(tripDescriptor);
                        serviceAlert.addInformedEntity(selector);
                    }
                } else {
                    for (AffectedCallStructure call : stopRefs.getCalls()) {
                        String stopId = call.getStopPointRef().getValue();
                        selector.setStopId(stopId);
                        serviceAlert.addInformedEntity(selector);
                    }
                }
            }
        }
    }

    private void handleConsequences(PtSituationElement ptSituation,
                                    Alert.Builder serviceAlert) {

        PtConsequencesStructure consequences = ptSituation.getConsequences();

        if (consequences == null || consequences.getConsequences() == null)
            return;

        for (PtConsequenceStructure consequence : consequences.getConsequences()) {
            if (consequence.getConditions() != null && !consequence.getConditions().isEmpty())
                serviceAlert.setEffect(getConditionAsEffect(consequence.getConditions().get(0)));
        }
    }

    private Effect getConditionAsEffect(ServiceConditionEnumeration condition) {
        switch (condition) {

            case CANCELLED:
            case NO_SERVICE:
                return Effect.NO_SERVICE;

            case DELAYED:
                return Effect.SIGNIFICANT_DELAYS;

            case DIVERTED:
                return Effect.DETOUR;

            case ADDITIONAL_SERVICE:
            case EXTENDED_SERVICE:
            case SHUTTLE_SERVICE:
            case SPECIAL_SERVICE:
            case REPLACEMENT_SERVICE:
                return Effect.ADDITIONAL_SERVICE;

            case DISRUPTED:
            case INTERMITTENT_SERVICE:
            case SHORT_FORMED_SERVICE:
                return Effect.REDUCED_SERVICE;

            case ALTERED:
            case ARRIVES_EARLY:
            case REPLACEMENT_TRANSPORT:
            case SPLITTING_TRAIN:
                return Effect.MODIFIED_SERVICE;

            case ON_TIME:
            case FULL_LENGTH_SERVICE:
            case NORMAL_SERVICE:
                return Effect.OTHER_EFFECT;

            case UNDEFINED_SERVICE_INFORMATION:
            case UNKNOWN:
                return Effect.UNKNOWN_EFFECT;

            default:
                _log.warn("unknown condition: " + condition);
                return Effect.UNKNOWN_EFFECT;
        }
    }

    private TranslatedString translation(List<DefaultedTextStructure> textLists) {
        if (textLists == null || textLists.isEmpty()) {
            return null;
        }
        DefaultedTextStructure text = textLists.get(0);
        String value = text.getValue();
        if (value == null)
            return null;

        value = value.replaceAll("\\s+", " ");

        Translation.Builder translation = Translation.newBuilder();
        translation.setText(value);
        if (text.getLang() != null)
            translation.setLanguage(text.getLang());

        TranslatedString.Builder tsBuilder = TranslatedString.newBuilder();
        tsBuilder.addTranslation(translation);
        return tsBuilder.build();
    }
}