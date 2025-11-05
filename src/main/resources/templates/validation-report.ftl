<html>
<head>
    <title>Validation results</title>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.2/dist/css/bootstrap.min.css" rel="stylesheet" integrity="sha384-T3c6CoIi6uLrA9TneNEoa7RxnatzjcDSCmG1MXxSR1GAsXEV/Dwwykc2MPK8M2HN" crossorigin="anonymous">
    <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/bootstrap-icons@1.11.3/font/bootstrap-icons.min.css">
    <script src="https://cdn.jsdelivr.net/npm/bootstrap@5.3.2/dist/js/bootstrap.bundle.min.js" integrity="sha384-C6RzsynM9kWDrMNeT87bh95OGNyZPhcTNXj1NW7RuBCsyN/o0jlpcV8Qyq46cDfL" crossorigin="anonymous"></script>
    <style>
        .clickable-row {
            cursor: pointer;
        }
    </style>
</head>
<body>
<div class="bg-light p-5 rounded-3 text-center mb-4">
    <h2>Validation results</h2>
    <strong>${(body?? && body.subscription??)?then(body.subscription.name, "Subscription not found")}</strong>
    <h4>${(body?? && body.filter??)?then('Filter: ${body.filter}', "No filter")?html}</h4>

    <#if body.status?? >
        <span>Validation active: ${body.status.validationActive?c}</span>
        <span>-</span>
        <span>Size: ${body.status.currentSize} / ${body.status.maxSize}</span>
        <span>-</span>
        <span>Count: ${body.status.currentValidations} / ${body.status.maxValidations}</span>
    </#if>

    <div class="mt-3 text-center">
        <div class="form-check form-switch d-inline-block">
            <input class="form-check-input" type="checkbox" id="autoRefresh">
            <label class="form-check-label" for="autoRefresh">
                Auto-refresh (30s)
            </label>
        </div>
    </div>

</div>
<#if body?? >
<div class="container">

    <div class="row">
        <table class="table table-striped">
            <thead>
            <tr>
                <th class="text-center">#</th>
                <th>Timestamp</th>
                <th class="text-center">Schema validation errors</th>
                <th class="text-center">Profile validation errors</th>
                <th>XML</th>
            </tr>
            </thead>
            <tbody >
                <#list body.validationRefs as validation>
                <tr class="clickable-row ${(validation.schema?? && validation.profile?? && (validation.schema.errorCount + validation.profile.errorCount) != 0)?then("table-danger","table-success")}">
                    <th class="text-center" data-bs-toggle="collapse" data-bs-target="#accordion${validation?counter}" >${validation?counter}</th>
                    <td data-bs-toggle="collapse" data-bs-target="#accordion${validation?counter}" >${validation.schema.timestamp?number_to_datetime?datetime?string("MMM d, yyyy, HH:mm:ss ")}</td>
                    <td class="text-center" data-bs-toggle="collapse" data-bs-target="#accordion${validation?counter}" >${validation.schema.errorCount?c}</td>
                    <td class="text-center" data-bs-toggle="collapse" data-bs-target="#accordion${validation?counter}" >${validation.profile.errorCount?c}</td>
                    <td><a href="siri?validationRef=${validation.validationRef}">XML <span class="bi bi-cloud-arrow-down-fill text-success ms-2"></span></a></td>
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
                                        <tr class="table-warning">
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
                                <legend>Profile validation <sup><span class="bi bi-info-circle text-info" title="Click row for details"></span></sup></legend>
                                <table class="table table-sm">
                                    <thead>
                                    <tr>
                                        <th colspan="2">Category</th>
                                        <th>Count</th>
                                    </tr>
                                    </thead>
                                    <tbody>
                                        <#list validation.profile.categories?sort_by("category") as category >
                                        <tr class="clickable-row table-warning" data-bs-toggle="collapse" data-bs-target="#accordion-category-${validation?counter}-${category?counter}">
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

<script>
    // Auto-refresh functionality
    document.addEventListener('DOMContentLoaded', function() {
        const autoRefreshToggle = document.getElementById('autoRefresh');
        let autoRefreshInterval;

        if (autoRefreshToggle) {
            autoRefreshToggle.addEventListener('change', function() {
                if (this.checked) {
                    // Start auto-refresh
                    autoRefreshInterval = setInterval(function() {
                        window.location.reload();
                    }, 30000); // 30 seconds
                    console.log('Auto-refresh enabled (30s)');
                } else {
                    // Stop auto-refresh
                    if (autoRefreshInterval) {
                        clearInterval(autoRefreshInterval);
                        console.log('Auto-refresh disabled');
                    }
                }
            });
        }
    });
</script>

</body>
</html>