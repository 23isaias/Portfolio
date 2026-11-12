package com.copiloto.novato

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import androidx.car.app.connection.CarConnection
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Servicio que se queda vigilando en segundo plano. Cuando el móvil se conecta
 * a Android Auto empieza un viaje, y cuando se desconecta lo termina.
 *
 * Android exige que un servicio así muestre una notificación fija mientras
 * funciona ("servicio en primer plano").
 */
class CarWatcherService : LifecycleService() {

    override fun onCreate() {
        super.onCreate()
        TripStore.init(this)

        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
        )
        _running.value = true

        // CarConnection avisa cada vez que cambia la conexión con el coche.
        CarConnection(this).type.observe(this) { connectionType ->
            if (connectionType == CarConnection.CONNECTION_TYPE_PROJECTION) {
                TripStore.startTrip(byCar = true)
            } else {
                TripStore.endTrip(onlyIfStartedByCar = true)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        // Si Android cierra el servicio por falta de memoria, que lo vuelva a abrir.
        return START_STICKY
    }

    override fun onDestroy() {
        _running.value = false
        super.onDestroy()
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
