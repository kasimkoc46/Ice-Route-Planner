package de.icekonig.routeplanner

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

data class Stop(
    val address: String,
    val latitude: Double,
    val longitude: Double,
    var delivered: Boolean = false
)

data class SearchResult(
    val address: String,
    val latitude: Double,
    val longitude: Double
)

class MainActivity : AppCompatActivity() {

    private val stops = mutableListOf<Stop>()

    private lateinit var searchInput: EditText
    private lateinit var suggestionsLayout: LinearLayout
    private lateinit var stopList: LinearLayout
    private lateinit var optimizeButton: Button
    private lateinit var deliveryButton: Button
    private lateinit var navigationButton: Button
    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar

    private val executor: ExecutorService =
        Executors.newSingleThreadExecutor()

    private val handler = Handler(Looper.getMainLooper())

    private var searchRunnable: Runnable? = null

    private var currentStopIndex = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        createScreen()
        setupSearch()
        updateScreen()
    }

    private fun createScreen() {

        val root = LinearLayout(this)

        root.orientation = LinearLayout.VERTICAL

        root.setPadding(
            16,
            16,
            16,
            12
        )

        val title = TextView(this)

        title.text = "ICE ROUTE PLANNER"

        title.textSize = 24f

        title.setPadding(
            4,
            4,
            4,
            12
        )

        root.addView(
            title,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        // ------------------------------------------------
        // MAP
        // ------------------------------------------------

        webView = WebView(this)

        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.setGeolocationEnabled(true)

        webView.webViewClient = WebViewClient()

        webView.loadDataWithBaseURL(
            "https://localhost/",
            createMapHtml(),
            "text/html",
            "UTF-8",
            null
        )

        root.addView(
            webView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                300
            )
        )

        // ------------------------------------------------
        // ADDRESS SEARCH
        // ------------------------------------------------

        searchInput = EditText(this)

        searchInput.hint = "Adres yaz... örn. Sonnenallee 100"

        searchInput.textSize = 16f

        searchInput.setSingleLine(true)

        searchInput.setPadding(
            16,
            12,
            16,
            12
        )

        root.addView(
            searchInput,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        // ------------------------------------------------
        // LOADING
        // ------------------------------------------------

        progressBar = ProgressBar(this)

        progressBar.visibility = View.GONE

        root.addView(
            progressBar,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER_HORIZONTAL
            }
        )

        // ------------------------------------------------
        // SUGGESTIONS
        // ------------------------------------------------

        suggestionsLayout = LinearLayout(this)

        suggestionsLayout.orientation =
            LinearLayout.VERTICAL

        root.addView(
            suggestionsLayout,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        // ------------------------------------------------
        // STOP LIST
        // ------------------------------------------------

        stopList = LinearLayout(this)

        stopList.orientation =
            LinearLayout.VERTICAL

        root.addView(
            stopList,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        // ------------------------------------------------
        // OPTIMIZE
        // ------------------------------------------------

        optimizeButton = Button(this)

        optimizeButton.text =
            "🚀 ROTAYI OPTİMİZE ET"

        optimizeButton.setOnClickListener {
            optimizeRoute()
        }

        root.addView(
            optimizeButton,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        // ------------------------------------------------
        // NAVIGATION
        // ------------------------------------------------

        navigationButton = Button(this)

        navigationButton.text =
            "🧭 NAVİGASYONA GİT"

        navigationButton.setOnClickListener {
            openNavigation()
        }

        root.addView(
            navigationButton,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        // ------------------------------------------------
        // DELIVERED
        // ------------------------------------------------

        deliveryButton = Button(this)

        deliveryButton.text =
            "✅ TESLİM EDİLDİ"

        deliveryButton.setOnClickListener {
            markDelivered()
        }

        root.addView(
            deliveryButton,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        setContentView(root)
    }

    // ====================================================
    // SEARCH
    // ====================================================

    private fun setupSearch() {

        searchInput.addTextChangedListener(
            object : TextWatcher {

                override fun beforeTextChanged(
                    s: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int
                ) {
                }

                override fun onTextChanged(
                    s: CharSequence?,
                    start: Int,
                    before: Int,
                    count: Int
                ) {

                    searchRunnable?.let {
                        handler.removeCallbacks(it)
                    }

                    val text =
                        s?.toString()?.trim() ?: ""

                    if (text.length < 3) {

                        suggestionsLayout.removeAllViews()

                        return
                    }

                    searchRunnable = Runnable {

                        searchPhoton(text)

                    }

                    handler.postDelayed(
                        searchRunnable!!,
                        350
                    )
                }

                override fun afterTextChanged(
                    s: Editable?
                ) {
                }
            }
        )
    }

    // ====================================================
    // PHOTON ADDRESS SEARCH
    // ====================================================

    private fun searchPhoton(
        query: String
    ) {

        runOnUiThread {

            progressBar.visibility =
                View.VISIBLE
        }

        executor.execute {

            try {

                val encoded =
                    URLEncoder.encode(
                        query,
                        "UTF-8"
                    )

                val urlString =
                    "https://photon.komoot.io/api/" +
                            "?q=$encoded" +
                            "&limit=8" +
                            "&lang=de"

                val connection =
                    URL(urlString)
                        .openConnection()
                            as HttpURLConnection

                connection.requestMethod = "GET"

                connection.connectTimeout =
                    10000

                connection.readTimeout =
                    10000

                val response =
                    connection.inputStream
                        .bufferedReader()
                        .use {
                            it.readText()
                        }

                connection.disconnect()

                val json =
                    JSONArray(
                        org.json.JSONObject(response)
                            .getJSONArray("features")
                            .toString()
                    )

                val results =
                    mutableListOf<SearchResult>()

                for (i in 0 until json.length()) {

                    val feature =
                        json.getJSONObject(i)

                    val properties =
                        feature.getJSONObject(
                            "properties"
                        )

                    val geometry =
                        feature.getJSONObject(
                            "geometry"
                        )

                    val coordinates =
                        geometry.getJSONArray(
                            "coordinates"
                        )

                    val longitude =
                        coordinates.getDouble(0)

                    val latitude =
                        coordinates.getDouble(1)

                    val name =
                        properties.optString(
                            "name",
                            ""
                        )

                    val street =
                        properties.optString(
                            "street",
                            ""
                        )

                    val houseNumber =
                        properties.optString(
                            "housenumber",
                            ""
                        )

                    val postcode =
                        properties.optString(
                            "postcode",
                            ""
                        )

                    val city =
                        properties.optString(
                            "city",
                            ""
                        )

                    val parts =
                        listOf(
                            name,
                            street +
                                    if (
                                        houseNumber.isNotBlank()
                                    ) {
                                        " $houseNumber"
                                    } else {
                                        ""
                                    },
                            postcode,
                            city
                        ).filter {
                            it.isNotBlank()
                        }

                    val address =
                        parts.joinToString(
                            ", "
                        )

                    if (
                        address.isNotBlank()
                    ) {

                        results.add(
                            SearchResult(
                                address,
                                latitude,
                                longitude
                            )
                        )
                    }
                }

                runOnUiThread {

                    progressBar.visibility =
                        View.GONE

                    showSuggestions(results)
                }

            } catch (e: Exception) {

                runOnUiThread {

                    progressBar.visibility =
                        View.GONE

                    suggestionsLayout.removeAllViews()

                    Toast.makeText(
                        this,
                        "Adres arama hatası",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    // ====================================================
    // SUGGESTIONS
    // ====================================================

    private fun showSuggestions(
        results: List<SearchResult>
    ) {

        suggestionsLayout.removeAllViews()

        for (result in results) {

            val button =
                Button(this)

            button.text =
                result.address

            button.textSize =
                14f

            button.gravity =
                Gravity.START or Gravity.CENTER_VERTICAL

            button.setOnClickListener {

                addStop(result)

            }

            suggestionsLayout.addView(
                button,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            )
        }
    }

    // ====================================================
    // ADD STOP
    // ====================================================

    private fun addStop(
        result: SearchResult
    ) {

        stops.add(
            Stop(
                address =
                    result.address,
                latitude =
                    result.latitude,
                longitude =
                    result.longitude
            )
        )

        searchInput.text.clear()

        suggestionsLayout.removeAllViews()

        currentStopIndex =
            if (stops.size == 1) {
                0
            } else {
                currentStopIndex
            }

        updateScreen()

        showStopsOnMap()
    }

    // ====================================================
    // SCREEN
    // ====================================================

    private fun updateScreen() {

        stopList.removeAllViews()

        if (stops.isEmpty()) {

            val empty =
                TextView(this)

            empty.text =
                "Adresleri yukarıdaki kutuya yaz.\n\n" +
                        "3 harften sonra adres önerileri gelir."

            empty.textSize =
                16f

            empty.setPadding(
                8,
                20,
                8,
                20
            )

            stopList.addView(
                empty
            )

            optimizeButton.isEnabled =
                false

            navigationButton.isEnabled =
                false

            deliveryButton.isEnabled =
                false

            return
        }

        for (
            index in stops.indices
        ) {

            val stop =
                stops[index]

            val row =
                TextView(this)

            val status =
                if (stop.delivered) {
                    "✅"
                } else if (
                    index == currentStopIndex
                ) {
                    "➡️"
                } else {
                    "⬜"
                }

            row.text =
                "$status ${index + 1}. ${stop.address}"

            row.textSize =
                15f

            row.setPadding(
                8,
                10,
                8,
                10
            )

            stopList.addView(
                row
            )
        }

        optimizeButton.isEnabled =
            stops.size >= 2

        navigationButton.isEnabled =
            stops.isNotEmpty() &&
                    currentStopIndex < stops.size

        deliveryButton.isEnabled =
            stops.isNotEmpty() &&
                    currentStopIndex < stops.size &&
                    !stops[currentStopIndex].delivered
    }

    // ====================================================
    // ROUTE OPTIMIZATION
    // ====================================================

    private fun optimizeRoute() {

        if (stops.size < 2) {

            Toast.makeText(
                this,
                "En az 2 adres gerekli.",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        optimizeButton.isEnabled =
            false

        progressBar.visibility =
            View.VISIBLE

        executor.execute {

            try {

                val coordinates =
                    stops.joinToString(";") {

                        "${it.longitude},${it.latitude}"
                    }

                val urlString =
                    "https://router.project-osrm.org/" +
                            "trip/v1/driving/" +
                            coordinates +
                            "?source=first" +
                            "&destination=last" +
                            "&roundtrip=false" +
                            "&steps=false" +
                            "&overview=full" +
                            "&geometries=geojson"

                val connection =
                    URL(urlString)
                        .openConnection()
                            as HttpURLConnection

                connection.requestMethod =
                    "GET"

                connection.connectTimeout =
                    15000

                connection.readTimeout =
                    15000

                val response =
                    connection.inputStream
                        .bufferedReader()
                        .use {
                            it.readText()
                        }

                connection.disconnect()

                val json =
                    org.json.JSONObject(
                        response
                    )

                val code =
                    json.optString(
                        "code"
                    )

                if (code != "Ok") {

                    throw Exception(
                        "OSRM: $code"
                    )
                }

                val waypoints =
                    json.getJSONArray(
                        "waypoints"
                    )

                val ordered =
                    mutableListOf<Pair<Int, Int>>()

                for (
                    i in 0 until waypoints.length()
                ) {

                    val waypoint =
                        waypoints.getJSONObject(
                            i
                        )

                    val inputIndex =
                        waypoint.getInt(
                            "waypoint_index"
                        )

                    val tripsIndex =
                        waypoint.getInt(
                            "trips_index"
                        )

                    ordered.add(
                        Pair(
                            tripsIndex,
                            inputIndex
                        )
                    )
                }

                val newOrder =
                    waypoints
                        .let {

                            val list =
                                mutableListOf<Pair<Int, Stop>>()

                            for (
                                i in 0 until it.length()
                            ) {

                                val waypoint =
                                    it.getJSONObject(i)

                                val originalIndex =
                                    waypoint.getInt(
                                        "waypoint_index"
                                    )

                                val routeIndex =
                                    waypoint.getInt(
                                        "trips_index"
                                    )

                                list.add(
                                    Pair(
                                        routeIndex,
                                        stops[originalIndex]
                                    )
                                )
                            }

                            list.sortedBy {
                                it.first
                            }.map {
                                it.second
                            }
                        }

                runOnUiThread {

                    stops.clear()

                    stops.addAll(
                        newOrder
                    )

                    currentStopIndex = 0

                    progressBar.visibility =
                        View.GONE

                    optimizeButton.isEnabled =
                        true

                    updateScreen()

                    showOptimizedRoute(
                        json
                    )

                    Toast.makeText(
                        this,
                        "Rota optimize edildi.",
                        Toast.LENGTH_SHORT
                    ).show()
                }

            } catch (e: Exception) {

                runOnUiThread {

                    progressBar.visibility =
                        View.GONE

                    optimizeButton.isEnabled =
                        true

                    Toast.makeText(
                        this,
                        "Rota oluşturulamadı.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    // ====================================================
    // MAP
    // ====================================================

    private fun showStopsOnMap() {

        if (stops.isEmpty()) {
            return
        }

        val points =
            stops.joinToString(",") {

                "[${it.latitude},${it.longitude}]"
            }

        val javascript =
            "setStops([$points]);"

        webView.evaluateJavascript(
            javascript,
            null
        )
    }

    private fun showOptimizedRoute(
        json: org.json.JSONObject
    ) {

        try {

            val trips =
                json.getJSONArray(
                    "trips"
                )

            if (trips.length() == 0) {
                showStopsOnMap()
                return
            }

            val trip =
                trips.getJSONObject(0)

            val geometry =
                trip.getJSONObject(
                    "geometry"
                )

            val coordinates =
                geometry.getJSONArray(
                    "coordinates"
                )

            val route =
                StringBuilder()

            for (
                i in 0 until coordinates.length()
            ) {

                val point =
                    coordinates.getJSONArray(i)

                val longitude =
                    point.getDouble(0)

                val latitude =
                    point.getDouble(1)

                if (route.isNotEmpty()) {
                    route.append(",")
                }

                route.append(
                    "[$latitude,$longitude]"
                )
            }

            val points =
                stops.joinToString(",") {

                    "[${it.latitude},${it.longitude}]"
                }

            val javascript =
                "setRoute([$points],[$route]);"

            webView.evaluateJavascript(
                javascript,
                null
            )

        } catch (e: Exception) {

            showStopsOnMap()
        }
    }

    // ====================================================
    // NAVIGATION
    // ====================================================

    private fun openNavigation() {

        if (
            stops.isEmpty() ||
            currentStopIndex >= stops.size
        ) {
            return
        }

        val stop =
            stops[currentStopIndex]

        val uri =
            Uri.parse(
                "geo:${stop.latitude}," +
                        "${stop.longitude}" +
                        "?q=${Uri.encode(stop.address)}"
            )

        val intent =
            Intent(
                Intent.ACTION_VIEW,
                uri
            )

        try {

            startActivity(intent)

        } catch (e: Exception) {

            Toast.makeText(
                this,
                "Navigasyon uygulaması bulunamadı.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    // ====================================================
    // DELIVERED
    // ====================================================

    private fun markDelivered() {

        if (
            currentStopIndex >= stops.size
        ) {
            return
        }

        stops[currentStopIndex]
            .delivered = true

        if (
            currentStopIndex <
            stops.size - 1
        ) {

            currentStopIndex++
        }

        updateScreen()

        showStopsOnMap()

        if (
            currentStopIndex >= stops.size - 1 &&
            stops.last().delivered
        ) {

            Toast.makeText(
                this,
                "Tüm teslimatlar tamamlandı.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    // ====================================================
    // MAP HTML
    // ====================================================

    private fun createMapHtml(): String {

        return """
<!DOCTYPE html>
<html>
<head>

<meta name="viewport"
      content="width=device-width,
      initial-scale=1.0,
      maximum-scale=1.0,
      user-scalable=no">

<link rel="stylesheet"
      href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css"/>

<script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js">
</script>

<style>

html, body {
    margin: 0;
    padding: 0;
    width: 100%;
    height: 100%;
}

#map {
    width: 100%;
    height: 100%;
}

</style>

</head>

<body>

<div id="map"></div>

<script>

var map = L.map('map').setView(
    [52.52, 13.405],
    11
);

L.tileLayer(
    'https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png',
    {
        maxZoom: 19,
        attribution:
        '&copy; OpenStreetMap contributors'
    }
).addTo(map);

var markers = [];

var routeLine = null;

function clearMarkers() {

    markers.forEach(
        function(marker) {
            map.removeLayer(marker);
        }
    );

    markers = [];
}

function setStops(points) {

    clearMarkers();

    if (!points || points.length === 0) {
        return;
    }

    var bounds = [];

    points.forEach(
        function(point, index) {

            var marker =
                L.marker(point)
                .addTo(map)
                .bindPopup(
                    '<b>Stop ' +
                    (index + 1) +
                    '</b>'
                );

            markers.push(marker);

            bounds.push(point);
        }
    );

    map.fitBounds(bounds, {
        padding: [30, 30]
    });
}

function setRoute(points, route) {

    clearMarkers();

    if (routeLine) {
        map.removeLayer(routeLine);
        routeLine = null;
    }

    if (!points || points.length === 0) {
        return;
    }

    var bounds = [];

    points.forEach(
        function(point, index) {

            var marker =
                L.marker(point)
                .addTo(map)
                .bindPopup(
                    '<b>' +
                    (index + 1) +
                    '</b>'
                );

            markers.push(marker);

            bounds.push(point);
        }
    );

    if (route && route.length > 0) {

        routeLine =
            L.polyline(
                route,
                {
                    weight: 5
                }
            ).addTo(map);

        bounds =
            bounds.concat(route);
    }

    map.fitBounds(bounds, {
        padding: [30, 30]
    });
}

</script>

</body>
</html>
        """.trimIndent()
    }

    override fun onDestroy() {

        searchRunnable?.let {
            handler.removeCallbacks(it)
        }

        executor.shutdownNow()

        webView.destroy()

        super.onDestroy()
    }
}
