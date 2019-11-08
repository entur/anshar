// Check if et-client-name header exists
var identifier = context.getVariable("request.header.et-client-name"); // header value 

// Get pathSuffix
var pathSuffix = context.getVariable("proxy.pathsuffix");

// Get message verb
var messageVerb = context.getVariable("message.verb");

// if no header value, set based on client-ip
if (!identifier) {
  identifier = "unknown-" + context.getVariable("client.ip")
}

// Set ratelimit rules based on path and verb
if (messageVerb == "GET" && (pathSuffix == "/rest/vm" || pathSuffix == "/rest/et" || pathSuffix == "/rest/sx")) {
  // Set ratelimit Values
  var rateLimitValues = [{
    "id": "unkown",
    "quota": "5",
    "spikeArrest": "4ps",
  },
  {
    "id": "others",
    "quota": "5",
    "spikeArrest": "4ps",
  }];
  identifier = identifier + "-" + pathSuffix.split('/').pop();
} else if (messageVerb == "POST" && pathSuffix == "/services") {
  // Set ratelimit Values
  var rateLimitValues = [{
    "id": "unkown",
    "quota": "50",
    "spikeArrest": "50ps",
  },
  {
    "id": "others",
    "quota": "50",
    "spikeArrest": "50ps",
  }];
  identifier = identifier + "-" + pathSuffix.split('/').pop();
} else {
  var rateLimitValues = [{
    "id": "unkown",
    "quota": "4000",
    "spikeArrest": "400ps",
  },
  {
    "id": "others",
    "quota": "4000",
    "spikeArrest": "400ps",
  }];
}

// Create flow variable
context.setVariable("rateLimitValues", JSON.stringify(rateLimitValues));
context.setVariable("request.header.Et-Client-Name", identifier);
context.setVariable("rateLimitValuesHeaderIdentifier", "Et-Client-Name");
