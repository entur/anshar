<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>Title</title>
    <link rel="stylesheet" href="https://unpkg.com/leaflet@1.7.1/dist/leaflet.css"
          integrity="sha512-xodZBNTC5n17Xt2atTPuE1HxjVMSvLVW9ocqUKLsCC5CXdbqCmblAshOMAS6/keqq/sMZMZ19scR4PsZChSR7A=="
          crossorigin=""/>
    <script src="https://unpkg.com/leaflet@1.7.1/dist/leaflet.js"
            integrity="sha512-XQoYMqMTK8LvdxXYG3nZ448hOEQiglfqkJs1NOQV44cWnUrBc8PkAOcXy20w0vlaXaVUearIOBhiXZ5V3ynxwA=="
            crossorigin=""></script>
    <style>
        #map {
            height: 90vh;
            width: 100%;
        }

        .icon {
            width: 18px;
            height: 18px;
            border-radius: 5px;
            border: 1px solid black;
            white-space: nowrap;
            text-align: center;
        }

        .user {
            background-color: yellow;
        }

        .bus {
            background-color: #d93a41;
            border-color: #b23538;
        }

        .tram {
            background-color: #3c9bcc;
            border-color: #2f789e;
        }

        .subway {
            background-color: #e57124;
            border-color: #b2581c;
        }

        .rail {
            background-color: #964569;
            border-color: #803b59;
        }

        .water {
            background-color: #167c87;
            border-color: #115e66;
        }

        .hidden {
            display: none;
        }

        ul {
            list-style-type: none;
        }

    </style>
    <script>
        <#include "../public/scripts/browser-mqtt.js">
    </script>
</head>
<body>
<input type="button" id="connect" value="1. Connect" />
<input type="button" id="disconnect" value="2. Disconnect" />
<input type="button" id="clear" value="3. Clear markers" />
<input id="topic" placeholder='/hfp/journey/+/+/+/+/+/+/#' value='/hfp/journey/+/+/+/+/+/+/#' />
<span id="counter"></span>
<ul id="topics" class="hidden"></ul>
<div id="map"></div>
<div id="log" style="white-space: pre">...</div>
</body>
</html>
<script>

    var websocketUrl = "${body.websocketUrl}";

    var map, mqtt_client, timerId = 0,
        markers = {},
        updated = {};

    function setup() {
        var osmUrl = 'https://a.tile.openstreetmap.org/{z}/{x}/{y}.png',
            osmAttrib = '&copy; <a href="http://openstreetmap.org/copyright">OpenStreetMap</a> contributors',
            osm = L.tileLayer(osmUrl, {
                maxZoom: 20,
                attribution: osmAttrib
            });

        // initialize the map on the "map" div with a given center and zoom
        map = L.map('map').setView([59.9122384, 10.750713], 10).addLayer(osm);

        map.on('popupopen', function(e) {
            var now = parseInt(new Date().getTime() / 1000, 10);
            var totalSeconds = 0;
            var lastNow = 0;
            timerId = setInterval(setTime, 1000);

            function setTime() {
                var vehId = document.getElementById("vehId").innerHTML;
                var tsi = updated[vehId];

                if (lastNow === 0 || lastNow !== tsi) {
                    totalSeconds++;
                } else {
                    now = parseInt(new Date().getTime() / 1000, 10);
                    totalSeconds = 0;
                }
                lastNow = tsi;

                var sec = now - tsi + totalSeconds;
                var disp = 'Last updated: ' + sec + ' s';
                document.getElementById("seconds").innerHTML = disp;
            }
        });
        map.on('popupclose', function(e) {
            clearInterval(timerId);
        });
        //map.on('click', onMapClick);
        connect();
    }

    function status(message) {
        console.log(message);
        document.getElementById('log').innerHTML = "";
        document.getElementById('log').appendChild(document.createTextNode(message));
    }

    function connect() {
        disconnect();

        mqtt_client = mqtt.connect(websocketUrl);
        mqtt_client.on('connect', function() {
            var topic = getTopic();
            mqtt_client.subscribe(topic);
            status('connected - subscribed to topic: "' + topic);
        });
        mqtt_client.on('message', function(topic, message) {
            var mode = topic.substring(13);
            mode = mode.substring(0, mode.indexOf('/'));
            var vp = JSON.parse(message.toString()).VP;
            updated[vp.veh] = vp.tsi;
            addToMap(vp.line, vp.lat, vp.long, mode, vp.veh, vp2popup(vp));
        });

        showExampleTopics(false);
    }

    function vp2popup(vp, all = false) {
        var popup = '';
        if (all) {
            var keys = Object.keys(vp);
            for (var i in keys) {
                var key = keys[i];
                popup += key + ":" + vp[key] + "<br/>";
            }
        } else {
            popup += '<b>Line ' + vp.desi + '</b><br/>';
            popup += 'ID: <span id="vehId">' + vp.veh + '</span><br/>';
            popup += vp.jrn + '<br/>';
            popup += vp.oday + '<br/>';
            popup += vp.dl + 's delayed<br/>';
            popup += vp.line + '<br/>';
            popup += '<div id="seconds"></div>';
        }
        return popup;
    }

    function disconnect() {
        if (typeof mqtt_client !== 'undefined') {
            status('disconnecting')
            mqtt_client.end();
            mqtt_client = undefined;
        }
    }

    function onMapClick(e) {
        var popup = "User click<br/> lat,lng = " + e.latlng.lat + ',' + e.latlng.lng;
        addToMap("XX", e.latlng.lat, e.latlng.lng, 'user', 'user123', popup);
    }

    // Script for adding marker on map click
    function addToMap(title, lat, lng, mode, id, popup) {
        var geojsonFeature = {
            "type": "Feature",
            "properties": {},
            "geometry": {
                "type": "Point",
                "coordinates": [lat, lng]
            }
        }
        var keys = Object.keys(markers);
        document.getElementById("counter").innerHTML = keys.length;

        var marker = markers[id];
        var line = title.lastIndexOf(':');
        var text = title;
        if (line !== -1) {
            text = parseInt(title.substring(line + 1))
        }

        if (typeof marker !== 'undefined') {
            marker.setLatLng([lat, lng]);
        } else {
            L.geoJson(geojsonFeature, {
                pointToLayer: function(feature, latlng) {
                    marker = L.marker([lat, lng], {
                        title: title,
                        alt: "public transport location",
                        riseOnHover: true,
                        draggable: title === 'XX',
                        icon: new L.DivIcon({
                            className: 'somediv',
                            html: '<div class="icon ' + mode + '">' + text + '</div>'
                        }),
                        iconSize: [240, 24],
                    });
                    return marker;
                }
            }).addTo(map);
            markers[id] = marker;
            marker.bindPopup(popup)
        }
    }

    function setTopic(topic) {
        document.getElementById('topic').value = topic
    }

    function getTopic() {
        return document.getElementById('topic').value
    }

    function createLi(text) {
        var entry = document.createElement('li');
        entry.appendChild(document.createTextNode(text));
        return entry;
    }

    function showExampleTopics(show = true) {
        var list = document.querySelector('#topics');
        if (show && list.classList.contains('hidden')) {
            list.classList.remove('hidden');
        } else {
            list.classList.add('hidden');
        }
    }

    function clearMarkers() {
        var keys = Object.keys(markers);
        for (var i = 0; i < keys.length; i++) {
            var id = keys[i];
            var marker = markers[id];
            map.removeLayer(marker);
        }
    }

    function addTopics() {
        var list = document.querySelector('#topics');
        var entry = createLi('/hfp/journey/mode/vehicleId/route/direction/headsign/start_time/next_stop/geohash');
        entry.style = 'color: gray';
        list.appendChild(entry);

        ['/hfp/journey/+/+/+/+/+/+/#',
            '/hfp/journey/tram/+/+/+/+/+/#',
            '/hfp/journey/+/+/RUT:Line:0037/+/+/+/#',
            '/hfp/journey/+/+/+/+/Nesoddtangen/+/#',
            '/hfp/journey/+/+/+/+/+/+/59;10/#',
        ].forEach(function(topic) {
            var entry = createLi(topic);
            entry.onclick = function() {
                list.classList.add('hidden');
                setTopic(topic);
                topicChanged();
            };
            list.appendChild(entry);
        })
    }


    function topicChanged() {
        console.log('topicchanged')
        status('topic changed, please click connect')
    }

    setup();
    addTopics();

    document.getElementById('connect').addEventListener('click', connect);
    document.getElementById('disconnect').addEventListener('click', disconnect);
    document.getElementById('topic').addEventListener('click', showExampleTopics);
    document.getElementById('topic').addEventListener('input', topicChanged, false);

    document.getElementById('clear').addEventListener('click', clearMarkers);

</script>