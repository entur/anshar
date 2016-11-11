<html>
<head>
    <title>Anshar statistics</title>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <link rel="stylesheet" href="https://maxcdn.bootstrapcdn.com/bootstrap/3.3.7/css/bootstrap.min.css">
    <script src="https://ajax.googleapis.com/ajax/libs/jquery/3.1.1/jquery.min.js"></script>
    <script src="https://maxcdn.bootstrapcdn.com/bootstrap/3.3.7/js/bootstrap.min.js"></script>
</head>
<body>
<div class="jumbotron text-center">
    <h2>Anshar statistics</h2>
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
                <th>Last activity</th>
                <th>Hitcount</th>
            </tr>
        </thead>
        <tbody>
            <#list body as item>
                <tr data-toggle="collapse" data-target="#accordion${item?counter}"  class="clickable ${item.healthy?then("success","danger")}">
                    <td>${item.status}</td>
                    <td>${item.healthy?c}</td>
                    <td>${item.activated!""}</td>
                    <td>${item.vendor}</td>
                    <td>${item.lastActivity!""}</td>
                    <td>${item.hitcount!0}</td>
                </tr>
                <tr id="accordion${item?counter}" class="collapse ${item.healthy?then("success","danger")}">
                <td colspan="6">
                    <table class="table table-striped">
                        <tr><th>Dataset ID</th><td>${item.datasetId}</td></tr>
                        <tr><th>Servicetype</th><td>${item.serviceType}</td></tr>
                        <tr><th>Heartbeat interval</th><td>${item.heartbeatInterval}</td></tr>
                        <tr><th>Duration</th><td>${item.durationOfSubscription}</td></tr>
                        <tr><th>Type</th><td>${item.subscriptionType}</td></tr>
                        <tr><th>Id</th><td>${item.subscriptionId}</td></tr>
                        <tr><th>Mode</th><td>${item.subscriptionMode}</td></tr>
                    </table>
                </td>
                </tr>
            </#list>
        </tbody>
        </table>
        </div>
    </div>
</body>
</html>