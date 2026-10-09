# Subscription configuration

Outbound subscriptions (Anshar → external SIRI provider) are configured as a list under `anshar.subscriptions`
in a YAML file. See `subscriptions-example.yml` for a working example.

Each entry is validated on startup, and startup fails on missing required fields or unresolved `${...}` placeholders.
Fields marked **required** must be set.

## Identification

| Property         | Values               | Description                                                                                                         |
|------------------|----------------------|---------------------------------------------------------------------------------------------------------------------|
| `internalId`     | Unique number        | **Required.** Must be unique across all subscriptions. Used to detect when a subscription's config has changed.     |
| `vendor`         | String               | **Required.** Label used in the inbound URL (`/anshar/{version}/{type}/{vendor}/{subscriptionId}`).               |
| `name`           | String               | Name shown on the stats page.                                                                                       |
| `datasetId`      | Short string         | **Required.** Preferably the codespace ID. Separates data from different providers, and is used in URLs.         |
| `subscriptionId` | String, e.g. a UUID  | **Required.** Unique ID for the subscription. `${random.uuid}` can be used.                                         |
| `requestorRef`   | String               | **Required.** Identifies Anshar to the remote server. Sometimes assigned by the provider.                           |
| `active`         | `true`, `false`      | Enables/disables the subscription.                                                                                   |

## Type and mode

| Property           | Values                                                                              | Description                                                                                                                                                                                                                                                                                   |
|--------------------|-------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `subscriptionType` | `ESTIMATED_TIMETABLE`, `VEHICLE_MONITORING`, `SITUATION_EXCHANGE`, `FACILITY_MONITORING` | **Required.** SIRI data type.                                                                                                                                                                                                                                                                 |
| `subscriptionMode` | `REQUEST_RESPONSE`, `SUBSCRIBE`, `FETCHED_DELIVERY`, `POLLING_FETCHED_DELIVERY`     | **Required.** <br>• `REQUEST_RESPONSE`: Anshar polls for all current data at a regular interval. <br>• `SUBSCRIBE`: The provider pushes changes to Anshar as they happen. Requires `anshar.inbound.url` to be reachable by the provider. <br>• `FETCHED_DELIVERY` / `POLLING_FETCHED_DELIVERY`: The provider notifies Anshar when data has been updated, and Anshar then sends a `ServiceRequest` to fetch it. |
| `serviceType`      | `REST`, `SOAP`                                                                      | **Required.** `SOAP` wraps requests in a SOAP envelope.                                                                                                                                                                                                                                       |
| `version`          | `1.4`, `2.0`, `2.1`                                                                 | SIRI version the remote service implements. Defaults to `2.1`.                                                                                                                                                                                                                                 |

## Endpoints

### `urlMap`

**Required.** Maps each request type to a URL. Only the entries needed for the chosen mode are required:

| Key                       | Used for                                    | Required when                                           |
|---------------------------|---------------------------------------------|---------------------------------------------------------|
| `SUBSCRIBE`               | Setting up the subscription                 | `SUBSCRIBE`, `FETCHED_DELIVERY`                         |
| `DELETE_SUBSCRIPTION`     | Ending the subscription                     | `SUBSCRIBE`, `FETCHED_DELIVERY`                         |
| `CHECK_STATUS`            | `CheckStatusRequest`                        | Optional                                                |
| `GET_ESTIMATED_TIMETABLE` | ET `ServiceRequest`                         | `REQUEST_RESPONSE` with `ESTIMATED_TIMETABLE`           |
| `GET_VEHICLE_MONITORING`  | VM `ServiceRequest`                         | `REQUEST_RESPONSE` with `VEHICLE_MONITORING`            |
| `GET_SITUATION_EXCHANGE`  | SX `ServiceRequest`                         | `REQUEST_RESPONSE` with `SITUATION_EXCHANGE`            |

URLs without a scheme are prefixed with `http://`. The legacy `https4://` scheme is rewritten to `https://`.
For `FETCHED_DELIVERY`, the `GET_*` URLs default to the `SUBSCRIBE` URL.

### Request details

| Property            | Values                                   | Description                                                                                                             |
|---------------------|------------------------------------------|-------------------------------------------------------------------------------------------------------------------------|
| `contentType`       | e.g. `text/xml;charset=UTF-8`            | **Required.** `Content-Type` header on outgoing requests.                                                                 |
| `customHeaders`     | Map of `header-name: value`              | Headers added to every outgoing request. Values are masked in logs.                                                     |
| `oauth2Config`      | Map, see below                           | If set, an OAuth2 token is fetched and added to outgoing requests. Values are masked in logs.                           |
| `operatorNamespace` | Namespace URL                            | Namespace used when marshalling requests for this subscription, e.g. `http://www.siri.org.uk/siri`.                     |
| `soapenvNamespace`  | Namespace URL                            | Namespace used for the SOAP envelope (only applies when `serviceType: SOAP`).                                                |
| `addressFieldName`  | `Address`, `ConsumerAddress` (default)   | Element that holds Anshar's inbound URL in subscription requests.                                                       |

`oauth2Config` keys: `SERVER`, `CLIENT_ID`, `CLIENT_SECRET`, `AUDIENCE`, `SCOPE`, `GRANT_TYPE`, `CONTENT_TYPE`.

## Timing

| Property                      | Values             | Description                                                                                                      |
|-------------------------------|--------------------|------------------------------------------------------------------------------------------------------------------|
| `heartbeatIntervalSeconds`    | Integer > 0        | **Required.** Expected heartbeat interval. Used to detect subscriptions that have stopped responding.             |
| `durationOfSubscriptionHours` | Integer > 0        | **Required.** Requested lifetime of the subscription.                                                            |
| `updateIntervalSeconds`       | Integer            | Sets `UpdateInterval` in VM subscription requests.                                                               |
| `previewIntervalSeconds`      | Integer            | Requested preview interval (ET/SX/FM).                                                                           |
| `changeBeforeUpdatesSeconds`  | Integer            | Sets `ChangeBeforeUpdates` in ET/VM subscription requests.                                                       |
| `restartTime`                 | `"HH:mm"`, e.g. `"01:00"` | Time of day at which a `SUBSCRIBE` subscription is restarted. Must be quoted.                             |
| `incrementalUpdates`          | `true`, `false`, empty | Sets `IncrementalUpdates` in the request. If left empty, the element is omitted.                                  |
| `dataSupplyRequestForInitialDelivery` | `true`, `false` | Sends a `DataSupplyRequest` to fetch the first delivery when the subscription starts.                    |

## Data processing

| Property                 | Values                          | Description                                                                                                                                   |
|--------------------------|---------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------|
| `mappingAdapterId`       | Adapter ID, e.g. `entur`        | Selects the mapping adapter used to convert IDs to a common format. See `MappingAdapters.md`.                                                 |
| `idMappingPrefixes`      | List, e.g. `RUT,ATB,KOL`        | Prefixes used when mapping stop IDs to NSR IDs (used by some adapters).                                                                      |
| `filterPresets`          | List of `SubscriptionPreset` values, e.g. `BYBANEN` | Predefined filters added to the subscription request.                                                                    |
| `codespaceWhiteList`     | List of codespaces              | Only data with these codespaces is kept.                                                                                                    |
| `codespaceBlackList`     | List of codespaces              | Data with these codespaces is dropped.                                                                                                        |
| `useProvidedCodespaceId` | `true`, `false` (default)       | Keep the codespace given in the data instead of overriding it with `datasetId`. Use this when one subscription delivers data for several codespaces. |
| `validation`             | `true`, `false`                 | Enables validation of incoming data from startup.                                                                                    |
| `validationFilter`       | String                          | Only validate deliveries that contain this string. Cleared when `validation` is `false`.                                                   |
| `enrichSiriData`         | `true`, `false` (default)       | Publishes incoming data to Kafka for APC enrichment.                                                                                         |
| `forwardPositionData`    | `true`, `false` (default)       | VM only: forward position data only.                                                                                                       |
| `vehicleMonitoringRefValue` | String                       | VM only: sets `VehicleMonitoringRef` in the subscription request.                                                                          |

## Operations

| Property                | Values          | Description                                                                                                                                       |
|-------------------------|-----------------|---------------------------------------------------------------------------------------------------------------------------------------------------|
| `reduceLogging`         | `true`, `false` | Permanently reduces logging for this subscription.                                                                                                |
| `dataNotReceivedAction` | Object          | Sends an HTTP POST if no data has been received for a while. Fields: `enabled`, `inactivityMinutes`, `endpoint`, `jsonPostContent`.               |

## Example

```yaml
anshar:
  subscriptions:
    - internalId: 100
      vendor: enturVm
      name: Entur - VM
      datasetId: ENT
      serviceType: REST
      subscriptionType: VEHICLE_MONITORING
      subscriptionMode: REQUEST_RESPONSE
      heartbeatIntervalSeconds: 60
      updateIntervalSeconds: 1
      operatorNamespace: http://www.siri.org.uk/siri
      urlMap:
        GET_VEHICLE_MONITORING: https://api.entur.io/realtime/v1/services
      customHeaders:
        ET-Client-Name: anshar-example-client
      version: 2.0
      contentType: text/xml;charset=UTF-8
      subscriptionId: anshar-example-${random.uuid}
      requestorRef: anshar-example-${random.uuid}
      durationOfSubscriptionHours: 168
      mappingAdapterId: entur
      codespaceWhiteList: TST
      restartTime: "01:30"
      active: true
```

**Note:** YAML is indentation-sensitive. Make sure list items and nested maps are indented correctly.
