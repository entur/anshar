# Usage

# SIRI Subscription
- Supports SIRI 2.0 SubscriptionRequest
- HTTP POST https://api.entur.org/anshar/1.0/anshar/subscribe
- Vendor-specific subscriptions can be obtained by specifying datasetId in url (e.g. http://mottak-test.rutebanken.org/anshar/subscribe/RUT) 
 
# SIRI GetServiceRequest
- Supports SIRI 2.0 ServiceRequests
- HTTP POST https://api.entur.org/anshar/1.0/anshar/services
- Vendor-specific data can be obtained by specifying datasetId in url (e.g. http://mottak-test.rutebanken.org/anshar/services/RUT)
- *NOTE:* For periodic requests (currently within 5 minutes), RequestorRef may be reused to only get "changes since last request" - see requestorId below.

# SIRI common
- To obtain the original ID a query-parameter can be added to the service/subscribe-url - i.e. `?useOriginalId=true`
- To obtain a OTP-friendly ID a query-parameter can be added to the service/subscribe-url - i.e. `?useOtpId=true`


# REST API

## Example URL's
- HTTP GET https://api.entur.org/anshar/1.0/rest/sx
- HTTP GET https://api.entur.org/anshar/1.0/rest/vm
- HTTP GET https://api.entur.org/anshar/1.0/rest/et

## Optional parameters

### datasetId
- E.g. _datasetId=RUT_
- Limits the results to original dataset-provider

### requestorId
- E.g. _requestorId=f5907670-9777-11e6-ae22-56b6b649961_
- Value needs to be unique for as long as only updated data are needed, a generated UUID is a good choice
- First request creates a short lived session (timeout after e.g. 5 minutes)
- Subsequent requests returns only changes since last request with the same requestorId

### useOriginalId
- E.g. _useOriginalId=true_
- Instructs Anshar to return datasets with the original, unaltered IDs.