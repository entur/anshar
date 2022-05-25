<html>
<head>
    <title>SIRI Situations</title>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <link rel="stylesheet" href="https://maxcdn.bootstrapcdn.com/bootstrap/3.3.7/css/bootstrap.min.css">
    <script src="https://ajax.googleapis.com/ajax/libs/jquery/3.1.1/jquery.min.js"></script>
    <script src="https://maxcdn.bootstrapcdn.com/bootstrap/3.3.7/js/bootstrap.min.js"></script>
</head>
<body>
<div class="jumbotron text-center">
    <h2>SIRI Situations</h2>
</div>
<#if body?? >
<div class="container">

    <div class="row">
        <table class="table">
            <thead>
            <tr>
                <th>#</th>
                <th>SituationNumber</th>
                <th>Progress</th>
                <th>Text</th>
                <th>Timestamps</th>
            </tr>
            </thead>
            <tbody>
                <#list body.situations?sort_by("situationNumber") as item>
                    <#if item.progress == "OPEN">
                        <tr class="success">
                    <#else>
                        <tr class="danger">
                    </#if>
                    <th>${item?counter}</th>
                    <td>${item.situationNumber}</td>
                    <td>${item.progress}</td>
                    <td>
                        <div>
                        <label>Summary:</label><br />
                        <#list item.summaries as summary>
                            <span>${summary.lang}</span>:<span>${summary.value}</span><br />
                        </#list>
                        </div>
                        <div>
                            <label>Description:</label><br />
                        <#list item.descriptions as description>
                            <span>${description.lang}</span>:<span>${description.value}</span><br />
                        </#list>
                        </div>
                        <div>
                            <label>Advice:</label><br />
                        <#list item.advices as advice>
                            <span>${advice.lang}</span>:<span>${advice.value}</span><br />
                        </#list>
                        </div>
                    </td>
                    <td>
                        <label>CreationTime:</label><br />
                        <span>${item.creationTime}</span><br />
                        <#list item.validity as validity>
                            <label>StartTime:</label><br />
                            <span>${validity.startTime}</span><br />
                            <label>EndTime:</label><br />
                            <span>${validity.endTime}</span><br />
                        </#list>
                    </td>
                </tr>
                </#list>
            </tbody>
        </table>
    </div>
</div>

</#if>
</body>
</html>