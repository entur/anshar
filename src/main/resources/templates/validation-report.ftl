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
    <strong>${(body?? && body.subscription??)?then(body.subscription.name, "Subscription not found")}</strong>
    <h4>${(body?? && body.filter??)?then('Filter: ${body.filter}', "No filter")}</h4>

    <#if body.status?? >
        <span>Validation active: ${body.status.validationActive?c}</span>
        <span>-</span>
        <span>Size: ${body.status.currentSize} / ${body.status.maxSize}</span>
        <span>-</span>
        <span>Count: ${body.status.currentValidations} / ${body.status.maxValidations}</span>
    </#if>

</div>
<#if body?? >
<div class="container">

    <div class="row">
        <table class="table table-striped">
            <thead>
            <tr>
                <th>#</th>
                <th>Timestamp</th>
                <th>Schema validation errors</th>
                <th>Profile validation errors</th>
                <th>XML</th>
            </tr>
            </thead>
            <tbody >
                <#list body.validationRefs as validation>
                <tr style="cursor: pointer" class="${(validation.schema?? && validation.profile?? && (validation.schema.errorCount + validation.profile.errorCount) != 0)?then("danger","success")}">
                    <th data-toggle="collapse" data-target="#accordion${validation?counter}" >${validation?counter}</th>
                    <td data-toggle="collapse" data-target="#accordion${validation?counter}" >${validation.schema.timestamp?number_to_datetime}</td>
                    <td data-toggle="collapse" data-target="#accordion${validation?counter}" >${validation.schema.errorCount?c}</td>
                    <td data-toggle="collapse" data-target="#accordion${validation?counter}" >${validation.profile.errorCount?c}</td>
                    <td><a href="siri?validationRef=${validation.validationRef}">XML <span class="glyphicon glyphicon-download"></span></a></td>
                </tr>
                <tr id="accordion${validation?counter}" class="collapse">
                    <td colspan="5">
                        <#if validation.schema.events?has_content >
                            <fieldset>
                                <legend>Schema validation</legend>
                                <table class="table table-sm">
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
                                        <#list validation.schema.events as event >
                                        <tr class="warning">
                                            <td>${event.severity}</td>
                                            <td>${event.numberOfOccurrences}</td>
                                            <td>${event.message}</td>
                                            <td>${event.locator.lineNumber}</td>
                                            <td>${event.locator.columnNumber}</td>
                                        </tr>
                                        </#list>
                                    </tbody>
                                </table>
                            </fieldset>
                        </#if>
                        <#if validation.profile.categories?has_content >
                            <fieldset>
                                <legend>Profile validation <sup><span class="glyphicon glyphicon-info-sign text-info" title="Click row for details"></span></sup></legend>
                                <table class="table table-sm">
                                    <thead>
                                    <tr>
                                        <th colspan="2">Category</th>
                                        <th>Count</th>
                                    </tr>
                                    </thead>
                                    <tbody>
                                        <#list validation.profile.categories?sort_by("category") as category >
                                        <tr style="cursor: pointer" data-toggle="collapse" data-target="#accordion-category-${validation?counter}-${category?counter}" class="warning">
                                            <td colspan="2">${category.category}</td>
                                            <td>${category.events?size}</td>
                                        </tr>
                                        <tr id="accordion-category-${validation?counter}-${category?counter}" class="collapse">
                                            <td colspan="3" >
                                                <table class="table table-sm">
                                                    <thead>
                                                    <tr>
                                                        <th>Severity</th>
                                                        <th>Count</th>
                                                        <th>Message</th>
                                                    </tr>
                                                    </thead>
                                                    <tbody>
                                                    <#list category.events?sort_by("numberOfOccurrences")?reverse as event >
                                                    <tr>
                                                        <td>${event.severity}</td>
                                                        <td>${event.numberOfOccurrences}</td>
                                                        <td>${event.message}</td>
                                                    </tr>
                                                    </#list>
                                                    </tbody>
                                                </table>
                                            </td>
                                        </tr>
                                        </#list>
                                    </tbody>
                                </table>
                            </fieldset>
                        </#if>
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