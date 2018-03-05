package no.rutebanken.anshar.export;

import org.onebusaway.gtfs_realtime.siri.SiriToGtfsRealtimeMain;

public class GtfsRealtimeExample {


  public static void main(String[] args) throws Exception {

    SiriToGtfsRealtimeMain main = new SiriToGtfsRealtimeMain();
    main.run(new String[]{"--clientUrl=http://localhost:8012/anshar/tmplogger",
            "--alertsUrl=http://localhost:8012/anshar/tmplogger",
            "--tripUpdatesUrl=http://localhost:8012/anshar/tmplogger",
            "--vehiclePositionsUrl=http://localhost:8012/anshar/tmplogger",
            "--updateFrequency=30",
            "Url=https://operator-test.entur.org/anshar/services,ModuleType=VEHICLE_MONITORING,ReconnectionAttempts=-1,Subscribe=false,PollInterval=60",
            "Url=https://operator-test.entur.org/anshar/services,ModuleType=SITUATION_EXCHANGE,ReconnectionAttempts=-1,Subscribe=false,PollInterval=60"});
  }

}