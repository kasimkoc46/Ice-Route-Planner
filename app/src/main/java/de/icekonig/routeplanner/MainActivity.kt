package de.icekonig.routeplanner

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.MapsInitializer
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import android.widget.Button
import android.widget.TextView

private data class Stop(val name: String, val address: String, val lat: Double, val lng: Double, val goods: String)

class MainActivity : AppCompatActivity() {
    private lateinit var map: MapView
    private lateinit var stops: MutableList<Stop>
    private var current = 0
    private val berlinDepot = LatLng(52.48, 13.43)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        map = findViewById(R.id.map)
        map.onCreate(savedInstanceState)
        MapsInitializer.initialize(this)
        stops = mutableListOf(
            Stop("Café Beispiel", "Berlin", 52.493, 13.431, "20 kg Eiswürfel"),
            Stop("Event Kunde", "Berlin", 52.510, 13.455, "8 Karton Cocktail")
        )
        map.getMapAsync { googleMap ->
            googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(berlinDepot, 11.5f))
            stops.forEachIndexed { i, s -> googleMap.addMarker(MarkerOptions().position(LatLng(s.lat,s.lng)).title("${i+1}. ${s.name}")) }
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) googleMap.isMyLocationEnabled = true
            else ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 10)
        }
        renderStop()
        findViewById<Button>(R.id.navigate).setOnClickListener { openGoogleMaps() }
        findViewById<Button>(R.id.delivered).setOnClickListener { if (current < stops.size) { current++; renderStop() } }
    }

    private fun renderStop() {
        val title = findViewById<TextView>(R.id.nextStop)
        val info = findViewById<TextView>(R.id.stopInfo)
        if (current >= stops.size) { title.text = "Tüm teslimatlar tamamlandı"; info.text = "Bugünkü rota bitti."; return }
        val s = stops[current]
        title.text = "${current + 1}/${stops.size} · ${s.name}"
        info.text = "${s.address}\n${s.goods}\nServis süresi: 10 dk"
    }

    private fun openGoogleMaps() {
        if (current >= stops.size) return
        val s = stops[current]
        val uri = Uri.parse("google.navigation:q=${s.lat},${s.lng}&mode=d")
        startActivity(Intent(Intent.ACTION_VIEW, uri).apply { setPackage("com.google.android.apps.maps") })
    }

    override fun onStart() { super.onStart(); map.onStart() }
    override fun onResume() { super.onResume(); map.onResume() }
    override fun onPause() { map.onPause(); super.onPause() }
    override fun onStop() { map.onStop(); super.onStop() }
    override fun onDestroy() { map.onDestroy(); super.onDestroy() }
    override fun onLowMemory() { super.onLowMemory(); map.onLowMemory() }
}
