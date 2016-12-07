|Propertyname | Possible values | Description |
|---|---|---|
|vendor|Any string|Informational label used in incoming url|
|datasetId|Any string - should be short|Id used to separate data from different vendors - also used in urls|
|serviceType|REST, SOAP|Specifies if requests should be wrapped in soap envelopes|
|subscriptionType|VEHICLE_MONITORING, SITUATION_EXCHANGE, ESTIMATED_TIMETABLE, PRODUCTION_TIMETABLE|SIRI datatype for this subscription|
|subscriptionMode|REQUEST_RESPONSE, SUBSCRIBE| - REQUEST_RESPONSE: Client gets all current data at som interval - SUBSCRIBE:Pubsub-pattern - changes are pushed from server to client when they occur|
|heartbeatIntervalSeconds|Any int|Expected heartbeat frequency|
|operatorNamespace|Namespace URL|Optional namespace used in XML-marshalling data from this subscription|
|urlMap|List of urls| Specifies URLs to separate services, unused may be deleted|
|  SUBSCRIBE|localhost:8080/siri/service/subscribe.xml |URL to register subscription|
|  DELETE_SUBSCRIPTION|localhost:8080/siri/service/managesubscription.xml |URL to terminate subscription|
|  CHECK_STATUS|localhost:8080/siri/service/checkstatus.xml|URL used to check status|
|  GET_VEHICLE_MONITORING|localhost:8080/siri/vm |URL to SIRI VM-ServiceRequest|
|  GET_SITUATION_EXCHANGE|localhost:8080/siri/sx |URL to SIRI SX-ServiceRequest|
|version|1.4, 2.0  |SIRI-version remote service implements|
|subscriptionId|Random uuid|Unique ID used to identify subscription|
|requestorRef|Any String, may be provided by remote serviceprovider |Used to identify against remote server|
|durationOfSubscriptionHours|Any int |Number of hours to trigger subscription to be terminated/restarted|
|mappingAdapterPreset|RUTER, ATB, KOLUMBUS, AKT|Adapters used to convert ids to common format|
|filterPresets|BYBANEN|Specific filters to use|
|idMappingPrefixes|RUT, ATB, KOL, AKT, NRI+++|List of prefixes to be used when mapping ids form NSR|
|active|true,false |Enables/disables subscription|

Example:
```
anshar:
  subscriptions:
    -
      vendor: rutebanken
      datasetId: rb
      serviceType: REST
      subscriptionType: VEHICLE_MONITORING
      subscriptionMode: REQUEST_RESPONSE
      heartbeatIntervalSeconds: 60
      operatorNamespace: http://www.siri.org.uk/siri
      urlMap:
        SUBSCRIBE: mottak-test.rutebanken.org/anshar/subscribe
        DELETE_SUBSCRIPTION: mottak-test.rutebanken.org/anshar/subscribe
        GET_VEHICLE_MONITORING: mottak-test.rutebanken.org/anshar/services
      version: 2.0
      subscriptionId: bbd85dee-2a50-49d4-852a-60e8fd00269a
      requestorRef: RutebankenDEV
      durationOfSubscriptionHours: 168
      mappingAdapterPreset: RUTER
      filterPresets: BYBANEN
      idMappingPrefixes: RUT,OPP,BRA
      active: true
```

**Note!** Since this is a .yml-file, correct indentation is crucial.