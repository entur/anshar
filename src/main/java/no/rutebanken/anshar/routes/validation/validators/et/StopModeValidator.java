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
import uk.org.siri.siri21.EstimatedCall;
import uk.org.siri.siri21.EstimatedTimetableDeliveryStructure;
import uk.org.siri.siri21.EstimatedVehicleJourney;
import uk.org.siri.siri21.EstimatedVersionFrameStructure;
import uk.org.siri.siri21.RecordedCall;
import uk.org.siri.siri21.Siri;
import uk.org.siri.siri21.VehicleModesEnumeration;

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
                                    for (RecordedCall call : calls) {
                                        if (call.getStopPointRef() != null) {
                                            validate(estimatedVehicleJourney, vehicleModes,
                                                getMappedId(call.getStopPointRef().getValue())
                                            );
                                        }
                                    }
                                }
                                final EstimatedVehicleJourney.EstimatedCalls estimatedCalls = estimatedVehicleJourney
                                    .getEstimatedCalls();
                                if (estimatedCalls != null && estimatedCalls.getEstimatedCalls() != null) {
                                    final List<EstimatedCall> calls = estimatedCalls.getEstimatedCalls();
                                    for (EstimatedCall call : calls) {
                                        if (call.getStopPointRef() != null) {
                                            validate(estimatedVehicleJourney, vehicleModes,
                                                getMappedId(call.getStopPointRef().getValue())
                                            );
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
            EstimatedVehicleJourney estimatedVehicleJourney, List<VehicleModesEnumeration> vehicleModes,
            String stopRef
    ) throws InvalidVehicleModeForStopException {

        verifyMode(estimatedVehicleJourney, vehicleModes, stopRef);
    }


    private void verifyMode(EstimatedVehicleJourney estimatedVehicleJourney, List<VehicleModesEnumeration> vehicleModes, String stopRef)
        throws InvalidVehicleModeForStopException {
        if (!StopsUtil.doesVehicleModeMatchStopMode(vehicleModes, stopRef)) {
            throw new InvalidVehicleModeForStopException(estimatedVehicleJourney, vehicleModes, stopRef);
        }
    }
}
