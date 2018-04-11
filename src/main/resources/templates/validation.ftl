<html>
<head>
    <title>Validation results</title>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <link rel="stylesheet" href="https://maxcdn.bootstrapcdn.com/bootstrap/3.3.7/css/bootstrap.min.css">
    <script src="https://ajax.googleapis.com/ajax/libs/jquery/3.1.1/jquery.min.js"></script>
    <script src="https://maxcdn.bootstrapcdn.com/bootstrap/3.3.7/js/bootstrap.min.js"></script>
</head>
<body>
<div class="jumbotron text-center">
    <h2>Validation results</h2>
    <strong>${body.subscription?exists?then(body.subscription.name, "Not found")}</strong>
</div>
<div class="container">

    <div class="row">
        <table class="table table-striped">
            <thead>
            <tr>
                <th>#</th>
                <th>Timestamp</th>
                <th>Validation errors</th>
            </tr>
            </thead>
            <tbody>
        <#list body.validationRefs as validation>
            <tr data-toggle="collapse" data-target="#accordion${validation?counter}" style="cursor: pointer" class="clickable ${validation.events?has_content?then("danger","success")}">
                <th>${validation?counter}</th>
                <td>${validation.timestamp}</td>
                <td>${validation.events?size}</td>
            </tr>
            <tr id="accordion${validation?counter}" class="collapse">
                <td colspan="3">
                    <div>
                        <a href="validation/siri?validationRef=${validation.validationRef}">Download XML</a>
                    </div>
                    <table class="table table-striped">
                        <thead>
                        <tr>
                            <th>Severity</th>
                            <th>Count</th>
                            <th>Message</th>
                            <th>Line</th>
                            <th>Column</th>
                        </tr>
                        </thead>
                        <tbody>
                            <#list validation.events?sort_by("numberOfOccurrences")?reverse as event >
                            <tr>
                                <td>${event.severity}</td>
                                <td>${event.numberOfOccurrences}</td>
                                <td>${event.message}</td>
                                <td>${event.locator.lineNumber}</td>
                                <td>${event.locator.columnNumber}</td>
                            </tr>
                            </#list>
                        </tbody>
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