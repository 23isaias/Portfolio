package com.copiloto.novato

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationManager
import android.os.Looper
import androidx.car.app.connection.CarConnection
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.location.LocationListenerCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Servicio que se queda vigilando en segundo plano. Cuando el móvil se conecta
 * a Android Auto empieza un viaje, y cuando se desconecta lo termina. Mientras
 * hay un viaje en curso escucha el GPS para medir la distancia y la ruta.
 *
 * Android exige que un servicio así muestre una notificación fija mientras
 * funciona ("servicio en primer plano").
 */
class CarWatcherService : LifecycleService() {

    private var listeningToGps = false

    private val locationListener = object : LocationListenerCompat {
        override fun onLocationChanged(location: Location) {
            TripStore.onLocation(location.latitude, location.longitude, location.accuracy)
        }
    }

    override fun onCreate() {
        super.onCreate()
        CarSettings.init(this)
        TripStore.init(this)

        startInForeground()
        _running.value = true

        // CarConnection avisa cada vez que cambia la conexión con el coche.
        CarConnection(this).type.observe(this) { connectionType ->
            if (connectionType == CarConnection.CONNECTION_TYPE_PROJECTION) {
                TripStore.startTrip(byCar = true)
            } else {
                TripStore.endTrip(onlyIfStartedByCar = true)
            }
        }

        // Encender el GPS solo mientras hay un viaje en curso (ahorra batería).
        lifecycleScope.launch {
            TripStore.activeTripStart.collect { tripStart ->
                if (tripStart != null) startGps() else stopGps()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        // Si Android cierra el servicio por falta de memoria, que lo vuelva a abrir.
        return START_STICKY
    }

    override fun onDestroy() {
        stopGps()
        _running.value = false
        super.onDestroy()
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Arranca como servicio en primer plano. Si hay permiso de ubicación lo
     * declara también como servicio de ubicación, que es lo que permite usar el
     * GPS con la pantalla apagada.
     */
    private fun startInForeground() {
        val notification = buildNotification()
        val connectedDevice = ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
        if (hasLocationPermission()) {
            try {
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    notification,
                    connectedDevice or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
                )
                return
            } catch (e: Exception) {
                // Android no deja arrancar un servicio de ubicación si la app no
                // está visible (pasa cuando el sistema lo reinicia por su cuenta).
                // Seguimos sin GPS: los viajes se graban, pero sin distancia.
            }
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, connectedDevice)
    }

    private fun startGps() {
        if (listeningToGps) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        try {
            val locationManager = getSystemService(LocationManager::class.java)
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                GPS_INTERVAL_MILLIS,
                GPS_MIN_DISTANCE_METERS,
                locationListener,
                Looper.getMainLooper(),
            )
            listeningToGps = true
        } catch (e: Exception) {
            // El dispositivo no tiene GPS o el sistema denegó el acceso.
        }
    }

    private fun stopGps() {
        if (!listeningToGps) return
        getSystemService(LocationManager::class.java).removeUpdates(locationListener)
        listeningToGps = false
    }

    private fun buildNotification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Detección de viajes",
                NotificationManager.IMPORTANCE_LOW,
            )
        )

        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_copiloto)
            .setContentTitle("Copiloto activo")
            .setContentText("Esperando a que conectes el móvil al coche")
            .setContentIntent(openApp)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "car_watcher"
        private const val NOTIFICATION_ID = 1

        /** Cada cuánto pedimos posición al GPS, y cuánto hay que moverse para recibirla. */
        private const val GPS_INTERVAL_MILLIS = 2000L
        private const val GPS_MIN_DISTANCE_METERS = 5f

        private val _running = MutableStateFlow(false)
        /** true mientras el servicio está vigilando. */
        val running: StateFlow<Boolean> = _running.asStateFlow()

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, CarWatcherService::class.java),
            )
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, CarWatcherService::class.java))
        }
    }
}
