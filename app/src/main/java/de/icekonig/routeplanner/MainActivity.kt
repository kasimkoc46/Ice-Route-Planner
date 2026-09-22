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

        buildScreen(savedInstanceState)
    }

    private fun buildScreen(savedInstanceState: Bundle?) {

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(8, 4, 8, 4)

        // Müşteri başlığı
        customerTitle = TextView(this)
        customerTitle.textSize = 17f
        customerTitle.setPadding(4, 2, 4, 1)

        // Müşteri bilgileri
        customerInfo = TextView(this)
        customerInfo.textSize = 13f
        customerInfo.setPadding(4, 1, 4, 1)

        // Durum
        statusText = TextView(this)
        statusText.textSize = 12f
        statusText.setPadding(4, 1, 4, 3)

        root.addView(customerTitle)
        root.addView(customerInfo)
        root.addView(statusText)

        // HARİTA
        mapView = MapView(this)

        val mapParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            0
        )

        mapParams.weight = 1f

        root.addView(mapView, mapParams)

        mapView.onCreate(savedInstanceState)

        // 1. SATIR BUTONLAR
        val row1 = LinearLayout(this)
        row1.orientation = LinearLayout.HORIZONTAL

        val addButton = Button(this)
        addButton.text = "+ Müşteri"
        addButton.textSize = 12f
        addButton.minimumHeight = 0
        addButton.minimumWidth = 0
        addButton.setPadding(2, 0, 2, 0)

        val nextButton = Button(this)
        nextButton.text = "Sıradaki"
        nextButton.textSize = 12f
        nextButton.minimumHeight = 0
        nextButton.minimumWidth = 0
        nextButton.setPadding(2, 0, 2, 0)

        row1.addView(
            addButton,
            LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        )

        row1.addView(
            nextButton,
            LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        )

        root.addView(row1)

        // 2. SATIR BUTONLAR
        val row2 = LinearLayout(this)
        row2.orientation = LinearLayout.HORIZONTAL

        val mapsButton = Button(this)
        mapsButton.text = "Google Maps"
        mapsButton.textSize = 12f
        mapsButton.minimumHeight = 0
        mapsButton.minimumWidth = 0
        mapsButton.setPadding(2, 0, 2, 0)

        val deliveredButton = Button(this)
        deliveredButton.text = "Teslim Edildi"
        deliveredButton.textSize = 12f
        deliveredButton.minimumHeight = 0
        deliveredButton.minimumWidth = 0
        deliveredButton.setPadding(2, 0, 2, 0)

        row2.addView(
            mapsButton,
            LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        )

        row2.addView(
            deliveredButton,
            LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        )

        root.addView(row2)

        setContentView(root)

        // GOOGLE MAP
        mapView.getMapAsync { map ->

            googleMap = map

            map.uiSettings.isZoomControlsEnabled = true
            map.uiSettings.isMyLocationButtonEnabled = true

            enableLocation()

            renderCustomers()
            renderCurrentCustomer()

            map.setOnMarkerClickListener { marker ->

                val id = marker.tag as? Long

                if (id != null) {

                    val index = customers.indexOfFirst {
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

        // BUTONLAR

        addButton.setOnClickListener {
            showAddCustomerDialog()
        }

        nextButton.setOnClickListener {
            goToNextCustomer()
        }

        mapsButton.setOnClickListener {
            openGoogleMaps()
        }

        deliveredButton.setOnClickListener {
            markCurrentDelivered()
        }
    }

    // =========================================================
    // KONUM
    // =========================================================

    private fun enableLocation() {

        val fineGranted =
            ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

        val coarseGranted =
            ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

        if (!fineGranted && !coarseGranted) {

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

            return
        }

        googleMap?.isMyLocationEnabled = true

        val locationClient =
            LocationServices.getFusedLocationProviderClient(this)

        locationClient.lastLocation.addOnSuccessListener { location ->

            if (location != null) {

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
    }

    // =========================================================
    // MÜŞTERİ EKLE
    // =========================================================

    private fun showAddCustomerDialog() {

        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(20, 4, 20, 4)

        val name = EditText(this)
        name.hint = "Müşteri adı"

        val address = EditText(this)
        address.hint = "Tam teslimat adresi"

        val phone = EditText(this)
        phone.hint = "Telefon"

        val service = EditText(this)
        service.hint = "Servis süresi (dakika)"
        service.setText("10")
        service.inputType = 2

        box.addView(name)
        box.addView(address)
        box.addView(phone)
        box.addView(service)

        val dialog = AlertDialog.Builder(this)
            .setTitle("Yeni Müşteri")
            .setView(box)
            .setNegativeButton("İptal", null)
            .setPositiveButton("Kaydet", null)
            .create()

        dialog.setOnShowListener {

            dialog.getButton(
                AlertDialog.BUTTON_POSITIVE
            ).setOnClickListener {

                val customerName =
                    name.text.toString().trim()

                val customerAddress =
                    address.text.toString().trim()

                val customerPhone =
                    phone.text.toString().trim()

                val minutes =
                    service.text.toString()
                        .toIntOrNull()
                        ?: 10

                if (customerName.isEmpty()) {
                    name.error = "Müşteri adı gerekli"
                    return@setOnClickListener
                }

                if (customerAddress.isEmpty()) {
                    address.error = "Adres gerekli"
                    return@setOnClickListener
                }

                dialog.dismiss()

                findAddress(
                    customerName,
                    customerAddress,
                    customerPhone,
                    minutes
                )
            }
        }

        dialog.show()
    }

    // =========================================================
    // ADRES ARAMA
    // =========================================================

    private fun findAddress(
        name: String,
        address: String,
        phone: String,
        minutes: Int
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
                val result =
                    geocoder.getFromLocationName(
                        address,
                        1
                    )

                runOnUiThread {

                    if (result.isNullOrEmpty()) {

                        Toast.makeText(
                            this,
                            "Adres bulunamadı.",
                            Toast.LENGTH_LONG
                        ).show()

                        return@runOnUiThread
                    }

                    val location = result[0]

                    val customer =
                        Customer(
                            id = System.currentTimeMillis(),
                            name = name,
                            address = address,
                            phone = phone,
                            serviceMinutes = minutes,
                            latitude = location.latitude,
                            longitude = location.longitude
                        )

                    customers.add(customer)

                    currentIndex =
                        customers.lastIndex

                    saveCustomers()

                    renderCustomers()
                    renderCurrentCustomer()

                    googleMap?.animateCamera(
                        CameraUpdateFactory.newLatLngZoom(
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
    // HARİTA MARKERLARI
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
                CameraUpdateFactory.newLatLngZoom(
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
    // SIRADAKİ MÜŞTERİ
    // =========================================================

    private fun goToNextCustomer() {

        if (customers.isEmpty()) {

            Toast.makeText(
                this,
                "Önce müşteri ekleyin.",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        var nextIndex = currentIndex + 1

        while (nextIndex < customers.size) {

            if (!customers[nextIndex].delivered) {

                currentIndex = nextIndex

                renderCurrentCustomer()

                return
            }

            nextIndex++
        }

        val firstPending =
            customers.indexOfFirst {
                !it.delivered
            }

        if (firstPending >= 0) {

            currentIndex = firstPending

            renderCurrentCustomer()

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

        goToNextCustomer()
    }

    // =========================================================
    // GOOGLE MAPS
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

        val uri =
            Uri.parse(
                "google.navigation:q=" +
                        customer.latitude +
                        "," +
                        customer.longitude
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
                            customer.latitude +
                            "," +
                            customer.longitude
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
}
