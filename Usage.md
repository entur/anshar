# Usage

# SIRI Subscription
- HTTP POST http://mottak-test.rutebanken.org/anshar/subscribe

 
# SIRI GetServiceRequest
- HTTP POST http://mottak-test.rutebanken.org/anshar/services


# REST API

## Example URL's
- HTTP GET http://mottak-test.rutebanken.org/anshar/rest/sx
- HTTP GET http://mottak-test.rutebanken.org/anshar/rest/vm
- HTTP GET http://mottak-test.rutebanken.org/anshar/rest/et

## Optional parameters

### datasetId
- I.e. _datasetId=rut_
- Limits the results to original dataset-provider

### requestorId
- I.e. _requestorId=f5907670-9777-11e6-ae22-56b6b649961_
- Value needs to be unique for as long as only updated data are needed, a generated UUID is a good choice
- First request creates a short lived session (timeout after e.g. 5 minutes)
- Subsequent requests returns only changes since last request with the same requestorId