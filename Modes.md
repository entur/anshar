# App-modes
Anshar now supports being started in different _modes_ to allow separate instances for each datatype -  both for reducing the necessary data-synchronization between instances, and making it possible to scale parts of the application separately. 

Possible modes are PROXY, DATA_ET, DATA_VM, DATA_SX (from enum `AppMode`) 

Recommended (i.e. tested) usage is either all modes in one application, or separate instances for each mode. 

## PROXY
- Subscription-management.
  - Starts/stops subscriptions
  - Monitors heartbeats/deliveries from external data-producers, restarts subscriptions when necessary
  - Fetches data from Polling/Fetched delivery-subscriptions
- Receives all incoming SIRI-data
- Applies mapping-rules (including lists of unmapped ids)
- Manages profile-validator 
- Publishes processed data to pubsub-topic
- Forwards client-requests to correct service based on the data-type requested.
- Forwards SubscriptionRequests from external clients to correct service
- Does not keep/hold any actual data
- Requires config of baseUrls to data-handlers
    - `anshar.data.handler.baseurl.et`, `-.vm`, `-.sx`
    
## DATA_ET, DATA_VM, DATA_SX
- Each mode handles its own datatype
- Reads processed data from related pubsub-topic (as published from PROXY)
- Adds updated objects to internal maps (holds all data in memory)
- Publishes data to 
  - External subscribers
  - Pubsub
  - Kafka