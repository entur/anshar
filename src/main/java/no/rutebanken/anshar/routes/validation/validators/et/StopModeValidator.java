package no.rutebanken.anshar.routes.validation.validators.et;

import no.rutebanken.anshar.routes.siri.processor.routedata.InvalidVehicleModeForStopException;
import no.rutebanken.anshar.routes.siri.processor.routedata.StopsUtil;
import no.rutebanken.anshar.routes.validation.validators.SiriObjectValidator;
import no.rutebanken.anshar.routes.validation.validators.Validator;
import no.rutebanken.anshar.subscription.SiriDataType;
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
import uk.org.siri.siri20.VehicleModesEnumeration;

import javax.xml.bind.ValidationEvent;
import java.util.List;

import static no.rutebanken.anshar.routes.siri.transformer.impl.OutboundIdAdapter.getMappedId;
import static org.apache.commons.lang3.BooleanUtils.isTrue;

@SuppressWarnings("unchecked")
@Validator(profileName = "norway", targetType = SiriDataType.ESTIMATED_TIMETABLE)
@Component
public class StopModeValidator extends SiriObjectValidator {
    private Logger logger = LoggerFactory.getLogger(StopModeValidator.class);

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
        try {
            validateSiriObject(siri);
        }
        catch (InvalidVehicleModeForStopException e) {
            return createCustomFieldEvent(DUMMY_NODE, e.getMessage(), ValidationEvent.FATAL_ERROR);
        }
        return null;
    }

    private ValidationEvent validateSiriObject(Siri siri)
        throws InvalidVehicleModeForStopException {
        if (siri != null && siri.getServiceDelivery() != null) {

            List<EstimatedTimetableDeliveryStructure> etDeliveries = siri.getServiceDelivery().getEstimatedTimetableDeliveries();
            if (etDeliveries != null) {
                for (EstimatedTimetableDeliveryStructure etDelivery : etDeliveries) {
                    List<EstimatedVersionFrameStructure> estimatedJourneyVersionFrames = etDelivery.getEstimatedJourneyVersionFrames();
                    for (EstimatedVersionFrameStructure estimatedJourneyVersionFrame : estimatedJourneyVersionFrames) {

                        List<EstimatedVehicleJourney> estimatedVehicleJourneies = estimatedJourneyVersionFrame
                            .getEstimatedVehicleJourneies();

                        for (EstimatedVehicleJourney estimatedVehicleJourney : estimatedVehicleJourneies) {
                            if (isTrue(estimatedVehicleJourney.isExtraJourney())) {
                                final List<VehicleModesEnumeration> vehicleModes = estimatedVehicleJourney
                                    .getVehicleModes();

                                final EstimatedVehicleJourney.RecordedCalls recordedCalls = estimatedVehicleJourney
                                    .getRecordedCalls();
                                if (recordedCalls != null && recordedCalls.getRecordedCalls() != null) {
                                    final List<RecordedCall> calls = recordedCalls.getRecordedCalls();
                                    for (
                                        int i = 0; i < calls.size() - 2; i++
                                    ) { // -2 to loop through "this" and "next"
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

                                            validate(vehicleModes, fromStop, toStop);
                                        }
                                    }
                                }
                                final EstimatedVehicleJourney.EstimatedCalls estimatedCalls = estimatedVehicleJourney
                                    .getEstimatedCalls();
                                if (estimatedCalls != null && estimatedCalls.getEstimatedCalls() != null) {
                                    final List<EstimatedCall> calls = estimatedCalls.getEstimatedCalls();
                                    for (
                                        int i = 0; i < calls.size() - 2; i++
                                    ) { // -2 to loop through "this" and "next"
                                        final EstimatedCall thisCall = calls.get(i);
                                        final EstimatedCall nextCall = calls.get(i + 1);

                                        if (thisCall.getStopPointRef() != null &&
                                            nextCall.getStopPointRef() != null) {
                                            final String fromStop = getMappedId(thisCall
                                                .getStopPointRef()
                                                .getValue());
                                            final String toStop = getMappedId(nextCall
                                                .getStopPointRef()
                                                .getValue());

                                            validate(vehicleModes, fromStop, toStop);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    private void validate(
        List<VehicleModesEnumeration> vehicleModes,
        String fromStop, String toStop
    ) throws InvalidVehicleModeForStopException {

        verifyMode(vehicleModes, fromStop);
        verifyMode(vehicleModes, toStop);
    }


    private void verifyMode(List<VehicleModesEnumeration> vehicleModes, String stop)
        throws InvalidVehicleModeForStopException {
        if (!StopsUtil.doesVehicleModeMatchStopMode(vehicleModes, stop)) {
            throw new InvalidVehicleModeForStopException(vehicleModes, stop);
        }
    }
}
