package de.icekonig.routeplanner

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.MapsInitializer
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

data class Customer(
    val id: Long,
    var name: String,
    var address: String,
    var phone: String,
    var serviceMinutes: Int = 10,
    var latitude: Double = 0.0,
    var longitude: Double = 0.0,
    var delivered: Boolean = false
)

class MainActivity : AppCompatActivity() {

    private lateinit var mapView: MapView
    private var googleMap: GoogleMap? = null

    private val customers = mutableListOf<Customer>()
    private var currentIndex = 0

    private lateinit var customerTitle: TextView
    private lateinit var customerInfo: TextView
    private lateinit var statusText: TextView

    private val prefsName = "ice_route_planner"
    private val customersKey = "customers"

    private val berlinDepot = LatLng(52.48, 13.43)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        loadCustomers()

        MapsInitializer.initialize(this)

        createUserInterface(savedInstanceState)
    }

    private fun createUserInterface(savedInstanceState: Bundle?) {

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(12, 12, 12, 12)

        // -------------------------
        // ÜST BİLGİ
        // -------------------------

        customerTitle = TextView(this)
        customerTitle.textSize = 20f
        customerTitle.setPadding(8, 8, 8, 4)

        customerInfo = TextView(this)
        customerInfo.textSize = 15f
        customerInfo.setPadding(8, 4, 8, 8)

        statusText = TextView(this)
        statusText.textSize = 14f
        statusText.setPadding(8, 4, 8, 8)

        root.addView(customerTitle)
        root.addView(customerInfo)
        root.addView(statusText)

        // -------------------------
        // HARİTA
        // -------------------------

        mapView = MapView(this)

        root.addView(
            mapView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        mapView.onCreate(savedInstanceState)

        // -------------------------
        // BUTONLAR
        // -------------------------

        val buttonRow1 = LinearLayout(this)
        buttonRow1.orientation = LinearLayout.HORIZONTAL

        val addButton = Button(this)
        addButton.text = "+ Müşteri Ekle"

        val nextButton = Button(this)
        nextButton.text = "Sıradaki"

        buttonRow1.addView(
            addButton,
            LinearLayout.LayoutParams(0, 55.dp(), 1f)
        )

        buttonRow1.addView(
            nextButton,
            LinearLayout.LayoutParams(0, 55.dp(), 1f)
        )

        root.addView(buttonRow1)

        val buttonRow2 = LinearLayout(this)
        buttonRow2.orientation = LinearLayout.HORIZONTAL

        val navigateButton = Button(this)
        navigateButton.text = "🚗 Google Maps"

        val deliveredButton = Button(this)
        deliveredButton.text = "✅ Teslim Edildi"

        buttonRow2.addView(
            navigateButton,
            LinearLayout.LayoutParams(0, 55.dp(), 1f)
        )

        buttonRow2.addView(
            deliveredButton,
            LinearLayout.LayoutParams(0, 55.dp(), 1f)
        )

        root.addView(buttonRow2)

        setContentView(root)

        // -------------------------
        // MAP
        // -------------------------

        mapView.getMapAsync { map ->

            googleMap = map

            map.uiSettings.isZoomControlsEnabled = true
            map.uiSettings.isMyLocationButtonEnabled = true

            if (
                ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                googleMap?.isMyLocationEnabled = true

                val fusedLocationClient =
                    LocationServices.getFusedLocationProviderClient(this)

                fusedLocationClient.lastLocation.addOnSuccessListener { location ->

                    if (location != null && customers.isEmpty()) {

                        googleMap?.moveCamera(
                            CameraUpdateFactory.newLatLngZoom(
                                LatLng(
                                    location.latitude,
                                    location.longitude
                                ),
                                11f
                            )
                        )

                    } else {

                        googleMap?.moveCamera(
                            CameraUpdateFactory.newLatLngZoom(
                                berlinDepot,
                                11f
                            )
                        )
                    }
                }

            } else {

                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    ),
                    100
                )

                googleMap?.moveCamera(
                    CameraUpdateFactory.newLatLngZoom(
                        berlinDepot,
                        11f
                    )
                )
            }

            renderCustomers()
            renderCurrentCustomer()
        }

        // -------------------------
        // BUTONLAR
        // -------------------------

        addButton.setOnClickListener {
            showAddCustomerDialog()
        }

        nextButton.setOnClickListener {

            if (customers.isEmpty()) {
                Toast.makeText(
                    this,
                    "Önce müşteri ekleyin.",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            if (currentIndex < customers.size - 1) {
                currentIndex++
                renderCurrentCustomer()
            } else {
                Toast.makeText(
                    this,
                    "Rotanın son müşterisindesiniz.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        navigateButton.setOnClickListener {
            openGoogleMaps()
        }

        deliveredButton.setOnClickListener {
            markCurrentDelivered()
        }
    }

    // ============================================================
    // MÜŞTERİ EKLE
    // ============================================================

    private fun showAddCustomerDialog() {

        val container = LinearLayout(this)
        container.orientation = LinearLayout.VERTICAL
        container.setPadding(30, 10, 30, 10)

        val nameInput = EditText(this)
        nameInput.hint = "Müşteri adı"

        val addressInput = EditText(this)
        addressInput.hint = "Tam teslimat adresi"

        val phoneInput
