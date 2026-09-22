package de.icekonig.routeplanner

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.widget.Autocomplete
import com.google.android.libraries.places.widget.model.AutocompleteActivityMode

data class Stop(
    val address: String,
    val latitude: Double,
    val longitude: Double
)

class MainActivity : AppCompatActivity() {

    private val stops = mutableListOf<Stop>()

    private lateinit var addressButton: Button
    private lateinit var stopList: TextView
    private lateinit var optimizeButton: Button

    private val autocompleteRequestCode = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        /*
         * Google Places başlat
         *
         * API key daha sonra GitHub Secret üzerinden bağlanacak.
         */
        val apiKey = ""

        if (!Places.isInitialized()) {
            Places.initializeWithNewPlacesApiEnabled(
                applicationContext,
                apiKey
            )
        }

        createScreen()
    }

    private fun createScreen() {

        val root = LinearLayout(this)

        root.orientation = LinearLayout.VERTICAL

        root.setPadding(
            20,
            20,
            20,
            12
        )

        // --------------------------------
        // BAŞLIK
        // --------------------------------

        val title = TextView(this)

        title.text = "ROTA OLUŞTUR"

        title.textSize = 22f

        root.addView(
            title,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        // --------------------------------
        // ADRES ARAMA BUTONU
        // --------------------------------

        addressButton = Button(this)

        addressButton.text = "🔍  İlk adresi ara"

        addressButton.textSize = 16f

        root.addView(
            addressButton,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        addressButton.setOnClickListener {
            openAddressSearch()
        }

        // --------------------------------
        // ADRES LİSTESİ
        // --------------------------------

        stopList = TextView(this)

        stopList.textSize = 15f

        stopList.setPadding(
            4,
            20,
            4,
            20
        )

        root.addView(
            stopList,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        // --------------------------------
        // ROTAYI OPTİMİZE ET
        // --------------------------------

        optimizeButton = Button(this)

        optimizeButton.text = "🚀  ROTAYI OPTİMİZE ET"

        optimizeButton.textSize = 15f

        optimizeButton.isEnabled = false

        root.addView(
            optimizeButton,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        optimizeButton.setOnClickListener {

            if (stops.size < 2) {

                Toast.makeText(
                    this,
                    "En az 2 adres ekleyin.",
                    Toast.LENGTH_SHORT
                ).show()

                return@setOnClickListener
            }

            Toast.makeText(
                this,
                "Rota optimizasyonu bir sonraki adımda.",
                Toast.LENGTH_SHORT
            ).show()
        }

        setContentView(root)

        updateStopList()
    }

    // =========================================================
    // GOOGLE ADRES ARAMA
    // =========================================================

    private fun openAddressSearch() {

        val fields = listOf(
            Place.Field.ID,
            Place.Field.ADDRESS,
            Place.Field.LAT_LNG,
            Place.Field.NAME
        )

        val intent =
            Autocomplete.IntentBuilder(
                AutocompleteActivityMode.FULLSCREEN,
                fields
            )
                .setCountries(listOf("DE"))
                .build(this)

        startActivityForResult(
            intent,
            autocompleteRequestCode
        )
    }

    // =========================================================
    // ADRES SEÇİLDİ
    // =========================================================

    @Deprecated("Deprecated in Android API")
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {

        super.onActivityResult(
            requestCode,
            resultCode,
            data
        )

        if (
            requestCode !=
            autocompleteRequestCode
        ) {
            return
        }

        if (
            resultCode ==
            RESULT_OK &&
            data != null
        ) {

            val place =
                Autocomplete
                    .getPlaceFromIntent(data)

            val address =
                place.address

            val latLng =
                place.latLng

            if (
                address != null &&
                latLng != null
            ) {

                stops.add(
                    Stop(
                        address = address,
                        latitude =
                            latLng.latitude,
                        longitude =
                            latLng.longitude
                    )
                )

                updateStopList()

                /*
                 * EN ÖNEMLİ KISIM:
                 *
                 * Adres seçildiği anda
                 * tekrar arama ekranını açıyoruz.
                 */

                openAddressSearch()
            }
        }
    }

    // =========================================================
    // LİSTEYİ GÜNCELLE
    // =========================================================

    private fun updateStopList() {

        if (stops.isEmpty()) {

            stopList.text =
                "Henüz adres eklenmedi.\n\n" +
                "İlk adresi aramak için yukarıdaki butona bas."

            optimizeButton.isEnabled = false

            addressButton.text =
                "🔍  İlk adresi ara"

            return
        }

        val text = StringBuilder()

        text.append(
            "${stops.size} teslimat\n\n"
        )

        stops.forEachIndexed { index, stop ->

            text.append(
                "${index + 1}. ${stop.address}\n\n"
            )
        }

        stopList.text = text.toString()

        optimizeButton.isEnabled =
            stops.size >= 2

        addressButton.text =
            "🔍  Sonraki adresi ara"
    }
}
