package de.icekonig.routeplanner

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.MapsInitializer
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

        createInterface(savedInstanceState)
    }

    private fun createInterface(savedInstanceState: Bundle?) {

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(6.dp(), 4.dp(), 6.dp(), 4.dp())

        // =========================
        // ÜST BİLGİ
        // =========================

        customerTitle = TextView(this)
        customerTitle.textSize = 17f
        customerTitle.setPadding(6.dp(), 2.dp(), 6.dp(), 1.dp)

        customerInfo = TextView(this)
        customerInfo.textSize = 13f
        customerInfo.setPadding(6.dp(), 1.dp(), 6.dp(), 1.dp)

        statusText = TextView(this)
        statusText.textSize = 12f
        statusText.setPadding(6.dp(), 1.dp(), 6.dp(), 3.dp)

        root.addView(customerTitle)

        root.addView(customerInfo)

        root.addView(statusText)

        // =========================
        // HARİTA
        // =========================

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

        // =========================
        // BUTONLAR
        // =========================

        val row1 = LinearLayout(this)
        row1.orientation = LinearLayout.HORIZONTAL

        val addButton = createButton("+ Müşteri")

        val nextButton = createButton("Sıradaki")

        row1.addView(
            addButton,
            LinearLayout.LayoutParams(
                0,
                48.dp(),
                1f
            )
        )

        row1.addView(
            nextButton,
            LinearLayout.LayoutParams(
                0,
                48.dp(),
                1f
            )
        )

        root.addView(row1)

        val row2 = LinearLayout(this)
        row2.orientation = LinearLayout.HORIZONTAL

        val navigateButton = createButton("Google Maps")

        val deliveredButton = createButton("Teslim Edildi")

        row2.addView(
            navigateButton,
            LinearLayout.LayoutParams(
                0,
                48.dp(),
                1f
            )
        )

        row2.addView(
            deliveredButton,
            LinearLayout.LayoutParams(
                0,
                48.dp(),
                1f
            )
        )

        root.addView(row2)

        setContentView(root)

        // =========================
        // GOOGLE MAP
        // =========================

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

                map.isMyLocationEnabled = true

                val locationClient =
                    LocationServices.getFusedLocationProviderClient(this)

                locationClient.lastLocation.addOnSuccessListener { location ->

                    if (location != null && customers.isEmpty()) {

                        map.moveCamera(
                            CameraUpdateFactory.newLatLngZoom(
                                LatLng(
                                    location.latitude,
                                    location.longitude
                                ),
                                11f
                            )
                        )

                    } else {

                        map.moveCamera(
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

                map.moveCamera(
                    CameraUpdateFactory.newLatLngZoom(
                        berlinDepot,
                        11f
                    )
                )
            }

            renderCustomers()
            renderCurrentCustomer()
        }

        // =========================
        // BUTONLAR
        // =========================

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

            val next =
                customers.indexOfFirst {
                    !it.delivered && customers.indexOf(it) > currentIndex
                }

            if (next >= 0) {

                currentIndex = next
                renderCurrentCustomer()

            } else {

                Toast.makeText(
                    this,
                    "Başka bekleyen müşteri yok.",
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

    // =========================================================
    // BUTON OLUŞTUR
    // =========================================================

    private fun createButton(text: String): Button {

        return Button(this).apply {

            this.text = text
            textSize = 12f

            minimumHeight = 0
            minimumWidth = 0

            setPadding(
                2.dp(),
                0,
                2.dp(),
                0
            )
        }
    }

    // =========================================================
    // MÜŞTERİ EKLE
    // =========================================================

    private fun showAddCustomerDialog() {

        val container = LinearLayout(this)

        container.orientation = LinearLayout.VERTICAL

        container.setPadding(
            20.dp(),
            4.dp(),
            20.dp(),
            4.dp()
        )

        val nameInput = EditText(this)
        nameInput.hint = "Müşteri adı"
        nameInput.textSize = 15f

        val addressInput = EditText(this)
        addressInput.hint = "Tam teslimat adresi"
        addressInput.textSize = 15f

        val phoneInput = EditText(this)
        phoneInput.hint = "Telefon"
        phoneInput.textSize = 15f

        val serviceInput = EditText(this)
        serviceInput.hint = "Servis süresi (dakika)"
        serviceInput.setText("10")
        serviceInput.textSize = 15f
        serviceInput.inputType = 2

        container.addView(nameInput)
        container.addView(addressInput)
        container.addView(phoneInput)
        container.addView(serviceInput)

        val dialog = AlertDialog.Builder(this)
            .setTitle("Yeni Müşteri")
            .setView(container)
            .setNegativeButton("İptal", null)
            .setPositiveButton("Kaydet", null)
            .create()

        dialog.setOnShowListener {

            dialog.getButton(
                AlertDialog.BUTTON_POSITIVE
            ).setOnClickListener {

                val name =
                    nameInput.text.toString().trim()

                val address =
                    addressInput.text.toString().trim()

                val phone =
                    phoneInput.text.toString().trim()

                val serviceMinutes =
                    serviceInput.text
                        .toString()
                        .toIntOrNull()
                        ?: 10

                if (name.isEmpty()) {

                    nameInput.error =
                        "Müşteri adı gerekli"

                    return@setOnClickListener
                }

                if (address.isEmpty()) {

                    addressInput.error =
                        "Adres gerekli"

                    return@setOnClickListener
                }

                dialog.dismiss()

                geocodeAndSaveCustomer(
                    name,
                    address,
                    phone,
                    serviceMinutes
                )
            }
        }

        dialog.show()
    }

    // =========================================================
    // ADRESİ KOORDİNATA ÇEVİR
    // =========================================================

    private fun geocodeAndSaveCustomer(
        name: String,
        address: String,
        phone: String,
        serviceMinutes: Int
    ) {

        Toast.makeText(
            this,
            "Adres aranıyor...",
            Toast.LENGTH_SHORT
        ).show()

        Thread {

            try {

                val geocoder =
                    Geocoder(
                        this,
                        Locale.GERMANY
                    )

                @Suppress("DEPRECATION")
                val results =
                    geocoder.getFromLocationName(
                        address,
                        1
                    )

                runOnUiThread {

                    if (!results.isNullOrEmpty()) {

                        val result =
                            results[0]

                        val customer =
                            Customer(
                                id = System.currentTimeMillis(),
                                name = name,
                                address = address,
                                phone = phone,
                                serviceMinutes =
                                    serviceMinutes,
                                latitude =
                                    result.latitude,
                                longitude =
                                    result.longitude
                            )

                        customers.add(customer)

                        saveCustomers()

                        currentIndex =
                            customers.lastIndex

                        renderCustomers()

                        renderCurrentCustomer()

                        googleMap?.animateCamera(
                            CameraUpdateFactory
                                .newLatLngZoom(
                                    LatLng(
                                        customer.latitude,
                                        customer.longitude
                                    ),
                                    15f
                                )
                        )

                        Toast.makeText(
                            this,
                            "$name eklendi.",
                            Toast.LENGTH_SHORT
                        ).show()

                    } else {

                        Toast.makeText(
                            this,
                            "Adres bulunamadı.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }

            } catch (e: Exception) {

                runOnUiThread {

                    Toast.makeText(
                        this,
                        "Adres aranırken hata oluştu.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }.start()
    }

    // =========================================================
    // MÜŞTERİLERİ HARİTAYA ÇİZ
    // =========================================================

    private fun renderCustomers() {

        val map = googleMap ?: return

        map.clear()

        customers.forEachIndexed { index, customer ->

            if (
                customer.latitude != 0.0 &&
                customer.longitude != 0.0
            ) {

                val marker =
                    map.addMarker(
                        MarkerOptions()
                            .position(
                                LatLng(
                                    customer.latitude,
                                    customer.longitude
                                )
                            )
                            .title(
                                "${index + 1}. ${customer.name}"
                            )
                            .snippet(
                                if (customer.delivered) {
                                    "Teslim edildi"
                                } else {
                                    "${customer.address} • " +
                                            "${customer.serviceMinutes} dk"
                                }
                            )
                    )

                marker?.tag = customer.id
            }
        }

        map.setOnMarkerClickListener { marker ->

            val id =
                marker.tag as? Long

            if (id != null) {

                val index =
                    customers.indexOfFirst {
                        it.id == id
                    }

                if (index >= 0) {

                    currentIndex = index

                    renderCurrentCustomer()
                }
            }

            false
        }
    }

    // =========================================================
    // MEVCUT MÜŞTERİ
    // =========================================================

    private fun renderCurrentCustomer() {

        if (customers.isEmpty()) {

            customerTitle.text =
                "Bugünkü rota boş"

            customerInfo.text =
                "Yeni müşteri eklemek için + Müşteri"

            statusText.text = ""

            return
        }

        if (currentIndex >= customers.size) {
            currentIndex = customers.lastIndex
        }

        val customer =
            customers[currentIndex]

        customerTitle.text =
            "${currentIndex + 1}/${customers.size}  ${customer.name}"

        customerInfo.text =
            "${customer.address}\n" +
                    "${customer.phone.ifEmpty { "-" }}  •  " +
                    "${customer.serviceMinutes} dk"

        statusText.text =
            if (customer.delivered) {
                "TESLİMAT TAMAMLANDI"
            } else {
                "TESLİMAT BEKLİYOR"
            }

        if (
            customer.latitude != 0.0 &&
            customer.longitude != 0.0
        ) {

            googleMap?.animateCamera(
                CameraUpdateFactory
                    .newLatLngZoom(
                        LatLng(
                            customer.latitude,
                            customer.longitude
                        ),
                        15f
                    )
            )
        }
    }

    // =========================================================
    // TESLİM EDİLDİ
    // =========================================================

    private fun markCurrentDelivered() {

        if (customers.isEmpty()) {

            Toast.makeText(
                this,
                "Müşteri yok.",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        val customer =
            customers[currentIndex]

        if (customer.delivered) {

            Toast.makeText(
                this,
                "Bu müşteri zaten teslim edildi.",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        customer.delivered = true

        saveCustomers()

        renderCustomers()

        moveToNextPendingCustomer()
    }

    private fun moveToNextPendingCustomer() {

        val next =
            customers.indexOfFirst {
                !it.delivered
            }

        if (next >= 0) {

            currentIndex = next

            renderCurrentCustomer()

            Toast.makeText(
                this,
                "Sıradaki müşteriye geçildi.",
                Toast.LENGTH_SHORT
            ).show()

        } else {

            customerTitle.text =
                "Tüm teslimatlar tamamlandı"

            customerInfo.text =
                "Bugünkü rota bitti."

            statusText.text =
                "Tüm müşteriler teslim edildi."
        }
    }

    // =========================================================
    // GOOGLE MAPS NAVİGASYON
    // =========================================================

    private fun openGoogleMaps() {

        if (customers.isEmpty()) {

            Toast.makeText(
                this,
                "Önce müşteri ekleyin.",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        val customer =
            customers[currentIndex]

        if (
            customer.latitude == 0.0 ||
            customer.longitude == 0.0
        ) {

            Toast.makeText(
                this,
                "Bu müşterinin konumu bulunamadı.",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        val uri =
            Uri.parse(
                "google.navigation:q=" +
                        "${customer.latitude}," +
                        "${customer.longitude}"
            )

        val intent =
            Intent(
                Intent.ACTION_VIEW,
                uri
            )

        intent.setPackage(
            "com.google.android.apps.maps"
        )

        try {

            startActivity(intent)

        } catch (e: Exception) {

            val webUri =
                Uri.parse(
                    "https://www.google.com/maps/dir/?api=1" +
                            "&destination=" +
                            "${customer.latitude}," +
                            "${customer.longitude}"
                )

            startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    webUri
                )
            )
        }
    }

    // =========================================================
    // KAYDET
    // =========================================================

    private fun saveCustomers() {

        val array = JSONArray()

        customers.forEach { customer ->

            val obj = JSONObject()

            obj.put("id", customer.id)
            obj.put("name", customer.name)
            obj.put("address", customer.address)
            obj.put("phone", customer.phone)
            obj.put(
                "serviceMinutes",
                customer.serviceMinutes
            )
            obj.put(
                "latitude",
                customer.latitude
            )
            obj.put(
                "longitude",
                customer.longitude
            )
            obj.put(
                "delivered",
                customer.delivered
            )

            array.put(obj)
        }

        getSharedPreferences(
            prefsName,
            Context.MODE_PRIVATE
        )
            .edit()
            .putString(
                customersKey,
                array.toString()
            )
            .apply()
    }

    // =========================================================
    // YÜKLE
    // =========================================================

    private fun loadCustomers() {

        val json =
            getSharedPreferences(
                prefsName,
                Context.MODE_PRIVATE
            )
                .getString(
                    customersKey,
                    null
                )
                ?: return

        try {

            val array =
                JSONArray(json)

            customers.clear()

            for (i in 0 until array.length()) {

                val obj =
                    array.getJSONObject(i)

                customers.add(
                    Customer(
                        id =
                            obj.getLong("id"),

                        name =
                            obj.getString("name"),

                        address =
                            obj.getString("address"),

                        phone =
                            obj.optString("phone"),

                        serviceMinutes =
                            obj.optInt(
                                "serviceMinutes",
                                10
                            ),

                        latitude =
                            obj.optDouble(
                                "latitude",
                                0.0
                            ),

                        longitude =
                            obj.optDouble(
                                "longitude",
                                0.0
                            ),

                        delivered =
                            obj.optBoolean(
                                "delivered",
                                false
                            )
                    )
                )
            }

        } catch (e: Exception) {

            customers.clear()
        }
    }

    // =========================================================
    // MAPVIEW YAŞAM DÖNGÜSÜ
    // =========================================================

    override fun onStart() {
        super.onStart()
        mapView.onStart()
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        mapView.onPause()
        super.onPause()
    }

    override fun onStop() {
        mapView.onStop()
        super.onStop()
    }

    override fun onDestroy() {
        mapView.onDestroy()
        super.onDestroy()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView.onLowMemory()
    }

    override fun onSaveInstanceState(
        outState: Bundle
    ) {
        mapView.onSaveInstanceState(outState)
        super.onSaveInstanceState(outState)
    }

    // =========================================================
    // DP
    // =========================================================

    private fun Int.dp(): Int {
        return (
            this *
                resources.displayMetrics.density
            ).toInt()
    }
}
