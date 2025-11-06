<html>
<head>
    <title>Anshar statistics</title>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.2/dist/css/bootstrap.min.css" rel="stylesheet" integrity="sha384-T3c6CoIi6uLrA9TneNEoa7RxnatzjcDSCmG1MXxSR1GAsXEV/Dwwykc2MPK8M2HN" crossorigin="anonymous">
    <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/bootstrap-icons@1.11.3/font/bootstrap-icons.min.css">
    <script src="https://cdn.jsdelivr.net/npm/bootstrap@5.3.2/dist/js/bootstrap.bundle.min.js" integrity="sha384-C6RzsynM9kWDrMNeT87bh95OGNyZPhcTNXj1NW7RuBCsyN/o0jlpcV8Qyq46cDfL" crossorigin="anonymous"></script>
    <style>
        /* ============================================
           Anshar Statistics Page Styles
           ============================================ */

        /* --- Layout & Container --- */
        .stats-header {
            background-color: #f8f9fa;
            padding: 3rem;
            border-radius: 0.5rem;
            text-align: center;
            margin-bottom: 1.5rem;
        }

        .stats-server-info {
            margin-left: auto;
            margin-right: auto;
            width: fit-content;
        }

        /* --- Table Styles --- */
        .table-cell-middle {
            vertical-align: middle !important;
        }

        .table-cell-center {
            text-align: center;
        }

        .table-cell-right {
            text-align: right;
        }

        .table-col-narrow {
            width: 15%;
        }

        /* Status icon cells */
        .status-icon-cell {
            vertical-align: middle;
            text-align: center;
            font-size: 1.25rem;
        }

        /* --- Clickable Elements --- */
        .clickable-row {
            cursor: pointer;
        }

        .clickable-icon {
            cursor: pointer;
        }

        .clickable-icon:hover {
            opacity: 0.7;
        }

        /* --- Distribution Table --- */
        .distribution-table tfoot {
            border-top: 2px solid #ddd;
            font-weight: bold;
        }

        /* --- Action Buttons --- */
        .action-icon-wrapper {
            text-align: right;
        }

        .btn-icon {
            margin-right: 0.25rem;
        }

        /* --- Subscription Details Table --- */
        .subscription-details {
            margin: 1rem 0;
        }

        .subscription-actions {
            margin-top: 1rem;
        }

        /* --- Admin Section --- */
        .admin-action-row {
            cursor: pointer;
        }

        .admin-action-row:hover {
            background-color: rgba(0,0,0,0.02);
        }

        /* --- Responsive Adjustments --- */
        @media (max-width: 768px) {
            .stats-header {
                padding: 2rem 1rem;
            }

            .table-responsive {
                overflow-x: auto;
            }

            .status-icon-cell {
                font-size: 1rem;
            }
        }

        /* --- Loading & Feedback (for future use) --- */
        .loading-overlay {
            position: fixed;
            top: 0;
            left: 0;
            width: 100%;
            height: 100%;
            background: rgba(0, 0, 0, 0.5);
            display: flex;
            align-items: center;
            justify-content: center;
            z-index: 9999;
        }

        .loading-overlay.d-none {
            display: none !important;
        }
    </style>
    <script>
        function administerSubscription(operation,id, type) {

            var uri = "?operation=" + operation + "&subscriptionId="+id;
            if (type != undefined) {
                uri += "&type=" + type
            }
            var xhr = new XMLHttpRequest();
            xhr.open('PUT', uri, true);
            xhr.onreadystatechange = function() {
                window.location.reload();
            };
            xhr.send(null);
        }
    </script>
</head>
<body>
<div class="stats-header">
    <h2>Anshar status/statistics</h2>
    <#if body.vmServerStarted??>
    <table class="stats-server-info">
        <tr><td class="table-cell-right"><small>Proxyserver started:&nbsp;</small></td><td><small>${body.serverStarted}</small></td></tr>
        <tr><td class="table-cell-right"><small>VM-server started:&nbsp;</small></td><td><small>${body.vmServerStarted}</small></td></tr>
        <tr><td class="table-cell-right"><small>ET-server started:&nbsp;</small></td><td><small>${body.etServerStarted}</small></td></tr>
        <tr><td class="table-cell-right"><small>SX-server started:&nbsp;</small></td><td><small>${body.sxServerStarted}</small></td></tr>
    </table>
    <#else>
        <small>Server started ${body.serverStarted}</small>
    </#if>
    <br /><small>- ${body.environment} -</small>
</div>
<div class="container">

    <ul class="nav nav-tabs" id="tabs" role="tablist">
        <li class="nav-item">
            <a class="nav-link" id="inbound-tab" data-bs-toggle="tab" href="#inbound" onclick="location.hash='inbound'" role="tab" aria-controls="inbound">Inbound <span class="badge bg-success"></span> <span class="bi bi-arrow-down"></span> </a>
        </li>
        <li class="nav-item">
            <a class="nav-link" id="outbound-tab" data-bs-toggle="tab" href="#outbound" onclick="location.hash='outbound'" role="tab" aria-controls="outbound">Outbound <span class="badge bg-success">${body.outbound?size}</span> <span class="bi bi-arrow-up"></span></a>
        </li>
        <li class="nav-item">
            <a class="nav-link" id="polling-tab" data-bs-toggle="tab" href="#polling" onclick="location.hash='polling'" role="tab" aria-controls="polling">Polling clients <span class="bi bi-arrow-down"></span><span class="bi bi-arrow-up"></span></a>
        </li>
        <li class="nav-item">
            <a class="nav-link" id="distribution-tab" data-bs-toggle="tab" href="#distribution" onclick="location.hash='distribution'" role="tab" aria-controls="distribution">Distribution <span class="bi bi-bar-chart"></span></a>
        </li>
        <li class="nav-item text-end">
            <a class="nav-link" id="admin-tab" data-bs-toggle="tab" href="#admin" onclick="location.hash='admin'" role="tab" aria-controls="admin">Admin <span class="bi bi-wrench"></span></a>
        </li>
    </ul>

    <div class="tab-content">
        <div class="tab-pane" id="inbound" role="tabpanel" aria-labelledby="inbound-tab">

            <#list body.types as type>
                <table class="table">
                <thead>
                    <tr><th colspan="9"><h4>${type.typeName}</h4></th></tr>

                    <tr>
                        <th>#</th>
                        <th>Started</th>
                        <th>Ok</th>
                        <th class="col-md-2">Activated</th>
                        <th class="col-md-3">Vendor</th>
                        <th class="col-md-2">Last data received</th>
                        <th>Requests<br />processed</th>
                        <th>Objects<br />processed</th>
                        <th>Data<br />processed</th>
                    </tr>
                </thead>
                <tbody>
                    <#list type.subscriptions?sort_by("name") as item>
                        <tr data-bs-toggle="collapse" data-bs-target="#accordion${type?counter}-${item?counter}" class="clickable-row ${item.healthy???then(item.healthy?then("table-success","table-danger"), "table-warning")}">
                            <th class="table-cell-middle">${item?counter}</th>
                            <td class="status-icon-cell">
                                <#if item.status=='active'>
                                    <span class="bi bi-check-circle-fill text-success"></span>
                                <#else>
                                    <span class="bi bi-dash-circle-fill text-warning"></span>
                                </#if>
                            </td>
                            <td class="status-icon-cell">
                                <#if item.healthy??>
                                    <#if item.healthy>
                                        <span class="bi bi-check-circle-fill text-success"></span>
                                    <#else>
                                        <span class="bi bi-x-circle-fill text-danger"></span>
                                    </#if>
                                </#if>
                            </td>
                            <td class="table-cell-middle">${item.activated!""}</td>
                            <td class="table-cell-middle">${item.name}</td>
                            <td class="table-cell-middle">${item.lastDataReceived!""} ${item.flagAsNotReceivingData?then("<span class=\"bi bi-exclamation-triangle-fill text-warning\" title=\"Subscription is alive, but not receiving data\"></span>","")}</td>
                            <td class="table-cell-middle table-cell-right">${item.hitcount!0}</td>
                            <td class="table-cell-middle table-cell-right">${item.objectcount!0}</td>
                            <td class="table-cell-middle table-cell-right">${item.bytecountLabel!""}</td>
                        </tr>
                        <tr id="accordion${type?counter}-${item?counter}" class="collapse ${item.healthy???then(item.healthy?then("table-success","table-danger"), "table-warning")}">
                        <td colspan="9">
                            <table class="table table-striped">
                                <tr><th>Dataset ID</th><td><a href="${item.validationUrl}" target="_blank">${item.datasetId}</a></td></tr>
                                <tr><th>Codespacefilter</th><td>Whitelisted: ${item.codespaceWhiteList}<br />Blacklisted: ${item.codespaceBlackList}</td></tr>
                                <tr><th>Vendor ID</th><td>${item.vendor}</td></tr>
                                <tr><th>Servicetype</th><td>${item.serviceType}</td></tr>
                                <tr><th>Inbound URL</th><td>${item.inboundUrl}</td></tr>
                                <tr><th>Content-Type</th><td>${item.contentType}</td></tr>
                                <tr><th>Heartbeat interval</th><td>${item.heartbeatInterval}</td></tr>
                                <tr><th>Preview interval</th><td>${item.previewInterval}</td></tr>
                                <tr><th>Change before updates</th><td>${item.changeBeforeUpdates}</td></tr>
                                <tr><th>Incremental updates</th><td>${item.incrementalUpdates}</td></tr>
                                <tr><th>Update interval</th><td>${item.updateInterval}</td></tr>
                                <tr><th>Duration</th><td>${item.durationOfSubscription}</td></tr>
                                <tr><th>Restart time</th><td>${item.restartTime!""}</td></tr>
                                <tr><th>Type</th><td>${item.subscriptionType}</td></tr>
                                <tr><th>Forward positiondata</th><td>${item.forwardPositionData?c}</td></tr>
                                <tr><th>Id</th><td>${item.subscriptionId}</td></tr>
                                <tr><th>RequestorRef</th><td>${item.requestorRef}</td></tr>
                                <tr><th>Mode</th><td>${item.subscriptionMode}</td></tr>
                                <tr>
                                    <th>URLs</th>
                                    <td>
                                        <table width="80%">
                                        <#list item.urllist?keys as label>
                                           <tr><td width="50%">${label}</td><td>${item.urllist[label]}</td></tr>
                                        </#list>
                                        </table>
                                    </td>
                                </tr>
                            </table>
                            <table border="0" width="100%">
                                <tr>
                                    <td>
                                        <#if item.status=='deactivated'>
                                            <button type="button" class="btn btn-danger" disabled onclick="administerSubscription('stop', '${item.subscriptionId}')">Stop</button>
                                            <button type="button" class="btn btn-success" onclick="administerSubscription('start', '${item.subscriptionId}')">Start</button>
                                        <#else >
                                            <button type="button" class="btn btn-danger"  onclick="administerSubscription('stop', '${item.subscriptionId}')">Stop</button>
                                            <button type="button" class="btn btn-success" disabled onclick="administerSubscription('start', '${item.subscriptionId}')">Start</button>
                                        </#if>
                                    </td>
                                    <td>
                                        <div class="action-icon-wrapper">
                                            <span class="bi bi-trash clickable-icon" onclick="administerSubscription('flush', '${item.subscriptionId}')"></span>
                                        </div>
                                    </td>
                                </tr>
                            </table>
                        </td>
                        </tr>
                    </#list>

                </tbody>
                </table>
            </#list>
        </div>

        <div class="tab-pane" id="outbound" role="tabpanel" aria-labelledby="outbound-tab">

                <table class="table table-striped">
                    <thead>
                    <tr>
                        <th>#</th>
                        <th>SubscriptionRef</th>
                        <th>DatasetId</th>
                        <th>ClientName</th>
                        <th>Type</th>
                        <th>Version</th>
                        <th>Heartbeat</th>
                        <th>Start-/Endtime</th>
                        <th></th>
                    </tr>
                    </thead>
                    <tbody>
                    <#list body.outbound?sort_by("subscriptionRef") as item>
                    <tr>
                        <th>${item?counter}</th>
                        <td><a href="${item.address}">${item.subscriptionRef}</a></td>
                        <td>${item.datasetId}</td>
                        <td>${item.clientTrackingName}</td>
                        <td>${item.subscriptionType}</td>
                        <td>${item.version}</td>
                        <td>${item.heartbeatInterval}</td>
                        <td>
                            ${item.requestReceived} - <br />
                            ${item.initialTerminationTime}
                        </td>
                        <td>
                            <span class="bi bi-trash clickable-icon" onclick="administerSubscription('terminate', '${item.subscriptionRef}')"></span>
                        </td>
                    </tr>
                    </#list>
                    </tbody>
                </table>

        </div>
        <div class="tab-pane" id="polling" role="tabpanel" aria-labelledby="polling-tab">

            <#list body.polling as pollingClient>
            <table class="table">
            <thead>
                <tr><th colspan="8"><h4>${pollingClient.typeName}</h4></th></tr>

                <tr>
                    <th class="col-md-1">#</th>
                    <th class="col-md-4">Id</th>
                    <th class="col-md-2">ClientName</th>
                    <th class="col-md-1">DatasetId</th>
                    <th class="col-md-2">Last requests</th>
                    <th class="col-md-1">Requests per min</th>
                    <th class="col-md-1 text-right">Objects remaining</th>
                </tr>
            </thead>
            <tbody>
                <#list pollingClient.polling?sort_by("id") as item>
                <tr class="table-success">
                    <th>${item?counter}</th>
                    <td><span title="First request: ${item.firstRequest}
Request count: ${item.requestCount}">${item.id}</span></td>
                    <td>${item.clientTrackingName}</td>
                    <td>${item.datasetId}</td>
                    <td>
                        <span title="<#list item.lastRequests as timestamp>
- ${timestamp}</#list>">${item.lastRequests[0]}</span>
                    </td>
                    <td>${item.requestsPerMinute}</td>
                    <td class="text-right">${item.count}</td>
                </tr>
                </#list>
            </tbody>
            </table>
            </#list>
        </div>
        <div class="tab-pane" id="distribution" role="tabpanel" aria-labelledby="distribution-tab">
            <table class="table table-striped distribution-table">
                <thead>
                <tr><th colspan="4"><h4>Active data per codespace:</h4></th></tr>
                <tr>
                    <th>Codespace</th>
                    <th class="text-end table-col-narrow">ET</th>
                    <th class="text-end table-col-narrow">VM</th>
                    <th class="text-end table-col-narrow">SX</th>
                </tr>
                </thead>
                <tbody>
                <#list body.elements.distribution?sort_by("datasetId") as item>
                <tr>
                    <th>${item.datasetId}</th>
                    <td class="text-end">${item.etCount}</td>
                    <td class="text-end">${item.vmCount}</td>
                    <td class="text-end">${item.sxCount}</td>
                </tr>
                </#list>
                </tbody>
                <tfoot>
                <tr>
                    <th>Total</th>
                    <th class="text-end">${body.elements.et}</th>
                    <th class="text-end">${body.elements.vm}</th>
                    <th class="text-end">${body.elements.sx}</th>
                </tr>
                </tfoot>
            </table>
        </div>
        <div class="tab-pane" id="admin" role="tabpanel" aria-labelledby="admin-tab">
            <table class="table table-striped">
                <thead>
                    <tr><th colspan="2"><h4>Admin tools:</h4></th></tr>
                </thead>
                <tbody>
                    <tr data-bs-toggle="collapse" data-bs-target="#accordion_admin_validate" class="admin-action-row table-success">
                        <td colspan="2">Validate ALL subscriptions</td>
                    </tr>
                    <tr id="accordion_admin_validate" class="collapse ">
                        <td>
                            CAUTION - validates ALL subscriptions!!! <br />
                            Any previous validation reports will be deleted
                        </td>
                        <td>
                            <p><button type="button" class="btn btn-danger"  onclick="administerSubscription('validateAll', '')"><span class="bi bi-check btn-icon"></span> ALL</button> </p>
                        </td>
                    </tr>
                    <tr data-bs-toggle="collapse" data-bs-target="#accordion_admin_terminate" class="admin-action-row table-danger">
                        <td colspan="2">Terminate ALL subscriptions</td>
                    </tr>
                    <tr id="accordion_admin_terminate" class="collapse ">
                        <td>
                            CAUTION - terminates ALL subscriptions!!! <br />
                            Will stop <i>all</i> incoming data immediately.<br />
                            Use case: Server is to be taken down controlled, and all subscriptions should be stopped.
                        </td>
                        <td>
                            <p><button type="button" class="btn btn-danger"  onclick="administerSubscription('terminateAll', '')"><span class="bi bi-stop-fill btn-icon"></span> ALL</button> </p>
                            <p><button type="button" class="btn btn-danger"  onclick="administerSubscription('terminateAll', null, 'ESTIMATED_TIMETABLE')"><span class="bi bi-stop-fill btn-icon"></span> ET</button></p>
                            <p><button type="button" class="btn btn-danger"  onclick="administerSubscription('terminateAll', null, 'VEHICLE_MONITORING')"><span class="bi bi-stop-fill btn-icon"></span> VM</button></p>
                            <p><button type="button" class="btn btn-danger"  onclick="administerSubscription('terminateAll', null, 'SITUATION_EXCHANGE')"><span class="bi bi-stop-fill btn-icon"></span> SX</button></p>
                        </td>
                    </tr>

                    <tr data-bs-toggle="collapse" data-bs-target="#accordion_admin_start" class="admin-action-row table-success">
                        <td colspan="2">Restart ALL active subscriptions</td>
                    </tr>
                    <tr id="accordion_admin_start" class="collapse ">
                        <td>
                            CAUTION - Triggers immediate restart of ALL active subscriptions!!!<br />
                            Use case: Server has just been started, and all subscriptions should be activated ASAP instead of waiting for health-trigger.
                        </td>
                        <td>
                            <p><button type="button" class="btn btn-danger"  onclick="administerSubscription('startAll', '')"><span class="bi bi-arrow-clockwise btn-icon"></span> ALL</button> </p>
                            <p><button type="button" class="btn btn-danger"  onclick="administerSubscription('startAll', null, 'ESTIMATED_TIMETABLE')"><span class="bi bi-arrow-clockwise btn-icon"></span> ET</button></p>
                            <p><button type="button" class="btn btn-danger"  onclick="administerSubscription('startAll', null, 'VEHICLE_MONITORING')"><span class="bi bi-arrow-clockwise btn-icon"></span> VM</button></p>
                            <p><button type="button" class="btn btn-danger"  onclick="administerSubscription('startAll', null, 'SITUATION_EXCHANGE')"><span class="bi bi-arrow-clockwise btn-icon"></span> SX</button></p>
                        </td>
                    </tr>

                    <tr data-bs-toggle="collapse" data-bs-target="#accordion_admin_delete" class="admin-action-row table-success">
                        <td colspan="2">Delete subscriptions</td>
                    </tr>
                    <tr id="accordion_admin_delete" class="collapse ">
                        <td  colspan="2">
                            <table class="table">
                                <tbody>
                                <tr>
                                    <td>
                                        CAUTION - Actually deletes subscription!!<br />
                                        Use case: Subscription has been removed from config, and has to be removed manually.<br />
                                        <strong>Note: this removes the <i>record</i>, a redeploy is necessary to remove all traces.</strong>
                                    </td>
                                </tr>
                                <tr>
                                    <td>
                                        <#list body.types as type>
                                            <table class="table">
                                                <tr><th colspan="4"><h4>${type.typeName}</h4></th></tr>
                                                <#list type.subscriptions?sort_by("vendor") as item>
                                                    <tr>
                                                        <th class="table-cell-middle">${item?counter}</th>
                                                        <td class="table-cell-middle">${item.name}</td>
                                                        <td>${item.vendor}</td>
                                                        <td>
                                                            <div class="action-icon-wrapper">
                                                                <span class="bi bi-trash clickable-icon" onclick="administerSubscription('delete', '${item.subscriptionId}')"></span>
                                                            </div>
                                                        </td>
                                                    </tr>
                                                </#list>
                                            </table>
                                        </#list>
                                    </td>
                                </tr>
                                </tbody>
                            </table>
                        </td>
                    </tr>
                </tbody>
            </table>
        </div>
    </div>
</div>
</body>
<script>
    document.addEventListener('DOMContentLoaded', function() {
        // Manage hash in URL to open the right tab
        const hash = window.location.hash;

        // Determine which tab to show
        let targetTab;
        if(hash && hash.length > 0) {
            // Try to find tab matching the hash
            targetTab = document.querySelector('.nav-tabs a[href="' + hash + '"]');
        }

        // If no hash or hash doesn't match a tab, default to inbound
        if(!targetTab) {
            targetTab = document.querySelector('.nav-tabs a[href="#inbound"]');
        }

        // Use Bootstrap's Tab API to properly show the tab
        if(targetTab) {
            const tab = new bootstrap.Tab(targetTab);
            tab.show();
        }
    });
</script>
</html>