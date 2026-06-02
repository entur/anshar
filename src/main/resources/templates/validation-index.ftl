<html>
<head>
    <title>SIRI Validation — Codespaces</title>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <script>
        // Resolve relative links against the canonical "validation/" directory so they work
        // whether or not the visited URL ends with a trailing slash. Absolute paths can't be
        // used here because the API gateway rewrites the path prefix (e.g. /anshar/validation
        // is served externally as /realtime/v1/validation).
        (function () {
            const path = window.location.pathname.replace(/\/+$/, "");
            const base = document.createElement("base");
            base.href = window.location.origin + path + "/";
            document.head.appendChild(base);
        })();
    </script>
    <link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.2/dist/css/bootstrap.min.css" rel="stylesheet" integrity="sha384-T3c6CoIi6uLrA9TneNEoa7RxnatzjcDSCmG1MXxSR1GAsXEV/Dwwykc2MPK8M2HN" crossorigin="anonymous">
    <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/bootstrap-icons@1.11.3/font/bootstrap-icons.min.css">
    <script src="https://cdn.jsdelivr.net/npm/bootstrap@5.3.2/dist/js/bootstrap.bundle.min.js" integrity="sha384-C6RzsynM9kWDrMNeT87bh95OGNyZPhcTNXj1NW7RuBCsyN/o0jlpcV8Qyq46cDfL" crossorigin="anonymous"></script>
</head>
<body>
<div class="bg-light p-5 rounded-3 text-center mb-4">
    <h2>SIRI Validation — Codespaces</h2>
</div>
<div class="container">
<#if body?? && body.codespaces?? && (body.codespaces?size > 0)>
    <div class="row row-cols-1 row-cols-md-2 row-cols-lg-3 row-cols-xl-4 g-3">
        <#list body.codespaces as item>
        <div class="col">
            <div class="card h-100 text-center position-relative bg-success-subtle border-success-subtle">
                <div class="card-body">
                    <#if item.displayName??>
                        <h5 class="card-title mb-1">${item.displayName}</h5>
                        <p class="card-subtitle text-muted small mb-2">${item.codespace}</p>
                    <#else>
                        <h5 class="card-title mb-2">${item.codespace}</h5>
                    </#if>
                    <p class="card-text text-muted small mb-0">
                        <span class="badge bg-secondary">${item.subscriptionCount}</span>
                        subscription<#if item.subscriptionCount != 1>s</#if>
                    </p>
                    <a href="${item.codespace}" class="stretched-link" aria-label="View validation for ${item.codespace}"></a>
                </div>
            </div>
        </div>
        </#list>
    </div>
<#else>
    <div class="row text-center text-muted">
        <p>No codespaces configured.</p>
    </div>
</#if>
</div>
</body>
</html>
