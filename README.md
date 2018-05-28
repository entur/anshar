# anshar

A pub-sub hub for SIRI data. It will connect and subscribe on SIRI endpoints,
and also supply external endpoints.

The name Anshar comes from the Sumerian primordial god.

``` 
anshar.incoming.logdirectory=/deployments/incoming
anshar.incoming.port = 8012
anshar.inbound.url = http://mottak.rutebanken.org/anshar
``` 

## Liveness/readiness
```
- http://<host>:<port>/up
- http://<host>:<port>/ready
```

## Healthcheck
```
- http://<host>:<port>/healthy
```

## Statistics
To view status/statistics for all subscriptions.
```
- http://<host>:<port>/anshar/stats
```
To view status/statistics (JSON-format) for all clustering.
```
- http://<host>:<port>/anshar/clusterstats
```

## Validation
Anshar supports validating all incoming XML against the SIRI XSD and a customizable subset (default is the norwegian SIRI Profile)
Validation is administered per codespace - and is accessible on:
```
- http://<host>:<port>/anshar/validation/ENT
```

