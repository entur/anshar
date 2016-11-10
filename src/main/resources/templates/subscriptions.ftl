<html>
<head>
    <title>Anshar subscriptions</title>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <link rel="stylesheet" href="https://maxcdn.bootstrapcdn.com/bootstrap/3.3.7/css/bootstrap.min.css">
    <script src="https://ajax.googleapis.com/ajax/libs/jquery/3.1.1/jquery.min.js"></script>
    <script src="https://maxcdn.bootstrapcdn.com/bootstrap/3.3.7/js/bootstrap.min.js"></script>
</head>
<body>
<div class="jumbotron text-center">
    <h2>Anshar subscriptions</h2>
</div>
<div class="container">
    <div class="row">
    <table class="table table-striped">
        <thead>
            <tr>
                <th>SubscriptionRef</th>
                <th>Address</th>
                <th>Type</th>
                <th>Heartbeatinterval</th>
                <th>Activated</th>
                <th>Terminationtime</th>
            </tr>
        </thead>
        <tbody>
            <#list body as item>
                <tr>
                    <td>${item.subscriptionRef}</td>
                    <td>${item.address}</td>
                    <td>${item.subscriptionType}</td>
                    <td>${item.heartbeatInterval}</td>
                    <td>${item.requestReceived}</td>
                    <td>${item.initialTerminationTime}</td>
                </tr>
            </#list>
        </tbody>
        </table>
        </div>
    </div>
</body>
</html>