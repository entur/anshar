# Lifecycle for a SIRI Subscription

(In this case Entur is client, and Operator is the server.)

## Initiating Subscription
The client sends a SubscriptionRequest to server to initiate a subscription. The SubscriptionRequest will include a unique SubscriptionId, the desired HeartbeatInterval and an Address defining the endpoint where all data for this specific subscription should be sent. 

When the server has processed the SubscriptionRequest, it should respond with a SubscriptionResponse.

The server then sends a ServiceDelivery (XML over HTTP POST) containing all currently known and active updates to ensure that the client is up to date.
For SX-messages: this includes all current and future messages that are still open/active.
For ET-updates: All departures that are still running (started ant not yet arrived, or about to start), or that have known changes (cancellations etc.)

Note: Each SIRI-datatype (ET/SX/VM) will have its own subscription with a separate SubscriptionId and separate Address.

## Sending data
The endpoint defined by Address is to be considered a REST endpoint that expects valid SIRI-XML sent as HTTP POST. It will return "200 OK" when data is received. (The response will be sent before the data is actually processed, so invalid data will still get 200 OK - although the data may be rejected in the next step.)

## Sending updates
As soon as a realtime-data is updated on the server, the complete, updated message should be sent to the client.

## Keeping subscription alive
To let the client know that a subscription is still active, a HeartbeatNotification should be sent periodically (defined by interval in SubscriptionRequest - typically every minute.)

It is the client's responsibility to keep the subscription alive. This means that if the server stops sending updates/heartbeats, the client will terminate and reestablish the subscription. When the server receives a TerminateSubscriptionRequest, the server can forget all information/progress for that subscription. For the following SubscriptionRequest, all initial data should be resent as if the subscription is completely new. This is to ensure that the client is fully updated at all times.

## When the client dies
If the client is having unexpected problems, it may not be able to process the updates/heartbeats sent by the client (i.e. client returns something other than "200 OK"). The server should allow for a single retry in case there is a small hick-up. If the retry also fails, the server can delete the subscription, and let the client re-establish when it is ready.

## Authentication
Currently, there is no required authentication for inbound traffic. The security is handled by using https, and by having support for dynamic endpoint-urls for each subscription that are easily changed on the client-side. 

If a provider requires authentication (i.e. access to create subscriptions) a solution that is based on e.g. sending an API-key as an HTTP-header will work by specifying this in the subscription-config.