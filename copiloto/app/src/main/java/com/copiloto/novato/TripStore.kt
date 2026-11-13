package com.copiloto.novato

import android.content.Context
import android.content.SharedPreferences
import android.location.Location
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
 * La pantalla observa [trips], [activeTripStart] y [activeDistanceMeters] y se
 * redibuja sola cuando cambian.
 */
object TripStore {

    private const val FILE_NAME = "trips.json"
    private const val PREFS_NAME = "copiloto"
    private const val KEY_ACTIVE_START = "active_start"
    private const val KEY_ACTIVE_BY_CAR = "active_by_car"

    /** Lecturas de GPS con más error que esto (en metros) se descartan. */
    private const val MAX_ACCURACY_METERS = 30f
    /** Movimientos más cortos que esto se consideran ruido del GPS. */
    private const val MIN_STEP_METERS = 3f
    /** Separación mínima entre puntos guardados de la ruta, para no llenar el archivo. */
    private const val ROUTE_POINT_SPACING_METERS = 25f

    private lateinit var file: File
    private lateinit var prefs: SharedPreferences

    private val _trips = MutableStateFlow<List<Trip>>(emptyList())
    /** Viajes terminados, del más antiguo al más reciente. */
    val trips: StateFlow<List<Trip>> = _trips.asStateFlow()

    private val _activeTripStart = MutableStateFlow<Long?>(null)
    /** Hora de inicio del viaje en curso, o null si no hay ninguno. */
    val activeTripStart: StateFlow<Long?> = _activeTripStart.asStateFlow()

    private val _activeDistanceMeters = MutableStateFlow(0.0)
    /** Metros recorridos en el viaje en curso. */
    val activeDistanceMeters: StateFlow<Double> = _activeDistanceMeters.asStateFlow()

    // Datos del viaje en curso. Viven solo en memoria.
    private var lastPoint: RoutePoint? = null
    private var lastSavedRoutePoint: RoutePoint? = null
    private val activeRoute = mutableListOf<RoutePoint>()

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
        resetActiveTripData()
        prefs.edit()
            .putLong(KEY_ACTIVE_START, now)
            .putBoolean(KEY_ACTIVE_BY_CAR, byCar)
            .apply()
        _activeTripStart.value = now
    }

    /**
     * Recibe una posición del GPS durante un viaje y va sumando la distancia.
     * [accuracyMeters] es el margen de error que declara el propio GPS.
     */
    @Synchronized
    fun onLocation(latitude: Double, longitude: Double, accuracyMeters: Float) {
        if (_activeTripStart.value == null) return
        if (accuracyMeters > MAX_ACCURACY_METERS) return

        val point = RoutePoint(latitude, longitude)
        val previous = lastPoint
        if (previous == null) {
            // Primera posición del viaje: es el punto de salida.
            lastPoint = point
            lastSavedRoutePoint = point
            activeRoute.add(point)
            return
        }

        val step = metersBetween(previous, point)
        if (step < MIN_STEP_METERS) return

        lastPoint = point
        _activeDistanceMeters.value += step

        val lastSaved = lastSavedRoutePoint
        if (lastSaved == null || metersBetween(lastSaved, point) >= ROUTE_POINT_SPACING_METERS) {
            lastSavedRoutePoint = point
            activeRoute.add(point)
        }
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

        // El último punto conocido es la llegada, aunque esté cerca del anterior.
        val arrival = lastPoint
        if (arrival != null && arrival != lastSavedRoutePoint) activeRoute.add(arrival)

        val distance = _activeDistanceMeters.value
        val trip = Trip(
            startMillis = start,
            endMillis = maxOf(now, start),
            distanceMeters = distance,
            cost = CarSettings.costFor(distance),
            route = activeRoute.toList(),
        )

        prefs.edit().remove(KEY_ACTIVE_START).remove(KEY_ACTIVE_BY_CAR).apply()
        _activeTripStart.value = null
        resetActiveTripData()

        val updated = _trips.value + trip
        _trips.value = updated
        writeTrips(updated)
    }

    /** Borra todo el historial. */
    @Synchronized
    fun clearTrips() {
        _trips.value = emptyList()
        writeTrips(emptyList())
    }

    private fun resetActiveTripData() {
        lastPoint = null
        lastSavedRoutePoint = null
        activeRoute.clear()
        _activeDistanceMeters.value = 0.0
    }

    private fun metersBetween(a: RoutePoint, b: RoutePoint): Float {
        val result = FloatArray(1)
        Location.distanceBetween(a.latitude, a.longitude, b.latitude, b.longitude, result)
        return result[0]
    }

    private fun readTrips(): List<Trip> {
        if (!file.exists()) return emptyList()
        return try {
            val array = JSONArray(file.readText())
            List(array.length()) { index -> tripFromJson(array.getJSONObject(index)) }
        } catch (e: Exception) {
            // Archivo dañado: mejor empezar de cero que dejar la app sin abrir.
            emptyList()
        }
    }

    private fun tripFromJson(item: JSONObject): Trip {
        // La ruta se guarda como una lista plana: [lat, lon, lat, lon, ...].
        // Los viajes de la etapa 1 no tienen distancia ni ruta: valen 0 y vacío.
        val coordinates = item.optJSONArray("route") ?: JSONArray()
        val route = List(coordinates.length() / 2) { i ->
            RoutePoint(
                latitude = coordinates.getDouble(i * 2),
                longitude = coordinates.getDouble(i * 2 + 1),
            )
        }
        return Trip(
            startMillis = item.getLong("start"),
            endMillis = item.getLong("end"),
            distanceMeters = item.optDouble("distance", 0.0),
            cost = item.optDouble("cost", 0.0),
            route = route,
        )
    }

    private fun writeTrips(trips: List<Trip>) {
        val array = JSONArray()
        for (trip in trips) {
            val coordinates = JSONArray()
            for (point in trip.route) {
                coordinates.put(point.latitude)
                coordinates.put(point.longitude)
            }
            array.put(
                JSONObject()
                    .put("start", trip.startMillis)
                    .put("end", trip.endMillis)
                    .put("distance", trip.distanceMeters)
                    .put("cost", trip.cost)
                    .put("route", coordinates)
            )
        }
        file.writeText(array.toString())
    }
}
