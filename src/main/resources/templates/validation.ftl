<html>
<head>
    <title>SIRI Validation</title>
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
    <script>
        async function toggleValidation(id, event) {
            const toggle = event.target;
            const isChecked = toggle.checked;

            const filterValue = document.getElementById("filterValue:" + id).value;
            let uri = "toggle?subscriptionId=" + id;

            if (filterValue) {
                uri += "&filter=" + encodeURIComponent(filterValue);
                console.log("Adding custom filter for validation: " + filterValue);
            }

            // Disable toggle during request
            toggle.disabled = true;

            try {
                const response = await fetch(uri, {
                    method: 'PUT',
                    headers: {
                        'Content-Type': 'application/json'
                    }
                });

                if (!response.ok) {
                    throw new Error('Failed to toggle validation: ' + response.status);
                }

                showNotification('Success', 'Validation toggled successfully', 'success');

                // Reload after short delay to show notification
                setTimeout(() => window.location.reload(), 1000);

            } catch (error) {
                console.error('Error toggling validation:', error);
                showNotification('Error', error.message, 'danger');

                // Revert toggle state on error
                toggle.checked = !isChecked;
            } finally {
                toggle.disabled = false;
            }
        }

        function showNotification(title, message, type) {
            const toastEl = document.getElementById('notificationToast');
            const toastTitle = document.getElementById('toastTitle');
            const toastBody = document.getElementById('toastBody');
            const toastHeader = toastEl.querySelector('.toast-header');

            // Set content
            toastTitle.textContent = title;
            toastBody.textContent = message;

            // Set color based on type
            toastHeader.className = 'toast-header';
            if (type === 'success') {
                toastHeader.classList.add('bg-success', 'text-white');
            } else if (type === 'danger') {
                toastHeader.classList.add('bg-danger', 'text-white');
            }

            // Show toast
            const toast = new bootstrap.Toast(toastEl);
            toast.show();
        }
    </script>
</head>
<body>
<div class="bg-light p-5 rounded-3 text-center mb-4">
    <h2>SIRI Validation</h2>
</div>
<#if body?? >
<div class="container">

    <div class="row">
        <table class="table">
            <thead>
            <tr>
                <th>#</th>
                <th>Name</th>
                <th>Subscription status</th>
                <th>Type</th>
                <th>Filter (optional)</th>
                <th>Results</th>
                <th>On/Off <sup><span class="bi bi-info-circle text-info" ></span></sup></th>
            </tr>
            </thead>
            <tbody>
                <#list body.subscriptions?sort_by("name") as item>
                <tr class="${item.healthy???then(item.healthy?then("table-success","table-danger"), "table-warning")}">
                    <th>${item?counter}</th>
                    <td>
                        ${item.name}<br />
                        <small>${item.description}</small>
                    </td>
                    <td>${item.status!""}</td>
                    <td>${item.subscriptionType}</td>
                    <td>
                        <input type="text" class="form-control form-control-sm"
                               id="filterValue:${item.internalId?long?c}"
                               placeholder="e.g. LineRef:123"
                               value="${item.validationFilter???then("${item.validationFilter}","")}">
                    </td>
                    <td>
                        <label>
                            <a href="report?subscriptionId=${item.internalId?long?c}" target="_blank">Validation report</a>
                        </label>
                    </td>
                    <td>
                        <div class="form-check form-switch">
                            <input class="form-check-input" type="checkbox"
                                   id="validation${item.internalId?long?c}"
                                   ${item.validation?then("checked", "")}
                                   onchange="toggleValidation(${item.internalId?long?c}, event)">
                            <label class="form-check-label visually-hidden" for="validation${item.internalId?long?c}">
                                Enable validation
                            </label>
                        </div>
                    </td>
                </tr>
                </#list>
            </tbody>
        </table>
    </div>
    <div class="row">
        <sup><span class="bi bi-info-circle text-info"></span></sup>
        Switching on validation will remove all previous validation reports, and start validation of all incoming ServiceDeliveries<br />
        If the optional filter has been provided before switching on the report it will only validate ServiceDeliveries that contain the given string.<br />
        Validation will automatically be disabled when size limit has been reached.<br />
        Validationresults will be kept for ${body.config.persistPeriodHours} hours.
    </div>
</div>

</#if>

<!-- Toast notification container -->
<div class="toast-container position-fixed top-0 end-0 p-3">
    <div id="notificationToast" class="toast" role="alert" aria-live="assertive" aria-atomic="true">
        <div class="toast-header">
            <strong class="me-auto" id="toastTitle"></strong>
            <button type="button" class="btn-close" data-bs-dismiss="toast" aria-label="Close"></button>
        </div>
        <div class="toast-body" id="toastBody"></div>
    </div>
</div>

</body>
</html>