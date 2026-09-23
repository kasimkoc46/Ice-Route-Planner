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
         * Google Places başlatılıyor.
         *
         * API anahtarını daha sonra GitHub Secret üzerinden
         * bağlayacağız.
         */

        val apiKey = ""

        if (!Places.isInitialized()) {
            Places.initialize(
                applicationContext,
                apiKey
            )
        }

        createScreen()
    }

    // =========================================================
    // ANA EKRAN
    // =========================================================

    private fun createScreen() {

        val root = LinearLayout(this)

        root.orientation = LinearLayout.VERTICAL

        root.setPadding(
            20,
            20,
            20,
            12
        )

        // -----------------------------------------------------
        // BAŞLIK
        // -----------------------------------------------------

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

        // -----------------------------------------------------
        // ADRES ARAMA
        // -----------------------------------------------------

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

        // -----------------------------------------------------
        // ADRES LİSTESİ
        // -----------------------------------------------------

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

        // -----------------------------------------------------
        // ROTA OPTİMİZE BUTONU
        // -----------------------------------------------------

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
                "${stops.size} adres hazır.",
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

        /*
         * Places SDK 5.3.0 için güncel alanlar:
         *
         * FORMATTED_ADDRESS
         * LOCATION
         */

        val fields = listOf(
            Place.Field.ID,
            Place.Field.FORMATTED_ADDRESS,
            Place.Field.LOCATION,
            Place.Field.DISPLAY_NAME
        )

        val intent =
            Autocomplete.IntentBuilder(
                AutocompleteActivityMode.FULLSCREEN,
                fields
            )
                .setCountries(
                    listOf("DE")
                )
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
            resultCode != RESULT_OK ||
            data == null
        ) {
            return
        }

        val place =
            Autocomplete.getPlaceFromIntent(data)

        /*
         * Places SDK 5.3.0:
         *
         * place.formattedAddress
         * place.location
         */

        val address =
            place.formattedAddress

        val location =
            place.location

        if (
            address == null ||
            location == null
        ) {

            Toast.makeText(
                this,
                "Adres bilgisi alınamadı.",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        val newStop = Stop(
            address = address,
            latitude = location.latitude,
            longitude = location.longitude
        )

        stops.add(newStop)

        updateStopList()

        /*
         * Kullanıcının istediği Spoke tarzı akış:
         *
         * Adres seçildi
         * ↓
         * Listeye eklendi
         * ↓
         * Otomatik olarak yeni adres arama ekranı açılır
         */

        openAddressSearch()
    }

    // =========================================================
    // ADRES LİSTESİNİ GÜNCELLE
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

        val text =
            StringBuilder()

        text.append(
            "${stops.size} TESLİMAT\n\n"
        )

        stops.forEachIndexed { index, stop ->

            text.append(
                "${index + 1}. ${stop.address}\n\n"
            )
        }

        stopList.text =
            text.toString()

        optimizeButton.isEnabled =
            stops.size >= 2

        addressButton.text =
            "🔍  Sonraki adresi ara"
    }
}
