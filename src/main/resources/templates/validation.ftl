<html>
<head>
    <title>SIRI Validation</title>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <link rel="stylesheet" href="https://maxcdn.bootstrapcdn.com/bootstrap/3.3.7/css/bootstrap.min.css">
    <script src="https://ajax.googleapis.com/ajax/libs/jquery/3.1.1/jquery.min.js"></script>
    <script src="https://maxcdn.bootstrapcdn.com/bootstrap/3.3.7/js/bootstrap.min.js"></script>
    <script>
        function toggleValidation(id) {

            var uri = "toggle?subscriptionId="+id;

            var filterValue = document.getElementById("filterValue:"+id).value;

            if (filterValue) {
                uri += "&filter="+filterValue;

                console.log("Adding custom filter for validation: " + filterValue)
            }

            var xhr = new XMLHttpRequest();
            xhr.open('PUT', uri, true);
            xhr.send(null);
        }
    </script>

    <style>
        /* The switch - the box around the slider */
        .switch {
            position: relative;
            display: inline-block;
            width: 34px;
            height: 19px;
        }

        /* Hide default HTML checkbox */
        .switch input {display:none;}

        /* The slider */
        .slider {
            position: absolute;
            cursor: pointer;
            top: 0;
            left: 0;
            right: 0;
            bottom: 0;
            background-color: #ccc;
            -webkit-transition: .4s;
            transition: .4s;
        }

        .slider:before {
            position: absolute;
            content: "";
            height: 15px;
            width: 15px;
            left: 2px;
            bottom: 2px;
            background-color: white;
            -webkit-transition: .4s;
            transition: .4s;
        }

        input:checked + .slider {
            background-color: #2196F3;
        }

        input:focus + .slider {
            box-shadow: 0 0 1px #2196F3;
        }

        input:checked + .slider:before {
            -webkit-transform: translateX(15px);
            -ms-transform: translateX(15px);
            transform: translateX(15px);
        }

        /* Rounded sliders */
        .slider.round {
            border-radius: 19px;
        }

        .slider.round:before {
            border-radius: 50%;
        }

        /* The text input field */
        .input-field {
            width: 150px;
            height: 23px;
            box-sizing: border-box;
            -webkit-box-sizing: border-box;
            -moz-box-sizing: border-box;
            border: 1px solid #C2C2C2;
            box-shadow: 1px 1px 4px #EBEBEB;
            -moz-box-shadow: 1px 1px 4px #EBEBEB;
            -webkit-box-shadow: 1px 1px 4px #EBEBEB;
            border-radius: 3px;
            -webkit-border-radius: 3px;
            -moz-border-radius: 3px;
            padding: 7px;
            outline: none;
        }

    </style>
</head>
<body>
<div class="jumbotron text-center">
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
                <th>On/Off <sup><span class="glyphicon glyphicon-info-sign text-info" ></span></sup></th>
            </tr>
            </thead>
            <tbody>
                <#list body.subscriptions?sort_by("name") as item>
                <tr class="${item.healthy???then(item.healthy?then("success","danger"), "warning")}">
                    <th>${item?counter}</th>
                    <td>
                        ${item.name}<br />
                        <small>${item.description}</small>
                    </td>
                    <td>${item.status!""}</td>
                    <td>${item.subscriptionType}</td>
                    <td>
                        <label>
                            <input type="text" class="input-field" id="filterValue:${item.internalId?long?c}" value="${item.validationFilter???then("${item.validationFilter}","")}" onchange="this.form.submit()">
                        </label>
                    </td>
                    <td>
                        <label>
                            <a href="report?subscriptionId=${item.internalId?long?c}" target="_blank">Validation report</a>
                        </label>
                    </td>
                    <td>
                        <label class="switch">
                            <input type="checkbox" ${item.validation?then("checked", "")} onchange="toggleValidation(${item.internalId?long?c})">
                            <span class="slider round"></span>
                        </label>
                    </td>
                </tr>
                </#list>
            </tbody>
        </table>
    </div>
    <div class="row">
        <sup><span class="glyphicon glyphicon-info-sign text-info"></span></sup>
        Switching on validation will remove all previous validation reports, and start validation of all incoming ServiceDeliveries<br />
        If the optional filter has been provided before switching on the report it will only validate ServiceDeliveries that contain the given string.<br />
        Validation will automatically be disabled when size limit has been reached.<br />
        Validationresults will be kept for ${body.config.persistPeriodHours} hours.
    </div>
</div>

</#if>
</body>
</html>