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
        <div>${body.subscription.name}</div>

        <ul>
        <#list body.validationRefs as ref>
            <li>${ref}</li>
        </#list>
        </ul>
    </div>
</div>
</body>
</html>