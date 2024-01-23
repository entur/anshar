| Propertyname                | Possible values | Description                                                                                                                                                                                                                                                              |
|-----------------------------|---|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| vendor                      |Any string| Informational label used in incoming url                                                                                                                                                                                                                                 |
| name                        |Any string| Informational name used in stats-page                                                                                                                                                                                                                                    |
| datasetId                   |Any string - should be short| Preferable Codespace-ID used to separate data from different vendors - also used in urls                                                                                                                                                                                 |
| serviceType                 |REST, SOAP| Specifies if requests should be wrapped in soap envelopes                                                                                                                                                                                                                |
| subscriptionType            |VEHICLE_MONITORING, SITUATION_EXCHANGE, ESTIMATED_TIMETABLE| SIRI datatype for this subscription                                                                                                                                                                                                                                      |
| subscriptionMode            |REQUEST_RESPONSE, SUBSCRIBE, FETCHED_DELIVERY| - REQUEST_RESPONSE: Client gets all current data at som interval - SUBSCRIBE:Pubsub-pattern - changes are pushed from server to client when they occur. - FETCHED_DELIVERY:Client is notified that data is updated, and should POST a GetServiceRequest to get updated data. |
| heartbeatIntervalSeconds    |Any int| Expected heartbeat frequency                                                                                                                                                                                                                                             |
| updateIntervalSeconds       |Any int| Indicates update-interval (only applicable when subscription is ESTIMATED_TIMETABLE/VEHICLE_MONITORING and type SUBSCRIBE)                                                                                                                                               |
| previewIntervalSeconds      |Any int| Requested preview interval                                                                                                                                                                                                                                               |
| operatorNamespace           |Namespace URL| Optional namespace used in XML-marshalling data from this subscription                                                                                                                                                                                                   |
| addressFieldName            | *Address* or *ConsumerAddress* (default)| XML-attribute to use for inbound URL                                                                                                                                                                                                                                     |
| soapenvNamespace            | Namespace URL for soap-Envelope| Optional namespace used in XML-marshalling Soap-Envelope                                                                                                                                                                                                                 |
| incrementalUpdates          |_true_, _false_ or leave empty | _true_ and _false_ sets the attribute to specified value. If empty, the IncrementalUpdates-element is not included in the Request                                                                                                                                        | 
| urlMap                      |List of urls| Specifies URLs to separate services, unused may be deleted. Specify https4:// for HTTPS (e.g. https4://localhost:8080/siri/sx)                                                                                                                                           |
| SUBSCRIBE                   |localhost:8080/siri/service/subscribe.xml | URL to register subscription                                                                                                                                                                                                                                             |
| DELETE_SUBSCRIPTION         |localhost:8080/siri/service/managesubscription.xml | URL to terminate subscription                                                                                                                                                                                                                                            |
| CHECK_STATUS                |localhost:8080/siri/service/checkstatus.xml| URL used to check status                                                                                                                                                                                                                                                 |
| GET_VEHICLE_MONITORING      |localhost:8080/siri/vm | URL to SIRI VM-ServiceRequest                                                                                                                                                                                                                                            |
| GET_SITUATION_EXCHANGE      |localhost:8080/siri/sx | URL to SIRI SX-ServiceRequest                                                                                                                                                                                                                                            |
| customHeaders               |header-name : header:value | List of headers that will be aded to all outgoing requests for subscription.                                                                                                                                                                                             |
| header-name: header-value   | |                                                                                                                                                                                                                                                                          |
| version                     |1.4, 2.0  | SIRI-version remote service implements                                                                                                                                                                                                                                   |
| subscriptionId              |Random uuid| Unique ID used to identify subscription                                                                                                                                                                                                                                  |
| requestorRef                |Any String, may be provided by remote serviceprovider | Used to identify against remote server                                                                                                                                                                                                                                   |
| durationOfSubscriptionHours |Any int | Number of hours to trigger subscription to be terminated/restarted                                                                                                                                                                                                       |
| mappingAdapterPreset        |RUTER, ATB, KOLUMBUS, AKT| Adapters used to convert ids to common format                                                                                                                                                                                                                            |
| filterPresets               |BYBANEN| Specific filters to use                                                                                                                                                                                                                                                  |
| idMappingPrefixes           |RUT, ATB, KOL, AKT, NRI+++| List of prefixes to be used when mapping ids form NSR                                                                                                                                                                                                                    |
| restartTime                 | "01:00"| Time of day that a subscription will be restarted in the format HH:mm                                                                                                                                                                                                    |
| codespaceWhiteList          |RUT, ATB, KOL, AKT, NRI+++| List of prefixes to be allowed as codespace on id-values                                                                                                                                                                                                          |
| active                      |true,false | Enables/disables subscription                                                                                                                                                                                                                                            |
| validation                  |true,false | Enables/disables validation on startup                                                                                                                                                                                                                                   |

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
      restartTime: "01:30"
      active: true
```

**Note!** Since this is a .yml-file, correct indentation is crucial.