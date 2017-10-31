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

            var uri = operation + "?subscriptionId="+id;
            var xhr = new XMLHttpRequest();
            xhr.open('PUT', uri, true);
            xhr.onreadystatechange = function() {
                window.location.reload()
            }
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

    <div class="row">
    <table class="table">
        <thead>
            <tr>
                <th>Status</th>
                <th>Healthy</th>
                <th>Activated</th>
                <th>Vendor</th>
                <th>Last data received</th>
                <th>Requests</th>
                <th>Total objects received</th>
            </tr>
        </thead>
        <tbody>
        <#list body.subscriptions?sort_by("vendor") as item>
            <tr data-toggle="collapse" data-target="#accordion${item?counter}" style="cursor: pointer" class="clickable ${item.healthy?exists?then(item.healthy?then("success","danger"), "warning")}">
                <td>${item.status}</td>
                <td>${item.healthy?exists?then(item.healthy?c,"")}</td>
                <td>${item.activated!""}</td>
                <td>${item.vendor}</td>
                <td>${item.lastDataReceived!""} ${item.flagAsNotReceivingData?then("<span class=\"glyphicon glyphicon-alert\"></span>","")}</td>
                <td align="right">${item.hitcount!0}</td>
                <td align="right">${item.objectcount!0}</td>
            </tr>
            <tr id="accordion${item?counter}" class="collapse ${item.healthy?exists?then(item.healthy?then("success","danger"), "warning")}">
            <td colspan="7">
                <table class="table table-striped">
                    <tr><th>Dataset ID</th><td>${item.datasetId}</td></tr>
                    <tr><th>Servicetype</th><td>${item.serviceType}</td></tr>
                    <tr><th>Inbound URL</th><td>${item.inboundUrl}</td></tr>
                    <tr><th>Content-Type</th><td>${item.contentType}</td></tr>
                    <tr><th>Heartbeat interval</th><td>${item.heartbeatInterval}</td></tr>
                    <tr><th>Preview interval</th><td>${item.previewInterval}</td></tr>
                    <tr><th>Change before updates</th><td>${item.changeBeforeUpdates}</td></tr>
                    <tr><th>Incremental updates</th><td>${item.incrementalUpdates}</td></tr>
                    <tr><th>Update interval</th><td>${item.updateInterval}</td></tr>
                    <tr><th>Duration</th><td>${item.durationOfSubscription}</td></tr>
                    <tr><th>Type</th><td>${item.subscriptionType}</td></tr>
                    <tr><th>Id</th><td>${item.subscriptionId}</td></tr>
                    <tr><th>RequestorRef</th><td>${item.requestorRef}</td></tr>
                    <tr><th>Mode</th><td>${item.subscriptionMode}</td></tr>
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
                <#if item.status=='deactivated'>
                    <button type="button" class="btn btn-danger" disabled onclick="administerSubscription('stop', '${item.subscriptionId}')">Stop</button>
                    <button type="button" class="btn btn-success" onclick="administerSubscription('start', '${item.subscriptionId}')">Start</button>
                <#else >
                    <button type="button" class="btn btn-danger"  onclick="administerSubscription('stop', '${item.subscriptionId}')">Stop</button>
                    <button type="button" class="btn btn-success" disabled onclick="administerSubscription('start', '${item.subscriptionId}')">Start</button>
                </#if>
            </td>
            </tr>
        </#list>
        </tbody>
    </table>
    </div>
    <div class="row">

        <div class="row">
            <div class="col-md-2"><label>Active elements:</label></div>
            <div class="col-md-1"><label>Clients:</label></div>
        </div>
        <div class="row">
            <div class="col-md-1">SX</div>
            <div class="col-md-1">${body.elements.sx}</div>
            <div class="col-md-1">${body.elements.sxChanges}</div>
        </div>
        <div class="row">
            <div class="col-md-1">VM</div>
            <div class="col-md-1">${body.elements.vm}</div>
            <div class="col-md-1">${body.elements.vmChanges}</div>
        </div>
        <div class="row">
            <div class="col-md-1">ET</div>
            <div class="col-md-1">${body.elements.et}</div>
            <div class="col-md-1">${body.elements.etChanges}</div>
        </div>
        <div class="row">
            <div class="col-md-1">PT</div>
            <div class="col-md-1">${body.elements.pt}</div>
            <div class="col-md-1">${body.elements.ptChanges}</div>
        </div>
    </div>
</div>
</body>
</html>