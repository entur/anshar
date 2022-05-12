package no.rutebanken.anshar.routes.validation.validators.et;

import no.rutebanken.anshar.routes.siri.processor.routedata.StopsUtil;
import no.rutebanken.anshar.routes.siri.processor.routedata.TooFastException;
import no.rutebanken.anshar.routes.validation.validators.ProfileValidationEventOrList;
import no.rutebanken.anshar.routes.validation.validators.SiriObjectValidator;
import no.rutebanken.anshar.routes.validation.validators.Validator;
import no.rutebanken.anshar.subscription.SiriDataType;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.xerces.dom.NodeImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.w3c.dom.Node;
import uk.org.siri.siri20.EstimatedCall;
import uk.org.siri.siri20.EstimatedTimetableDeliveryStructure;
import uk.org.siri.siri20.EstimatedVehicleJourney;
import uk.org.siri.siri20.EstimatedVersionFrameStructure;
import uk.org.siri.siri20.RecordedCall;
import uk.org.siri.siri20.Siri;
import uk.org.siri.siri20.StopPointRef;

import javax.xml.bind.ValidationEvent;
import java.time.ZonedDateTime;
import java.util.List;

import static no.rutebanken.anshar.routes.siri.transformer.impl.OutboundIdAdapter.getMappedId;

@SuppressWarnings("unchecked")
@Validator(profileName = "norway", targetType = SiriDataType.ESTIMATED_TIMETABLE)
@Component
public class SaneSpeedValidator extends SiriObjectValidator {
    private Logger logger = LoggerFactory.getLogger(SaneSpeedValidator.class);

    public static final int SANE_SPEED_LIMIT = 300;

    private static final Node DUMMY_NODE = new NodeImpl() {
        @Override
        public short getNodeType() {
            return 0;
        }

        @Override
        public String getNodeName() {
            return "Siri";
        }
    };

    private String path = "Siri";

    @Override
    public String getXpath() {
        return path;
    }

    @Override
    public ValidationEvent isValid(Node node) {
        return null;
    }

    @Override
    public ValidationEvent isValid(Siri siri) {
        return validateSiriObject(siri);
    }

    private ProfileValidationEventOrList validateSiriObject(Siri siri) {
        ProfileValidationEventOrList events = new ProfileValidationEventOrList();
        if (siri != null && siri.getServiceDelivery() != null) {

            List<EstimatedTimetableDeliveryStructure> etDeliveries = siri.getServiceDelivery().getEstimatedTimetableDeliveries();
            if (etDeliveries != null) {
                for (EstimatedTimetableDeliveryStructure etDelivery : etDeliveries) {
                    List<EstimatedVersionFrameStructure> estimatedJourneyVersionFrames = etDelivery.getEstimatedJourneyVersionFrames();
                    for (EstimatedVersionFrameStructure estimatedJourneyVersionFrame : estimatedJourneyVersionFrames) {

                        List<EstimatedVehicleJourney> estimatedVehicleJourneies = estimatedJourneyVersionFrame
                            .getEstimatedVehicleJourneies();

                        for (EstimatedVehicleJourney estimatedVehicleJourney : estimatedVehicleJourneies) {
//                            if (isTrue(estimatedVehicleJourney.isExtraJourney())) {
                                final EstimatedVehicleJourney.RecordedCalls recordedCalls = estimatedVehicleJourney
                                    .getRecordedCalls();
                                if (recordedCalls != null && recordedCalls.getRecordedCalls() != null) {
                                    final List<RecordedCall> calls = recordedCalls.getRecordedCalls();
                                    for (
                                        int i = 0; i < calls.size() - 1; i++
                                    ) {
                                        final RecordedCall thisCall = calls.get(i);
                                        final RecordedCall nextCall = calls.get(i + 1);

                                        if (thisCall.getStopPointRef() != null &&
                                            nextCall.getStopPointRef() != null) {
                                            final String fromStop = getMappedId(thisCall
                                                .getStopPointRef()
                                                .getValue());
                                            final String toStop = getMappedId(nextCall
                                                .getStopPointRef()
                                                .getValue());

                                            try {
                                                validate(estimatedVehicleJourney,
                                                        fromStop,
                                                    toStop,
                                                    getTimes(thisCall, nextCall)
                                                );
                                            }
                                            catch (TooFastException e) {
                                                events.addEvent(createCustomFieldEvent(DUMMY_NODE,
                                                    e.getMessage(),
                                                    ValidationEvent.FATAL_ERROR
                                                ));
                                            }
                                        }
                                    }
                                }
                                final EstimatedVehicleJourney.EstimatedCalls estimatedCalls = estimatedVehicleJourney
                                    .getEstimatedCalls();
                                if (estimatedCalls != null && estimatedCalls.getEstimatedCalls() != null) {
                                    final List<EstimatedCall> calls = estimatedCalls.getEstimatedCalls();
                                    for (
                                        int i = 0; i < calls.size() - 1; i++
                                    ) {
                                        final EstimatedCall thisCall = calls.get(i);
                                        final EstimatedCall nextCall = calls.get(i + 1);

                                        final StopPointRef thisStop = thisCall.getStopPointRef();
                                        final StopPointRef nextStop = nextCall.getStopPointRef();
                                        if (thisStop != null && nextStop != null) {

                                            try {
                                                validate(
                                                    estimatedVehicleJourney,
                                                    getMappedId(thisStop.getValue()),
                                                    getMappedId(nextStop.getValue()),
                                                    getTimes(thisCall, nextCall)
                                                );
                                            }
                                            catch (TooFastException e) {
                                                events.addEvent(createCustomFieldEvent(DUMMY_NODE,
                                                    e.getMessage(),
                                                    ValidationEvent.FATAL_ERROR
                                                ));
                                            }
                                        }
                                    }
                                }
//                            }
                        }
                    }
                }
            }
        }
        return events;
    }

    private void validate(
        EstimatedVehicleJourney estimatedVehicleJourney,
        String fromStop, String toStop,
        Pair<ZonedDateTime, ZonedDateTime> times
    ) throws TooFastException {

        final ZonedDateTime fromTime = times.getLeft();
        final ZonedDateTime toTime = times.getRight();

        if (fromTime != null && toTime != null &&
            toTime.isAfter(fromTime)) {
            isSaneSpeed(estimatedVehicleJourney, fromStop, toStop, fromTime, toTime);
        }
    }

    private void isSaneSpeed(
        EstimatedVehicleJourney estimatedVehicleJourney,
        String fromStop, String toStop, ZonedDateTime fromTime, ZonedDateTime toTime
    ) throws TooFastException {
        final double kph = StopsUtil.calculateSpeedKph(fromStop, toStop, fromTime, toTime);


        if (kph > SANE_SPEED_LIMIT) {
            throw new TooFastException(estimatedVehicleJourney, fromStop, toStop, fromTime, toTime);
        } else {
            logger.debug("Calculated speed between {} and {}: {}", fromStop, toStop, kph);
        }
    }


    private Pair<ZonedDateTime, ZonedDateTime> getTimes(
        RecordedCall thisCall, RecordedCall nextCall
    ) {
        ZonedDateTime fromTime;
        ZonedDateTime toTime;

        // Only use comparable times
        if (thisCall.getActualDepartureTime() != null && nextCall.getActualArrivalTime() != null) {
            fromTime = thisCall.getActualDepartureTime();
            toTime = nextCall.getActualArrivalTime();
        } else if (thisCall.getExpectedDepartureTime() != null && nextCall.getExpectedArrivalTime() != null) {
            fromTime = thisCall.getExpectedDepartureTime();
            toTime = nextCall.getExpectedArrivalTime();
        } else {
            fromTime = thisCall.getAimedDepartureTime();
            toTime = nextCall.getAimedArrivalTime();
        }

        return Pair.of(fromTime, toTime);
    }

    private Pair<ZonedDateTime, ZonedDateTime> getTimes(
        EstimatedCall thisCall, EstimatedCall nextCall
    ) {
        ZonedDateTime fromTime;
        ZonedDateTime toTime;

        // Only use comparable times
        if (thisCall.getExpectedDepartureTime() != null && nextCall.getExpectedArrivalTime() != null) {
            fromTime = thisCall.getExpectedDepartureTime();
            toTime = nextCall.getExpectedArrivalTime();
        } else {
            fromTime = thisCall.getAimedDepartureTime();
            toTime = nextCall.getAimedArrivalTime();
        }

        return Pair.of(fromTime, toTime);
    }
}
