# anshar

A pub-sub hub for SIRI data. It will connect and subscribe on SIRI endpoints,
and also supply external endpoints.

The name Anshar comes from the Sumerian primordial god.

``` 
anshar.incoming.logdirectory=target/incoming
anshar.incoming.port = 8012
anshar.inbound.url = http://mottak.rutebanken.org/anshar
``` 

# Liveness/readiness
```
- http://<host>:<port>/up
- http://<host>:<port>/ready
```


# Stats/statistics
To view status/statistics (JSON-format) for all subscriptions.
```
- http://<host>:<port>/anshar/stats
```

# XML Validation
Anshar provides a REST-api for validating SIRI-xml against the SIRI xsd.
```
- http://<host>:<port>/anshar/rest/sirivalidator?version=[SIRI version]
[SIRI version] Possible values: 1.0, 1.3, 1.4, 2.0
If not provided version is set to 2.0
```
