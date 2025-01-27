# Usage

# Build
- mvn clean package -Dmaven.test.skip=true

# Run
The Google SDK requires the path to a credentials-file as an environment variable to allow startup. 
This is not used by the application by default, but it still needs to exist.

A dummy-credentials file is provided, and can be set by 
`export GOOGLE_APPLICATION_CREDENTIALS=src/main/resources/dummy_credentials.json` (Mac/Linux) before starting the application.
To start the application, run 
`java -jar target/anshar-0.0.1-SNAPSHOT.jar`

The app will start with subscriptions defined in `resources/subscription-example.yml`. For subscriptions of type `SUBSCRIBE`, an inbound URL is required - this is defined in property `anshar.inbound.url`. 

When the app has started, an admin-gui will be available at http://localhost:8012/anshar/stats 

# Using the container image

An image is build on every push to Entur's master branch.

To run the image:

```shell
# With default configuration
docker run -p 8012:8012 ghcr.io/entur/anshar:latest
# With custom properties. In this example, application.properties' anshar.subscriptions.config.path should be "/subscriptions.yml"
docker run -p 8012:8012 --mount type=bind,source=/path/to/application.properties,target=/application.properties --mount type=bind,source=/path/to/subscriptions.yml,target=/subscriptions.yml ghcr.io/entur/anshar:latest

# a more extensive example using podman
podman run -p 8012:8012 --rm \
  -v `pwd`/src/main/resources/application.properties:/application.properties:z \
  -v `pwd`/src/main/resources/subscriptions-example.yml:/subscriptions.yml:z \
  -v `pwd`/src/main/resources/dummy_credentials.json:/credentials.json:z \
  -v `pwd`/src/main/resources/logback.xml:/logback.xml:z \
  -e GOOGLE_APPLICATION_CREDENTIALS=/credentials.json \
  -e JAVA_TOOL_OPTIONS="-Dlogging.config=/logback.xml" \
  docker.io/entur/anshar:latest
```

# SIRI Subscription
- Supports SIRI 2.0 SubscriptionRequest
- HTTP POST https://api.entur.io/realtime/v1/subscribe
- Vendor-specific subscriptions can be obtained by specifying datasetId in url (e.g. http://api.entur.io/realtime/v1/subscribe/RUT) 
 
# SIRI GetServiceRequest
- Supports SIRI 2.0 ServiceRequests
- HTTP POST https://api.entur.io/realtime/v1/services
- Vendor-specific data can be obtained by specifying datasetId in url (e.g. http://api.entur.io/realtime/v1/services/RUT)
- *NOTE:* For periodic requests (currently within 5 minutes), RequestorRef may be reused to only get "changes since last request" - see requestorId below.

# SIRI common
- To obtain the original ID a query-parameter can be added to the service/subscribe-url - i.e. `?useOriginalId=true`

# REST API

## Example URL's
- HTTP GET https://api.entur.io/realtime/v1/rest/sx
- HTTP GET https://api.entur.io/realtime/v1/rest/vm
- HTTP GET https://api.entur.io/realtime/v1/rest/et

## Optional parameters

### datasetId
- E.g. _datasetId=RUT_
- Limits the results to original dataset-provider

### excludedDatasetIds
- Comma-separated list of datasets to exclude from result
- E.g. _excludedDatasetIds=RUT,NSB_
- Limits result by excluding the provided datasetIds
- *Note:* Valid for VM and ET requests - both HTTP GET and POST

### requestorId
- E.g. _requestorId=f5907670-9777-11e6-ae22-56b6b649961_
- Value needs to be unique for as long as only updated data are needed, a generated UUID is a good choice
- First request creates a short lived session (timeout after e.g. 5 minutes)
- Subsequent requests returns only changes since last request with the same requestorId

### useOriginalId
- E.g. _useOriginalId=true_
- Instructs Anshar to return datasets with the original, unaltered IDs.

### maxSize
- E.g. _maxSize=100_ (default is 1500)
- Limits the number of elements in the returned result. _requestorId_ will be used to track changes since last request and is provided in result. An id will be created and returned if not provided.
- If more data exists, the attribute _MoreData_ will be set to _true_ 
 