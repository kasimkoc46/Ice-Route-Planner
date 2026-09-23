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

                    suggestions
