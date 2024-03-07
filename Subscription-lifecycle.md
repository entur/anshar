# Lifecycle for a SIRI Subscription

(In this case Entur is client, and Operator is the server.)

## Demo-app
There is now a simple demo-app of a SIRI client/server available at https://github.com/entur/siri-service-example that demonstrate these concepts.

## Initiating Subscription
The client sends a SubscriptionRequest to server to initiate a subscription. The SubscriptionRequest will include a unique SubscriptionId, the desired HeartbeatInterval and an Address defining the endpoint where all data for this specific subscription should be sent. 

When the server has processed the SubscriptionRequest, it should respond with a SubscriptionResponse.

The server should then send a ServiceDelivery (XML over HTTP POST) containing all currently known and active updates to ensure that the client is up to date.
For SX-messages: this includes all current and future messages that are still open/active.
For ET-updates: All departures that are still running (started and not yet arrived, or about to start), or that have known changes (cancellations etc.)

Note: Each SIRI-datatype (ET/SX/VM) will have its own subscription with a separate SubscriptionId and posibly also a separate Address.

SubscriptionRequest:
```xml
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Siri version="2.0" xmlns="http://www.siri.org.uk/siri" xmlns:ns2="http://www.ifopt.org.uk/acsb" xmlns:ns3="http://www.ifopt.org.uk/ifopt" xmlns:ns4="http://datex2.eu/schema/2_0RC1/2_0">
    <SubscriptionRequest>
        <RequestTimestamp>2019-12-03T13:25:00+01:00</RequestTimestamp>
        <Address>https://SERVER:PORT/full/path/to/consumer/endpoint</Address>
        <RequestorRef>ENTUR_DEV</RequestorRef>
        <MessageIdentifier>ad2c0501-dd99-468a-a1bc-91ac8fbd7543</MessageIdentifier>
        <SubscriptionContext>
            <HeartbeatInterval>PT60S</HeartbeatInterval>
        </SubscriptionContext>
        <EstimatedTimetableSubscriptionRequest>
            <SubscriberRef>ENTUR_DEV</SubscriberRef>
            <SubscriptionIdentifier>SUBSCRIPTION_ID</SubscriptionIdentifier>
            <InitialTerminationTime>2020-12-03T13:25:00+01:00</InitialTerminationTime>
            <EstimatedTimetableRequest version="2.0">
                <RequestTimestamp>2019-12-03T13:25:00+01:00</RequestTimestamp>
                <PreviewInterval>PT10H</PreviewInterval>
            </EstimatedTimetableRequest>
            <ChangeBeforeUpdates>PT10S</ChangeBeforeUpdates>
        </EstimatedTimetableSubscriptionRequest>
    </SubscriptionRequest>
</Siri>

```

SubscriptionResponse:
```xml
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Siri version="2.0" xmlns="http://www.siri.org.uk/siri" xmlns:ns2="http://www.ifopt.org.uk/acsb" xmlns:ns3="http://www.ifopt.org.uk/ifopt" xmlns:ns4="http://datex2.eu/schema/2_0RC1/2_0">
	<SubscriptionResponse>
		<ResponseTimestamp>2019-12-04T08:42:05.072924+01:00</ResponseTimestamp>
		<ResponderRef>ENTUR_DEV</ResponderRef>
		<RequestMessageRef>3235b6fe-5428-43e2-a75c-1782a48637d5</RequestMessageRef>
		<ResponseStatus>
			<ResponseTimestamp>2019-12-04T08:42:05.072928+01:00</ResponseTimestamp>
			<RequestMessageRef>ENTUR_DEV</RequestMessageRef>
			<SubscriptionRef>SUBSCRIPTION_ID</SubscriptionRef>
			<Status>true</Status>
		</ResponseStatus>
		<ServiceStartedTime>2019-12-04T08:39:21.201782+01:00</ServiceStartedTime>
	</SubscriptionResponse>
</Siri>
```

## Sending data
The endpoint defined by Address is to be considered a REST endpoint that expects valid SIRI-XML sent as HTTP POST. It will return "200 OK" when data is received. (The response may be sent before the data is actually processed, so invalid data will still get 200 OK - although the data may be rejected in the next step.)

All requests from the server to the client should use this endpoint, including ServiceDelivery, HeartbeatNotification.

## Sending updates
As soon as a realtime-data is updated on the server, the complete, updated message should be sent to the client.
All updates should be sent as a ServiceDelivery

Examples of ServiceDelivery skeleton-XML below, full examples can be found at https://github.com/entur/profile-norway-examples/tree/master/siri.

SIRI VM:
```xml
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Siri version="2.0" xmlns="http://www.siri.org.uk/siri" xmlns:ns2="http://www.ifopt.org.uk/acsb" xmlns:ns4="http://datex2.eu/schema/2_0RC1/2_0" xmlns:ns3="http://www.ifopt.org.uk/ifopt">
    <ServiceDelivery>
        <ResponseTimestamp>2019-12-03T15:51:54.166055+01:00</ResponseTimestamp>
        <ProducerRef>ENT</ProducerRef>
        <MoreData>true</MoreData>
        <VehicleMonitoringDelivery version="2.0">
            <ResponseTimestamp>2019-12-03T15:51:54.166057+01:00</ResponseTimestamp>
            <VehicleActivity>
                ...
            </VehicleActivity>
        </VehicleMonitoringDelivery>
    </ServiceDelivery>
</Siri>
```

SIRI ET:
```xml
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Siri version="2.0" xmlns="http://www.siri.org.uk/siri" xmlns:ns2="http://www.ifopt.org.uk/acsb" xmlns:ns4="http://datex2.eu/schema/2_0RC1/2_0" xmlns:ns3="http://www.ifopt.org.uk/ifopt">
    <ServiceDelivery>
        <ResponseTimestamp>2019-12-03T15:51:54.166055+01:00</ResponseTimestamp>
        <ProducerRef>ENT</ProducerRef>
        <MoreData>true</MoreData>
        <EstimatedTimetableDelivery version="2.0">
            <ResponseTimestamp>2019-12-03T15:51:54.166057+01:00</ResponseTimestamp>
            <EstimatedJourneyVersionFrame>
                ...
            </EstimatedJourneyVersionFrame>
        </VehicleMonitoringDelivery>
    </ServiceDelivery>
</Siri>
```

SIRI SX:
```xml
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Siri version="2.0" xmlns="http://www.siri.org.uk/siri" xmlns:ns2="http://www.ifopt.org.uk/acsb" xmlns:ns4="http://datex2.eu/schema/2_0RC1/2_0" xmlns:ns3="http://www.ifopt.org.uk/ifopt">
    <ServiceDelivery>
        <ResponseTimestamp>2019-12-03T15:51:54.166055+01:00</ResponseTimestamp>
        <ProducerRef>ENT</ProducerRef>
        <MoreData>true</MoreData>
         <SituationExchangeDelivery version="2.0">
            <ResponseTimestamp>2019-12-04T09:51:22.865727+01:00</ResponseTimestamp>
            <Situations>
                ...
            </Situations>
        </SituationExchangeDelivery>
    </ServiceDelivery>
</Siri>
```

## Keeping subscription alive
To let the client know that a subscription is still active, a HeartbeatNotification should be sent periodically (defined by interval in SubscriptionRequest - typically every minute.)

It is the client's responsibility to keep the subscription alive. This means that if the server stops sending updates/heartbeats, the client will terminate and reestablish the subscription. When the server receives a TerminateSubscriptionRequest, the server can forget all information/progress for that subscription. For the following SubscriptionRequest, all initial data should be resent as if the subscription is completely new. This is to ensure that the client is fully updated at all times.

HeartbeatNotification:
```xml
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Siri version="2.0" xmlns="http://www.siri.org.uk/siri" xmlns:ns2="http://www.ifopt.org.uk/acsb" xmlns:ns3="http://www.ifopt.org.uk/ifopt" xmlns:ns4="http://datex2.eu/schema/2_0RC1/2_0">
    <HeartbeatNotification>
        <RequestTimestamp>2019-12-03T13:25:00+01:00</RequestTimestamp>
        <ProducerRef>SUBSCRIPTION_ID</ProducerRef>
        <Status>true</Status>
        <ServiceStartedTime>2019-11-23T13:25:00+01:00</ServiceStartedTime>
    </HeartbeatNotification>
</Siri>
```

## Terminating a subscription
When terminating a subscription, the client will send a TerminateSubscriptionRequest. The server should then respond with a TerminateSubscriptionResponse, and can delete the subscription completely.

TerminateSubscriptionRequest:
```xml
<?xml version="1.0" encoding="UTF-8"?>
<Siri version="2.0" xmlns="http://www.siri.org.uk/siri" xmlns:ns2="http://www.ifopt.org.uk/acsb" xmlns:ns3="http://www.ifopt.org.uk/ifopt" xmlns:ns4="http://datex2.eu/schema/2_0RC1/2_0">
    <TerminateSubscriptionRequest>
        <RequestTimestamp>2019-12-03T13:25:00+01:00</RequestTimestamp>
        <RequestorRef>ENTUR_DEV</RequestorRef>
        <MessageIdentifier>e8fa60ea-288c-4485-97ba-a8d4f6503810</MessageIdentifier>
        <SubscriptionRef>SUBSCRIPTION_ID</SubscriptionRef>
    </TerminateSubscriptionRequest>
</Siri>
```

TerminateSubscriptionResponse:
```xml
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Siri version="2.0" xmlns="http://www.siri.org.uk/siri" xmlns:ns2="http://www.ifopt.org.uk/acsb" xmlns:ns3="http://www.ifopt.org.uk/ifopt" xmlns:ns4="http://datex2.eu/schema/2_0RC1/2_0">
	<TerminateSubscriptionResponse>
		<TerminationResponseStatus>
			<ResponseTimestamp>2019-12-04T08:33:30+01:00</ResponseTimestamp>
			<SubscriptionRef>SUBSCRIPTION_ID</SubscriptionRef>
			<Status>true</Status>
		</TerminationResponseStatus>
	</TerminateSubscriptionResponse>
</Siri>
```

## When the client dies
If the client is having unexpected problems, it may not be able to process the updates/heartbeats sent by the client (i.e. client returns something other than "200 OK"). The server should allow for a single retry in case there is a small hick-up. If the retry also fails, the server can delete the subscription, and let the client re-establish when it is ready.
