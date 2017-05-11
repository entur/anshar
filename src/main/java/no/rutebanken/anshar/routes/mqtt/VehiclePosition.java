package no.rutebanken.anshar.routes.mqtt;

/**
 * digitransit-ui message keys
 */
public interface VehiclePosition {
    // digitransit-ui mapping
    String ROOT = "VP";
    String DESIGNATION = "desi";
    String DIRECTION = "dir";
    String OPERATOR = "oper";
    String VEHICLE_ID = "veh";
    String TIMESTAMP = "tst";
    String TSI = "tsi";
    String LATITUDE = "lat";
    String LONGITUDE = "long";
    String DELAY = "dl";
    String ODAY = "oday";
    String JOURNEY = "jrn";
    String LINE = "line";
    String STARTTIME = "start";
    String STOP_INDEX = "stop_index";
    String SOURCE = "source";

    String UNKNOWN = "XXX";
}
