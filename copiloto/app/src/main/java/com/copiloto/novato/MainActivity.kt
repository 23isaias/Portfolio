package com.copiloto.novato

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min

/** La única pantalla de la app en el móvil. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(
                colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme(),
            ) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    HomeScreen()
                }
            }
        }
    }
}

@Composable
fun HomeScreen() {
    // Estos valores se actualizan solos cuando cambian los datos.
    val trips by TripStore.trips.collectAsStateWithLifecycle()
    val activeStart by TripStore.activeTripStart.collectAsStateWithLifecycle()
    val activeDistance by TripStore.activeDistanceMeters.collectAsStateWithLifecycle()
    val watching by CarWatcherService.running.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("Copiloto", style = MaterialTheme.typography.headlineMedium)
        }
        item { DetectionCard(watching) }
        item { ActiveTripCard(activeStart, activeDistance, watching) }
        item { CarSettingsCard() }
        item { HistoryHeader(trips) }
        // reversed(): el viaje más reciente arriba.
        items(trips.reversed(), key = { it.startMillis }) { trip ->
            TripRow(trip)
        }
    }
}

/** Interruptor de la detección automática. */
@Composable
private fun DetectionCard(watching: Boolean) {
    val context = LocalContext.current

    // Pide los permisos y después arranca el servicio. Si deniegas alguno la
    // app funciona igual: sin ubicación no hay distancia, y sin notificaciones
    // no se ve el aviso del servicio.
    val permissions = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        CarWatcherService.start(context)
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Detección automática", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (watching) "Vigilando la conexión con Android Auto"
                    else "Actívala para grabar tus viajes al conectar el coche",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Switch(
                checked = watching,
                onCheckedChange = { enable ->
                    if (enable) {
                        val wanted = mutableListOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION,
                        )
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            wanted.add(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        permissions.launch(wanted.toTypedArray())
                    } else {
                        CarWatcherService.stop(context)
                    }
                },
            )
        }
    }
}

/** Viaje en curso, o el botón para simular uno. */
@Composable
private fun ActiveTripCard(activeStart: Long?, activeDistance: Double, watching: Boolean) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (activeStart != null) {
                val now = rememberCurrentTime()
                Text("Viaje en curso", style = MaterialTheme.typography.titleMedium)
                Text(
                    formatDuration(now - activeStart),
                    style = MaterialTheme.typography.displaySmall,
                )
                Text(
                    if (watching) {
                        "${formatDistance(activeDistance)} · ${formatCost(CarSettings.costFor(activeDistance))}"
                    } else {
                        "Activa la detección automática para medir la distancia con GPS"
                    },
                    style = MaterialTheme.typography.bodyLarge,
                )
                Button(onClick = { TripStore.endTrip() }) {
                    Text("Terminar viaje")
                }
            } else {
                Text("Sin viaje en curso", style = MaterialTheme.typography.titleMedium)
                Text(
                    "¿Aún no tienes el coche? Simula un viaje para probar la app.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedButton(onClick = { TripStore.startTrip(byCar = false) }) {
                    Text("Simular viaje")
                }
            }
        }
    }
}

/** Precio del combustible y consumo del coche, para estimar el coste. */
@Composable
private fun CarSettingsCard() {
    val fuelPrice by CarSettings.fuelPricePerLiter.collectAsStateWithLifecycle()
    val consumption by CarSettings.litersPer100Km.collectAsStateWithLifecycle()
    var expanded by rememberSaveable { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Mi coche", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${formatCost(fuelPrice.toDouble())} por litro · $consumption L/100 km",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                TextButton(onClick = { expanded = !expanded }) {
                    Text(if (expanded) "Cerrar" else "Editar")
                }
            }
            if (expanded) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    NumberField(
                        label = "Precio por litro (B/.)",
                        value = fuelPrice,
                        onValue = CarSettings::setFuelPricePerLiter,
                        modifier = Modifier.weight(1f),
                    )
                    NumberField(
                        label = "Consumo (L/100 km)",
                        value = consumption,
                        onValue = CarSettings::setLitersPer100Km,
                        modifier = Modifier.weight(1f),
                    )
                }
                Text(
                    "El coste de cada viaje se calcula al terminarlo, con los valores de ese momento.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

/** Campo para escribir un número con decimales (acepta coma o punto). */
@Composable
private fun NumberField(
    label: String,
    value: Float,
    onValue: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    var text by remember { mutableStateOf(value.toString()) }
    OutlinedTextField(
        value = text,
        onValueChange = { typed ->
            text = typed
            val number = typed.replace(',', '.').toFloatOrNull()
            if (number != null && number > 0f) onValue(number)
        },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier,
    )
}

@Composable
private fun HistoryHeader(trips: List<Trip>) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Historial", style = MaterialTheme.typography.titleLarge)
            Text(
                "${trips.size} viajes · " +
                    "${formatDuration(trips.sumOf { it.durationMillis })} · " +
                    "${formatDistance(trips.sumOf { it.distanceMeters })} · " +
                    formatCost(trips.sumOf { it.cost }),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (trips.isNotEmpty()) {
            TextButton(onClick = { TripStore.clearTrips() }) {
                Text("Borrar")
            }
        }
    }
}

/** Una fila del historial. Al tocarla se despliega el dibujo de la ruta. */
@Composable
private fun TripRow(trip: Trip) {
    var expanded by rememberSaveable { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(formatDate(trip.startMillis), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        if (trip.hasDistance) {
                            "${formatDistance(trip.distanceMeters)} · ${formatCost(trip.cost)}"
                        } else {
                            "Sin datos de GPS"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Text(
                    formatDuration(trip.durationMillis),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            if (expanded) {
                if (trip.route.size >= 2) {
                    RouteSketch(
                        route = trip.route,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                    )
                    Text(
                        "Verde: salida · Rojo: llegada",
                        style = MaterialTheme.typography.bodySmall,
                    )
                } else {
                    Text(
                        "Este viaje no tiene ruta guardada.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

/**
 * Dibuja la forma del recorrido (sin mapa de fondo): convierte las coordenadas
 * GPS a posiciones dentro del recuadro y las une con una línea.
 */
@Composable
private fun RouteSketch(route: List<RoutePoint>, modifier: Modifier = Modifier) {
    val lineColor = MaterialTheme.colorScheme.primary

    Canvas(modifier = modifier) {
        if (route.size < 2) return@Canvas

        val minLat = route.minOf { it.latitude }
        val maxLat = route.maxOf { it.latitude }
        val minLon = route.minOf { it.longitude }
        val maxLon = route.maxOf { it.longitude }

        // Un grado de longitud mide menos que uno de latitud lejos del ecuador;
        // este factor evita que el dibujo salga estirado.
        val lonFactor = cos(Math.toRadians((minLat + maxLat) / 2))
        val routeWidth = max((maxLon - minLon) * lonFactor, 1e-9)
        val routeHeight = max(maxLat - minLat, 1e-9)

        val margin = 12.dp.toPx()
        val scale = min(
            (size.width - 2 * margin) / routeWidth,
            (size.height - 2 * margin) / routeHeight,
        )
        // Centrar el dibujo en el recuadro.
        val offsetX = (size.width - routeWidth * scale) / 2
        val offsetY = (size.height - routeHeight * scale) / 2

        fun toScreen(point: RoutePoint) = Offset(
            x = (offsetX + (point.longitude - minLon) * lonFactor * scale).toFloat(),
            y = (offsetY + (maxLat - point.latitude) * scale).toFloat(),
        )

        val path = Path()
        route.forEachIndexed { index, point ->
            val position = toScreen(point)
            if (index == 0) path.moveTo(position.x, position.y) else path.lineTo(position.x, position.y)
        }
        drawPath(
            path = path,
            color = lineColor,
            style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
        drawCircle(color = Color(0xFF2E7D32), radius = 6.dp.toPx(), center = toScreen(route.first()))
        drawCircle(color = Color(0xFFC62828), radius = 6.dp.toPx(), center = toScreen(route.last()))
    }
}

/** Devuelve la hora actual y la refresca cada segundo (para el cronómetro). */
@Composable
private fun rememberCurrentTime(): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    return now
}

private val dateFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEE d MMM · HH:mm", Locale("es"))

private fun formatDate(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(dateFormatter)

/** 3725000 ms -> "1 h 2 min"; 65000 ms -> "1 min 5 s". */
private fun formatDuration(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return when {
        hours > 0 -> "$hours h $minutes min"
        minutes > 0 -> "$minutes min $seconds s"
        else -> "$seconds s"
    }
}

/** 850.0 -> "850 m"; 12345.0 -> "12.3 km". */
private fun formatDistance(meters: Double): String =
    if (meters < 1000) {
        "${meters.toInt()} m"
    } else {
        String.format(Locale.US, "%.1f km", meters / 1000)
    }

/** 1.5 -> "B/. 1.50". */
private fun formatCost(balboas: Double): String =
    String.format(Locale.US, "B/. %.2f", balboas)
