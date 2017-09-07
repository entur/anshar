package no.rutebanken.anshar.routes.siri.processor;


import no.rutebanken.anshar.routes.siri.handlers.OutboundIdMappingPolicy;
import no.rutebanken.anshar.routes.siri.transformer.SiriValueTransformer;
import no.rutebanken.anshar.routes.siri.transformer.ValueAdapter;
import uk.org.siri.siri20.EstimatedTimetableDeliveryStructure;
import uk.org.siri.siri20.EstimatedVehicleJourney;
import uk.org.siri.siri20.EstimatedVersionFrameStructure;
import uk.org.siri.siri20.Siri;

import java.util.List;

public class RuterOutboundDatedVehicleRefAdapter extends ValueAdapter implements PostProcessor {

    private OutboundIdMappingPolicy outboundIdMappingPolicy;

    public RuterOutboundDatedVehicleRefAdapter(Class clazz, OutboundIdMappingPolicy outboundIdMappingPolicy) {
        super(clazz);
        this.outboundIdMappingPolicy = outboundIdMappingPolicy;
    }


    @Override
    public void process(Siri siri) {
        if (siri != null && siri.getServiceDelivery() != null) {
            List<EstimatedTimetableDeliveryStructure> estimatedTimetableDeliveries = siri.getServiceDelivery().getEstimatedTimetableDeliveries();
            if (estimatedTimetableDeliveries != null) {
                for (EstimatedTimetableDeliveryStructure estimatedTimetableDelivery : estimatedTimetableDeliveries) {
                    List<EstimatedVersionFrameStructure> estimatedJourneyVersionFrames = estimatedTimetableDelivery.getEstimatedJourneyVersionFrames();
                    if (estimatedJourneyVersionFrames != null) {
                        for (EstimatedVersionFrameStructure estimatedVersionFrameStructure : estimatedJourneyVersionFrames) {
                            if (estimatedVersionFrameStructure != null) {
                                for (EstimatedVehicleJourney estimatedVehicleJourney : estimatedVersionFrameStructure.getEstimatedVehicleJourneies()) {
                                    if (estimatedVehicleJourney.getFramedVehicleJourneyRef() != null) {
                                        String datedVehicleJourneyRef = estimatedVehicleJourney.getFramedVehicleJourneyRef().getDatedVehicleJourneyRef();
                                        if (datedVehicleJourneyRef != null) {
                                            estimatedVehicleJourney.getFramedVehicleJourneyRef().setDatedVehicleJourneyRef(apply(datedVehicleJourneyRef));
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    public String apply(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        if (text.contains(SiriValueTransformer.SEPARATOR)) {
            if (outboundIdMappingPolicy == OutboundIdMappingPolicy.DEFAULT) {
                text = getMappedId(text);
            } else if (outboundIdMappingPolicy == OutboundIdMappingPolicy.ORIGINAL_ID) {
                text = getOriginalId(text);
            } else if (outboundIdMappingPolicy == OutboundIdMappingPolicy.OTP_FRIENDLY_ID) {
                text = getOtpFriendly(text);
            }
        }
        return text;
    }

    public static String getOriginalId(String text) {
        return text.substring(0, text.indexOf(SiriValueTransformer.SEPARATOR));
    }

    public static String getMappedId(String text) {
        return text.substring(text.indexOf(SiriValueTransformer.SEPARATOR)+1);
    }

    public static String getOtpFriendly(String text) {
        return text.substring(text.indexOf(SiriValueTransformer.SEPARATOR)+1).replace(":", ".");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RuterOutboundDatedVehicleRefAdapter)) return false;

        RuterOutboundDatedVehicleRefAdapter that = (RuterOutboundDatedVehicleRefAdapter) o;

        if (!super.getClassToApply().equals(that.getClassToApply())) return false;
        return outboundIdMappingPolicy == that.outboundIdMappingPolicy;

    }
}
