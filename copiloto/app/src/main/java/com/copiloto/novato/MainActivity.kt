package com.copiloto.novato

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

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
    val context = LocalContext.current

    // Estos tres valores se actualizan solos cuando cambian los datos.
    val trips by TripStore.trips.collectAsStateWithLifecycle()
    val activeStart by TripStore.activeTripStart.collectAsStateWithLifecycle()
    val watching by CarWatcherService.running.collectAsStateWithLifecycle()

    // Pide permiso de notificaciones (Android 13+) y después arranca el servicio.
    // Si lo deniegas el servicio funciona igual, solo que sin notificación visible.
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        CarWatcherService.start(context)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.height(4.dp))
        Text("Copiloto", style = MaterialTheme.typography.headlineMedium)

        // Interruptor de la detección automática
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
                        if (!enable) {
                            CarWatcherService.stop(context)
                        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            CarWatcherService.start(context)
                        }
                    },
                )
            }
        }

        // Viaje en curso
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val start = activeStart
                if (start != null) {
                    val now = rememberCurrentTime()
                    Text("Viaje en curso", style = MaterialTheme.typography.titleMedium)
                    Text(
                        formatDuration(now - start),
                        style = MaterialTheme.typography.displaySmall,
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

        // Historial
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Historial", style = MaterialTheme.typography.titleLarge)
                Text(
                    "${trips.size} viajes · ${formatDuration(trips.sumOf { it.durationMillis })} al volante",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (trips.isNotEmpty()) {
                TextButton(onClick = { TripStore.clearTrips() }) {
                    Text("Borrar")
                }
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // reversed(): el viaje más reciente arriba.
            items(trips.reversed(), key = { it.startMillis }) { trip ->
                TripRow(trip)
            }
        }
    }
}

@Composable
private fun TripRow(trip: Trip) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                formatDate(trip.startMillis),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                formatDuration(trip.durationMillis),
                style = MaterialTheme.typography.titleMedium,
            )
        }
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
