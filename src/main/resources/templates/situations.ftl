<html>
<head>
    <title>SIRI Situations</title>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.2/dist/css/bootstrap.min.css" rel="stylesheet" integrity="sha384-T3c6CoIi6uLrA9TneNEoa7RxnatzjcDSCmG1MXxSR1GAsXEV/Dwwykc2MPK8M2HN" crossorigin="anonymous">
    <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/bootstrap-icons@1.11.3/font/bootstrap-icons.min.css">
    <script src="https://cdn.jsdelivr.net/npm/bootstrap@5.3.2/dist/js/bootstrap.bundle.min.js" integrity="sha384-C6RzsynM9kWDrMNeT87bh95OGNyZPhcTNXj1NW7RuBCsyN/o0jlpcV8Qyq46cDfL" crossorigin="anonymous"></script>
    <style>
        .situations-header {
            background-color: #f8f9fa;
            padding: 3rem;
            border-radius: 0.5rem;
            text-align: center;
            margin-bottom: 1.5rem;
        }

        .clickable-row {
            cursor: pointer;
        }

        .table-cell-middle {
            vertical-align: middle !important;
        }

        @media (max-width: 768px) {
            .situations-header {
                padding: 2rem 1rem;
            }

            .table-responsive {
                overflow-x: auto;
            }
        }
    </style>
</head>
<body>
<div class="situations-header">
    <h2>SIRI Situations</h2>
</div>
<#if body?? >
<div class="container">
    <div class="row">
        <div class="table-responsive">
        <table class="table">
            <thead>
            <tr>
                <th>#</th>
                <th>SituationNumber</th>
                <th>Progress</th>
                <th>Text</th>
            </tr>
            </thead>
            <tbody>
                <#list body.situations?sort_by("situationNumber") as item>
                    <tr data-bs-toggle="collapse" data-bs-target="#accordion${item?counter}"
                        class="clickable-row ${(item.progress == "OPEN")?then("table-success", "table-danger")}">
                        <th class="table-cell-middle">${item?counter}</th>
                        <td class="table-cell-middle">${item.situationNumber}</td>
                        <td class="table-cell-middle">${item.progress}</td>
                        <td class="table-cell-middle">
                            <#list item.summaries as summary>
                                <span>${summary.value}</span><br />
                            </#list>
                        </td>
                    </tr>
                    <tr id="accordion${item?counter}" class="collapse ${(item.progress == "OPEN")?then("table-success", "table-danger")}">
                        <td colspan="4">
                            <table class="table table-sm table-striped">
                                <tr>
                                    <td>
                                        <div>
                                            <label class="fw-bold">Summary:</label><br />
                                            <#list item.summaries as summary>
                                                <span class="text-muted">${summary.lang}</span> <span>${summary.value}</span><br />
                                            </#list>
                                        </div>
                                        <div class="mt-2">
                                            <label class="fw-bold">Description:</label><br />
                                            <#list item.descriptions as description>
                                                <span class="text-muted">${description.lang}</span> <span>${description.value}</span><br />
                                            </#list>
                                        </div>
                                        <div class="mt-2">
                                            <label class="fw-bold">Advice:</label><br />
                                            <#list item.advices as advice>
                                                <span class="text-muted">${advice.lang}</span> <span>${advice.value}</span><br />
                                            </#list>
                                        </div>
                                    </td>
                                    <td>
                                        <label class="fw-bold">CreationTime:</label><br />
                                        <span>${item.creationTime}</span><br />
                                        <#list item.validity as validity>
                                            <label class="fw-bold">StartTime:</label><br />
                                            <span>${validity.startTime}</span><br />
                                            <label class="fw-bold">EndTime:</label><br />
                                            <span>${validity.endTime}</span><br />
                                        </#list>
                                    </td>
                                </tr>
                            </table>
                        </td>
                    </tr>
                </#list>
            </tbody>
        </table>
        </div>
    </div>
</div>
</#if>
</body>
</html>