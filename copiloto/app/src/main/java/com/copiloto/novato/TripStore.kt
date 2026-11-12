package com.copiloto.novato

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * El "cuaderno" de viajes. Guarda los viajes terminados en un archivo
 * (trips.json) y recuerda si hay un viaje en curso, aunque Android cierre la app.
 *
 * La pantalla observa [trips] y [activeTripStart] y se redibuja sola cuando cambian.
 */
object TripStore {

    private const val FILE_NAME = "trips.json"
    private const val PREFS_NAME = "copiloto"
    private const val KEY_ACTIVE_START = "active_start"
    private const val KEY_ACTIVE_BY_CAR = "active_by_car"

    private lateinit var file: File
    private lateinit var prefs: SharedPreferences

    private val _trips = MutableStateFlow<List<Trip>>(emptyList())
    /** Viajes terminados, del más antiguo al más reciente. */
    val trips: StateFlow<List<Trip>> = _trips.asStateFlow()

    private val _activeTripStart = MutableStateFlow<Long?>(null)
    /** Hora de inicio del viaje en curso, o null si no hay ninguno. */
    val activeTripStart: StateFlow<Long?> = _activeTripStart.asStateFlow()

    /** Hay que llamarlo una vez antes de usar el resto (lo hace [CopilotoApp]). */
    @Synchronized
    fun init(context: Context) {
        if (::file.isInitialized) return
        val appContext = context.applicationContext
        file = File(appContext.filesDir, FILE_NAME)
        prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _trips.value = readTrips()
        if (prefs.contains(KEY_ACTIVE_START)) {
            _activeTripStart.value = prefs.getLong(KEY_ACTIVE_START, 0L)
        }
    }

    /**
     * Empieza un viaje. Si ya hay uno en curso no hace nada.
     * [byCar] indica si lo inició la conexión real con el coche (true)
     * o el botón de simulación (false).
     */
    @Synchronized
    fun startTrip(byCar: Boolean, now: Long = System.currentTimeMillis()) {
        if (_activeTripStart.value != null) return
        prefs.edit()
            .putLong(KEY_ACTIVE_START, now)
            .putBoolean(KEY_ACTIVE_BY_CAR, byCar)
            .apply()
        _activeTripStart.value = now
    }

    /**
     * Termina el viaje en curso y lo guarda.
     * Con [onlyIfStartedByCar] = true no toca los viajes simulados; así la
     * desconexión del coche no corta una simulación manual.
     */
    @Synchronized
    fun endTrip(onlyIfStartedByCar: Boolean = false, now: Long = System.currentTimeMillis()) {
        val start = _activeTripStart.value ?: return
        if (onlyIfStartedByCar && !prefs.getBoolean(KEY_ACTIVE_BY_CAR, false)) return

        prefs.edit().remove(KEY_ACTIVE_START).remove(KEY_ACTIVE_BY_CAR).apply()
        _activeTripStart.value = null

        val updated = _trips.value + Trip(startMillis = start, endMillis = maxOf(now, start))
        _trips.value = updated
        writeTrips(updated)
    }

    /** Borra todo el historial. */
    @Synchronized
    fun clearTrips() {
        _trips.value = emptyList()
        writeTrips(emptyList())
    }

    private fun readTrips(): List<Trip> {
        if (!file.exists()) return emptyList()
        return try {
            val array = JSONArray(file.readText())
            List(array.length()) { index ->
                val item = array.getJSONObject(index)
                Trip(
                    startMillis = item.getLong("start"),
                    endMillis = item.getLong("end"),
                )
            }
        } catch (e: Exception) {
            // Archivo dañado: mejor empezar de cero que dejar la app sin abrir.
            emptyList()
        }
    }

    private fun writeTrips(trips: List<Trip>) {
        val array = JSONArray()
        for (trip in trips) {
            array.put(
                JSONObject()
                    .put("start", trip.startMillis)
                    .put("end", trip.endMillis)
            )
        }
        file.writeText(array.toString())
    }
}
