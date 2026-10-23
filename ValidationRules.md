# Anshar SIRI validation rules

Last updated: 23 October 2026

Data delivered to Entur is checked against the SIRI 2.1 XML schema and the Norwegian SIRI profile. This page lists every profile rule, grouped by data type, so you can check your data before sending it.

## How to read the rules

- **Element** is the SIRI element the rule applies to. A rule on an element you don't send only applies if the element is required.
- **Requirement** is what the content must satisfy. Element names and allowed values are written exactly as in SIRI.
- **Severity** tells you how serious a violation is. Fix `FATAL_ERROR` first (wrong references, impossible times), then `ERROR` (values outside the allowed set), then `WARNING` (missing recommended fields, implausible values).
- **Reference formats.** NeTEx references use `CODESPACE:Type:ID`, for example `ATB:Line:2_11`. Stop references must be NSR quays (`NSR:Quay:ID`) that exist in the National Stop Register.
- **Cancellations.** Time-order checks skip calls marked `Cancellation=true` or with status `cancelled` or `missed`.

## ET – Estimated Timetable

Each `EstimatedVehicleJourney` needs line and journey references in NeTEx format, and calls numbered 1, 2, 3… at NSR quays, with times that never run backwards. Extra journeys (`ExtraJourney=true`) have additional requirements.

### Delivery and journey

| Element | Requirement | Severity |
| --- | --- | --- |
| `ServiceDelivery` | Contains only `EstimatedTimetableDelivery`, with no SX or VM deliveries mixed in | WARNING |
| `EstimatedVehicleJourney` | Contains `LineRef`, `DataSource` and `IsCompleteStopSequence`. Unless it is an extra journey, it also needs `FramedVehicleJourneyRef/DatedVehicleJourneyRef` or `DatedVehicleJourneyRef` | WARNING |
| `IsCompleteStopSequence` | `true`: the journey lists all its stops | WARNING |
| `LineRef` | `CODESPACE:Line:ID` | FATAL\_ERROR |
| `ExternalLineRef` | `CODESPACE:Line:ID`, and a real line (not a placeholder such as `NSB:Line:xx`) | FATAL\_ERROR |
| `OperatorRef` | `CODESPACE:Operator:ID` | FATAL\_ERROR |
| `DatedVehicleJourneyRef` | `CODESPACE:DatedServiceJourney:ID` | FATAL\_ERROR |
| `FramedVehicleJourneyRef` | `DataFrameRef` is a date in `yyyy-MM-dd`; `DatedVehicleJourneyRef` is `CODESPACE:ServiceJourney:ID` | FATAL\_ERROR |
| `Occupancy` | One of `unknown`, `manySeatsAvailable`, `seatsAvailable`, `fewSeatsAvailable`, `standingAvailable`, `full`, `notAcceptingPassengers` | ERROR |
| `ExtraJourney` | Has a value (`true` or `false`) when present | ERROR |

### Extra journeys (`ExtraJourney=true`)

| Element | Requirement | Severity |
| --- | --- | --- |
| `EstimatedVehicleJourney` | Contains `EstimatedVehicleJourneyCode`, `RouteRef`, `GroupOfLinesRef` and `ExternalLineRef` | WARNING |
| `VehicleMode` | Present, and one of `air`, `bus`, `coach`, `ferry`, `metro`, `rail`, `taxi`, `tram` | ERROR |
| `RouteRef` | `CODESPACE:Route:ID` | ERROR (WARNING if missing) |
| `GroupOfLinesRef` | `CODESPACE:Network:ID` | ERROR (WARNING if missing) |
| `EstimatedVehicleJourneyCode` | `CODESPACE:ServiceJourney:ID` | ERROR |
| `EstimatedCall/DestinationDisplay` | Present on every estimated call | ERROR |
| `StopPointRef` (every call) | The stop serves the journey's `VehicleMode`, for example a rail journey calls at rail quays | FATAL\_ERROR |

### Calls

| Element | Requirement | Severity |
| --- | --- | --- |
| `Order` | Set on every `RecordedCall` and `EstimatedCall`, counting 1, 2, 3… without gaps from the first recorded call through the last estimated call | ERROR if missing, FATAL\_ERROR if out of sequence |
| `RecordedCall` | Contains `StopPointRef`, `Order` and `AimedArrivalTime` or `AimedDepartureTime`. An aimed arrival needs `ExpectedArrivalTime` or `ActualArrivalTime`; an aimed departure needs `ExpectedDepartureTime` or `ActualDepartureTime` | WARNING |
| `EstimatedCall` | Contains `StopPointRef`, `Order`, `AimedArrivalTime` or `AimedDepartureTime`, and `ExpectedArrivalTime` or `ExpectedDepartureTime` | WARNING |
| `RecordedCall`, `EstimatedCall` | Do not use `VisitNumber`; `EstimatedCall` also not `EarliestExpectedDepartureTime` | WARNING |
| `StopPointRef` | `NSR:Quay:ID` that exists in the National Stop Register | FATAL\_ERROR |
| `ArrivalBoardingActivity` | One of `alighting`, `noAlighting`, `passThru` | ERROR |
| `DepartureBoardingActivity` | One of `boarding`, `noBoarding`, `passThru` | ERROR |
| `ArrivalStatus` | One of `arrived`, `cancelled`, `missed`, `early`, `onTime`, `delayed` | ERROR |
| `RecordedCall/DepartureStatus` | One of `departed`, `cancelled`, `missed`, `early`, `onTime`, `delayed` | ERROR |
| `EstimatedCall/DepartureStatus` | One of `cancelled`, `missed`, `onTime`, `delayed` | ERROR |

### Times

| Element | Requirement | Severity |
| --- | --- | --- |
| Times within a call | Valid timestamps. Arrival is not after departure for aimed times (all calls), and for expected and actual times (estimated calls) | FATAL\_ERROR |
| Times along the journey | Using the best known time (actual, else expected, else aimed): no departure before arrival at the same stop, and no arrival before departure from the previous stop | FATAL\_ERROR |
| Delay | Expected or actual time is less than 24 hours from the aimed time | WARNING |
| Speed | Travel between consecutive stops is at most 300 km/h, or 3000 km/h between two airports | WARNING |
| When the update is sent | The journey's first aimed time is at most 7 days ahead, and its last call is no more than 20 minutes in the past | WARNING |

## VM – Vehicle Monitoring

Each `MonitoredVehicleJourney` needs line and journey references in NeTEx format, a valid WGS84 position and a `Delay`, and should leave out the elements listed below. Elements are inside `VehicleActivity/MonitoredVehicleJourney` unless noted.

| Element | Requirement | Severity |
| --- | --- | --- |
| `ServiceDelivery` | Contains only `VehicleMonitoringDelivery`, with no ET or SX deliveries mixed in | WARNING |
| `MonitoredVehicleJourney` | Contains `LineRef`, `DataSource`, `VehicleLocation`, `VehicleRef` and `Delay` | WARNING |
| `MonitoredVehicleJourney` | Does not use `JourneyPatternRef`, `Via`, `JourneyNote`, `MonitoringError`, `InPanic`, `BlockRef`, `PreviousCalls`, `OnwardCalls`, `ProgressStatus`, `CourseOfJourneyRef` or `LocationRecordedAtTime` | WARNING |
| `LineRef` | `CODESPACE:Line:ID` | FATAL\_ERROR |
| `OperatorRef` | `CODESPACE:Operator:ID` | FATAL\_ERROR |
| `VehicleJourneyRef` | `CODESPACE:DatedServiceJourney:ID` | FATAL\_ERROR |
| `FramedVehicleJourneyRef` | `DataFrameRef` is a date in `yyyy-MM-dd`; `DatedVehicleJourneyRef` is `CODESPACE:ServiceJourney:ID` | FATAL\_ERROR |
| `VehicleLocation` | Has `Longitude` and `Latitude` (or `Coordinates`). Longitude is within −180 to 180 and latitude within −90 to 90, and neither is 0 | FATAL\_ERROR |
| `VehicleLocation/@srsName` | If set, `WGS84` or `EPSG:4326` | ERROR |
| `Delay` | An ISO 8601 duration, for example `PT2M30S` or `-PT45S` | ERROR |
| `VehicleMode` | One of `air`, `bus`, `coach`, `ferry`, `metro`, `rail`, `taxi`, `tram` | ERROR |
| `VehicleStatus` | One of `assigned`, `atOrigin`, `cancelled`, `completed`, `inProgress`, `offRoute` | ERROR |
| `Occupancy` | One of `unknown`, `manySeatsAvailable`, `seatsAvailable`, `fewSeatsAvailable`, `standingAvailable`, `full`, `notAcceptingPassengers` | ERROR |
| `MonitoredCall/StopPointRef` | `NSR:Quay:ID` that exists in the National Stop Register | FATAL\_ERROR |
| `MonitoredCall` | Does not use `LatestExpectedArrivalTime`, `EarliestExpectedDepartureTime` or `DepartureStopAssignment` | WARNING |
| `VehicleActivity/ValidUntilTime` | A valid timestamp (ERROR). At most 24 hours after `RecordedAtTime`, and not more than 10 minutes in the past when sent (WARNING) | ERROR / WARNING |

## SX – Situation Exchange

Each `PtSituationElement` needs a situation number, a validity period and plain-text messages, and must say what it affects using NeTEx and NSR references. Elements are inside `SituationExchangeDelivery/Situations/PtSituationElement` unless noted.

### Situation

| Element | Requirement | Severity |
| --- | --- | --- |
| `ServiceDelivery` | Contains only `SituationExchangeDelivery`, with no ET or VM deliveries mixed in | WARNING |
| `PtSituationElement` | Contains `CreationTime`, `ParticipantRef`, `SituationNumber`, `Source`, `Progress`, `ValidityPeriod` and `ReportType`. Unless `Progress` is `closed`, it also contains `Summary` and `Affects` | WARNING |
| `PtSituationElement` | Does not use `UnknownReason`, `PublicationWindow`, `ScopeType`, `Audience`, `Keywords`, `Detail` or `Consequences` | WARNING |
| `ParticipantRef` | Your codespace: exactly 3 characters, for example `ATB` | ERROR |
| `SituationNumber` | `CODESPACE:SituationNumber:ID` | FATAL\_ERROR |
| `Source/SourceType` | `directReport` | ERROR |
| `Progress` | `open` or `closed` | ERROR |
| `ReportType` | `general` or `incident` | ERROR |
| `ValidityPeriod` | Not empty, with a valid `StartTime` | WARNING if empty, FATAL\_ERROR if `StartTime` is missing or invalid |
| `ValidityPeriod/EndTime` (closed situations) | Required, a valid timestamp, and at least 5 hours after `StartTime` | FATAL\_ERROR if missing or invalid, ERROR if too early |
| `ValidityPeriod/EndTime` (open situations) | If set: not in the past, and at most one year after `StartTime` | WARNING |
| `Summary`, `Description`, `Advice` | Not empty, and no HTML tags. If an element is repeated for several languages, each copy needs a unique `xml:lang` | FATAL\_ERROR (WARNING for HTML) |
| `Summary` | At most 160 characters | WARNING |
| `InfoLinks/InfoLink/Uri` | A valid URL | WARNING |

### Affects

Paths in this table are inside `PtSituationElement/Affects`.

| Element | Requirement | Severity |
| --- | --- | --- |
| `Networks/AffectedNetwork/VehicleMode` | One of `all`, `air`, `bus`, `coach`, `funicular`, `metro`, `rail`, `taxi`, `telecabin`, `tram`, `water`, `selfDrive` | ERROR |
| `AffectedNetwork/AffectedOperator/OperatorRef` | `CODESPACE:Operator:ID` | FATAL\_ERROR |
| `AffectedNetwork/AffectedLine/LineRef` | `CODESPACE:Line:ID` | FATAL\_ERROR |
| `AffectedNetwork/AffectedLine` | Does not use `AffectedOperator`; put the operator on `AffectedNetwork` instead | WARNING |
| `AffectedLine/Routes/AffectedRoute/RouteRef` | If set, `CODESPACE:Route:ID` | FATAL\_ERROR |
| `StopPointRef` (in `StopPoints/AffectedStopPoint`, also under an `AffectedRoute`) | `NSR:Quay:ID` or `NSR:StopPlace:ID` that exists in the National Stop Register | FATAL\_ERROR |
| `AffectedStopPoint/StopCondition` | One of `exceptionalStop`, `destination`, `notStopping`, `requestStop`, `startPoint`, `stop` | ERROR |
| `StopPlaces/AffectedStopPlace/StopPlaceRef` | `NSR:StopPlace:ID` or `NSR:Quay:ID` that exists in the National Stop Register | FATAL\_ERROR |
| `AffectedStopPlace/AffectedComponents/AffectedComponent` | `ComponentType` is one of `accessSpace`, `boardingPosition`, `entrance`, `quay` (ERROR). For `quay`, `ComponentRef` is an existing `NSR:Quay:ID` (FATAL\_ERROR). `AccessFeatureType`, if set, is one of `escalator`, `lift`, `narrowEntrance`, `ramp`, `stairs` (ERROR) | ERROR / FATAL\_ERROR |
| `AccessibilityAssessment` | `MobilityImpairedAccess` is `true` or `false`, and `Limitations/AccessibilityLimitation` gives `WheelchairAccess`, `StepFreeAccess`, `EscalatorFreeAccess` and `LiftFreeAccess` as `true`, `false` or `unknown` | FATAL\_ERROR |
| `VehicleJourneys/AffectedVehicleJourney/LineRef` | `CODESPACE:Line:ID` | FATAL\_ERROR |
| `AffectedVehicleJourney/Operator/OperatorRef` | `CODESPACE:Operator:ID` | FATAL\_ERROR |
| `AffectedVehicleJourney/VehicleJourneyRef` | `CODESPACE:ServiceJourney:ID` | FATAL\_ERROR |
| `AffectedVehicleJourney/FramedVehicleJourneyRef` | `DataFrameRef` is a date in `yyyy-MM-dd` (FATAL\_ERROR); `DatedVehicleJourneyRef` is `CODESPACE:ServiceJourney:ID` (ERROR) | FATAL\_ERROR / ERROR |

## FM – Facility Monitoring

FM has two rules: each `FacilityCondition` must reference a parking facility in NeTEx format and have a sensible validity period. Elements are inside `FacilityMonitoringDelivery/FacilityCondition`.

| Element | Requirement | Severity |
| --- | --- | --- |
| `FacilityRef` | `CODESPACE:Parking:ID` | FATAL\_ERROR |
| `ValidityPeriod` | Not empty, with a valid `StartTime` | WARNING if empty, FATAL\_ERROR if `StartTime` is missing or invalid |
| `ValidityPeriod/EndTime` | If set: not in the past, and at most one year after `StartTime` | WARNING |
