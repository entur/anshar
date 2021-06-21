<html>
<head>
    <title>Unmapped IDs</title>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <link rel="stylesheet" href="https://maxcdn.bootstrapcdn.com/bootstrap/3.3.7/css/bootstrap.min.css">
    <script src="https://ajax.googleapis.com/ajax/libs/jquery/3.1.1/jquery.min.js"></script>
    <script src="https://maxcdn.bootstrapcdn.com/bootstrap/3.3.7/js/bootstrap.min.js"></script>

</head>
<body>
<div class="jumbotron text-center">
    <h2>Unmapped IDs</h2>
</div>
<div class="container">

    <div class="row">
    <table class="table">
        <thead>
            <tr>
                <th>Type</th>
                <th align="right">Count</th>
            </tr>
        </thead>
        <tbody>
        <#list body.unmapped?sort_by("type") as item>

            <tr data-toggle="collapse" data-target="#accordion${item?counter}" style="cursor: pointer" class="clickable ${item.healthy???then(item.healthy?then("success","danger"), "warning")}">
                <td>${item.type}</td>
                <td align="right">${item.count!0}</td>
            </tr>
            <tr id="accordion${item?counter}" class="collapse">
            <td colspan="2">
                <table width="100%" border="1">
                    <#list item.ids?chunk(8) as row>
                        <tr>
                            <#list row as cell><td>${cell}</td></#list>
                        </tr>
                    </#list>
                </table>
            </td>
            </tr>
        </#list>
        </tbody>
    </table>
    </div>
    <div class="row">
        <sup><span class="glyphicon glyphicon-info-sign text-info"></span></sup>
        Unmapped ids with "NSR"-prefix indicates that the id references a stop/quay that has been shut down, and is no longer valid.
    </div>
</div>
</body>
</html>