<html>
<head>
    <title>Anshar statistics</title>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <link rel="stylesheet" href="https://maxcdn.bootstrapcdn.com/bootstrap/3.3.7/css/bootstrap.min.css">
    <script src="https://ajax.googleapis.com/ajax/libs/jquery/3.1.1/jquery.min.js"></script>
    <script src="https://maxcdn.bootstrapcdn.com/bootstrap/3.3.7/js/bootstrap.min.js"></script>
    <script>
        function administerSubscription(operation,id) {

            var uri = "?operation=" + operation + "&subscriptionId="+id;
            var xhr = new XMLHttpRequest();
            xhr.open('PUT', uri, true);
            xhr.onreadystatechange = function() {
                window.location.reload();
            };
            xhr.send(null);
        }
    </script>
</head>
<body>
<div class="jumbotron text-center">
    <h2>Anshar status/statistics</h2>
    <small>Server started ${body.serverStarted}</small>
    <small>(${body.secondsSinceDataReceived}s)</small>
    <br /><small>- ${body.environment} -</small>
</div>
<div class="container">

    <ul class="nav nav-tabs" id="tabs" role="tablist">
        <li class="nav-item">
            <a class="nav-link active" id="inbound-tab" data-toggle="tab" href="#inbound" role="tab" aria-controls="inbound" aria-selected="true">Inbound <span class="badge alert-success"></span> <span class="glyphicon glyphicon-arrow-down"></span> </a>
        </li>
        <li class="nav-item">
            <a class="nav-link" id="outbound-tab" data-toggle="tab" href="#outbound" role="tab" aria-controls="outbound" aria-selected="false">Outbound <span class="badge alert-success">${body.outbound?size}</span> <span class="glyphicon glyphicon-arrow-up"></span></a>
        </li>
        <li class="nav-item">
            <a class="nav-link" id="distribution-tab" data-toggle="tab" href="#distribution" role="tab" aria-controls="distribution" aria-selected="false">Distribution <span class="glyphicon glyphicon-equalizer"></span></a>
        </li>
    </ul>

    <div class="tab-content">
        <div class="tab-pane" id="inbound" role="tabpanel" aria-labelledby="inbound-tab">

        <table class="table">
            <#list body.types as type>
                        <tr><th colspan="8"><h4>${type.typeName}</h4></th></tr>

                        <tr>
                            <th>#</th>
                            <th>Status</th>
                            <th>Healthy</th>
                            <th>Activated</th>
                            <th>Vendor</th>
                            <th>Last data received</th>
                            <th>Requests</th>
                            <th>Total objects received</th>
                        </tr>
                    <#list type.subscriptions?sort_by("vendor") as item>
                        <tr data-toggle="collapse" data-target="#accordion${type?counter}-${item?counter}" style="cursor: pointer" class="clickable ${item.healthy???then(item.healthy?then("success","danger"), "warning")}">
                            <th>${item?counter}</th>
                            <td>${item.status}</td>
                            <td>${item.healthy???then(item.healthy?c,"")}</td>
                            <td>${item.activated!""}</td>
                            <td>${item.name}</td>
                            <td>${item.lastDataReceived!""} ${item.flagAsNotReceivingData?then("<span class=\"glyphicon glyphicon-alert text-warning\" title=\"Subscription is alive, but not receiving data\"></span>","")}</td>
                            <td align="right">${item.hitcount!0}</td>
                            <td align="right">${item.objectcount!0}</td>
                        </tr>
                        <tr id="accordion${type?counter}-${item?counter}" class="collapse ${item.healthy???then(item.healthy?then("success","danger"), "warning")}">
                        <td colspan="8">
                            <table class="table table-striped">
                                <tr><th>Dataset ID</th><td>${item.datasetId}</td></tr>
                                <tr><th>Vendor ID</th><td>${item.vendor}</td></tr>
                                <tr><th>Servicetype</th><td>${item.serviceType}</td></tr>
                                <tr><th>Inbound URL</th><td>${item.inboundUrl}</td></tr>
                                <tr><th>Content-Type</th><td>${item.contentType}</td></tr>
                                <tr><th>Heartbeat interval</th><td>${item.heartbeatInterval}</td></tr>
                                <tr><th>Preview interval</th><td>${item.previewInterval}</td></tr>
                                <tr><th>Change before updates</th><td>${item.changeBeforeUpdates}</td></tr>
                                <tr><th>Incremental updates</th><td>${item.incrementalUpdates}</td></tr>
                                <tr><th>Update interval</th><td>${item.updateInterval}</td></tr>
                                <tr><th>Duration</th><td>${item.durationOfSubscription}</td></tr>
                                <tr><th>Restart time</th><td>${item.restartTime!""}</td></tr>
                                <tr><th>Type</th><td>${item.subscriptionType}</td></tr>
                                <tr><th>Id</th><td>${item.subscriptionId}</td></tr>
                                <tr><th>RequestorRef</th><td>${item.requestorRef}</td></tr>
                                <tr><th>Mode</th><td>${item.subscriptionMode}</td></tr>
                                <tr><th>Validation active</th><td>${item.validation?c}</td></tr>
                                <tr>
                                    <th>URLs</th>
                                    <td>
                                        <table width="80%">
                                        <#list item.urllist?keys as label>
                                           <tr><td>${label}</td><td>${item.urllist[label]}</td></tr>
                                        </#list>
                                        </table>
                                    </td>
                                </tr>
                            </table>
                            <table border="0" width="100%">
                                <tr>
                                    <td>
                                        <#if item.status=='deactivated'>
                                            <button type="button" class="btn btn-danger" disabled onclick="administerSubscription('stop', '${item.subscriptionId}')">Stop</button>
                                            <button type="button" class="btn btn-success" onclick="administerSubscription('start', '${item.subscriptionId}')">Start</button>
                                        <#else >
                                            <button type="button" class="btn btn-danger"  onclick="administerSubscription('stop', '${item.subscriptionId}')">Stop</button>
                                            <button type="button" class="btn btn-success" disabled onclick="administerSubscription('start', '${item.subscriptionId}')">Start</button>
                                        </#if>
                                    </td>
                                    <td>
                                        <div align="right">
                                            <span style="cursor: pointer"  class="glyphicon glyphicon-trash" onclick="administerSubscription('flush', '${item.subscriptionId}')"></span>
                                        </div>
                                    </td>
                                </tr>
                            </table>
                        </td>
                        </tr>
                    </#list>
                </#list>
            </table>
        </div>

        <div class="tab-pane" id="outbound" role="tabpanel" aria-labelledby="outbound-tab">
            <div class="row">
                <table class="table table-striped">
                    <thead>
                    <tr>
                        <th>#</th>
                        <th>SubscriptionRef</th>
                        <th>Address</th>
                        <th>DatasetId</th>
                        <th>Type</th>
                        <th>Heartbeat</th>
                        <th>Activated</th>
                        <th>Terminationtime</th>
                        <th></th>
                    </tr>
                    </thead>
                    <tbody>
                    <#list body.outbound?sort_by("subscriptionRef") as item>
                    <tr>
                        <th>${item?counter}</th>
                        <td>${item.subscriptionRef}</td>
                        <td><a href="${item.address}">URL</a></td>
                        <td>${item.datasetId}</td>
                        <td>${item.subscriptionType}</td>
                        <td>${item.heartbeatInterval}</td>
                        <td>${item.requestReceived}</td>
                        <td>${item.initialTerminationTime}</td>
                        <td>
                            <span style="cursor: pointer"  class="glyphicon glyphicon-trash" onclick="administerSubscription('terminate', '${item.subscriptionRef}')"></span>
                        </td>
                    </tr>
                    </#list>
                    </tbody>
                </table>
            </div>
        </div>
        <div class="tab-pane" id="distribution" role="tabpanel" aria-labelledby="distribution-tab">
            <div class="row">
                <h4>Active data per codespace:</h4>
                <table class="table table-striped">
                    <thead>
                    <tr>
                        <th>Codespace</th>
                        <td align="right"><b>ET</b></td>
                        <td align="right"><b>VM</b></td>
                        <td align="right"><b>SX</b></td>
                    </tr>
                    </thead>
                    <tbody>
                    <#list body.elements.distribution?sort_by("datasetId") as item>
                    <tr>
                        <th>${item.datasetId}</th>
                        <td align="right">${item.etCount}</td>
                        <td align="right">${item.vmCount}</td>
                        <td align="right">${item.sxCount}</td>
                    </tr>
                    </#list>
                    <tr>
                        <th>Total</th>
                        <td align="right">${body.elements.et}</td>
                        <td align="right">${body.elements.vm}</td>
                        <td align="right">${body.elements.sx}</td>
                    </tr>
                    <tr><td colspan="4"></td> </tr>
                    <tr>
                        <th>Polling clients</th>
                        <td align="right">${body.elements.etChanges}</td>
                        <td align="right">${body.elements.vmChanges}</td>
                        <td align="right">${body.elements.sxChanges}</td>
                    </tr>
                    </tbody>
                </table>
            </div>
        </div>
    </div>
</div>
</body>
<script>
    $(function () {
        $('#tabs li:first-child a').tab('show')
    })
</script>
</html>