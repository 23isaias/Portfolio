package com.copiloto.novato

import android.app.Application

/** Lo primero que se ejecuta al abrir la app: prepara los ajustes y el cuaderno de viajes. */
class CopilotoApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CarSettings.init(this)
        TripStore.init(this)
    }
}
