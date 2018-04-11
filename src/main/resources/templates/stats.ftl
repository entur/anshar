<html>
<head>
    <title>Anshar statistics</title>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <link rel="stylesheet" href="https://maxcdn.bootstrapcdn.com/bootstrap/3.3.7/css/bootstrap.min.css">
    <script src="https://ajax.googleapis.com/ajax/libs/jquery/3.1.1/jquery.min.js"></script>
    <script src="https://maxcdn.bootstrapcdn.com/bootstrap/3.3.7/js/bootstrap.min.js"></script>
    <script>
        function administerSubscription(operation,id,reload) {

            var uri = operation + "?subscriptionId="+id;
            var xhr = new XMLHttpRequest();
            xhr.open('PUT', uri, true);
            xhr.onreadystatechange = function() {
                if (reload) {
                    window.location.reload();
                }
            };
            xhr.send(null);
        }
    </script>
    <style>
        /* The switch - the box around the slider */
        .switch {
            position: relative;
            display: inline-block;
            width: 34px;
            height: 19px;
        }

        /* Hide default HTML checkbox */
        .switch input {display:none;}

        /* The slider */
        .slider {
            position: absolute;
            cursor: pointer;
            top: 0;
            left: 0;
            right: 0;
            bottom: 0;
            background-color: #ccc;
            -webkit-transition: .4s;
            transition: .4s;
        }

        .slider:before {
            position: absolute;
            content: "";
            height: 15px;
            width: 15px;
            left: 2px;
            bottom: 2px;
            background-color: white;
            -webkit-transition: .4s;
            transition: .4s;
        }

        input:checked + .slider {
            background-color: #2196F3;
        }

        input:focus + .slider {
            box-shadow: 0 0 1px #2196F3;
        }

        input:checked + .slider:before {
            -webkit-transform: translateX(15px);
            -ms-transform: translateX(15px);
            transform: translateX(15px);
        }

        /* Rounded sliders */
        .slider.round {
            border-radius: 19px;
        }

        .slider.round:before {
            border-radius: 50%;
        }
    </style>
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
                <th>#</th>
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
                <th>${item?counter}</th>
                <td>${item.status}</td>
                <td>${item.healthy?exists?then(item.healthy?c,"")}</td>
                <td>${item.activated!""}</td>
                <td>${item.name}</td>
                <td>${item.lastDataReceived!""} ${item.flagAsNotReceivingData?then("<span class=\"glyphicon glyphicon-alert text-warning\"></span>","")}</td>
                <td align="right">${item.hitcount!0}</td>
                <td align="right">${item.objectcount!0}</td>
            </tr>
            <tr id="accordion${item?counter}" class="collapse ${item.healthy?exists?then(item.healthy?then("success","danger"), "warning")}">
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
                    <tr><th>Type</th><td>${item.subscriptionType}</td></tr>
                    <tr><th>Id</th><td>${item.subscriptionId}</td></tr>
                    <tr><th>RequestorRef</th><td>${item.requestorRef}</td></tr>
                    <tr><th>Mode</th><td>${item.subscriptionMode}</td></tr>
                    <tr><th><a tabindex="-1" href="validation?subscriptionId=${item.subscriptionId}" target="_blank">Validation active</a></th>
                        <td>
                            <label class="switch">
                                <input type="checkbox" tabindex="-1" ${item.validation?then("checked", "")} onchange="administerSubscription('toggle-validate', '${item.subscriptionId}', false)">
                                <span class="slider round"></span>
                            </label>
                        </td>
                    </tr>
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
                    <button type="button" class="btn btn-danger" disabled onclick="administerSubscription('stop', '${item.subscriptionId}', true)">Stop</button>
                    <button type="button" class="btn btn-success" onclick="administerSubscription('start', '${item.subscriptionId}', true)">Start</button>
                <#else >
                    <button type="button" class="btn btn-danger"  onclick="administerSubscription('stop', '${item.subscriptionId}', true)">Stop</button>
                    <button type="button" class="btn btn-success" disabled onclick="administerSubscription('start', '${item.subscriptionId}', true)">Start</button>
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
            <div class="col-md-1"><label>Polling clients:</label></div>
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