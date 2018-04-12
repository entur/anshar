package no.rutebanken.anshar.routes.validation.validators;

class Constants {
    private static final String SERVICE_DELIVERY = "Siri/ServiceDelivery/";
    static final String PT_SITUATION_ELEMENT = SERVICE_DELIVERY + "SituationExchangeDelivery/Situations/PtSituationElement";
    static final String ESTIMATED_VEHICLE_JOURNEY = SERVICE_DELIVERY + "EstimatedTimetableDelivery/EstimatedJourneyVersionFrame/EstimatedVehicleJourney";
    static final String ESTIMATED_CALL = ESTIMATED_VEHICLE_JOURNEY + "/EstimatedCalls/EstimatedCall";
}
