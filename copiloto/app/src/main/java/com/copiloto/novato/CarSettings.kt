package com.copiloto.novato

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Los dos datos de tu coche que hacen falta para estimar cuánto cuesta un viaje:
 * el precio del combustible y lo que consume el coche.
 */
object CarSettings {

    private const val PREFS_NAME = "car_settings"
    private const val KEY_FUEL_PRICE = "fuel_price"
    private const val KEY_CONSUMPTION = "consumption"

    // Valores de partida; cámbialos en la app por los de tu coche.
    private const val DEFAULT_FUEL_PRICE = 0.90f   // balboas por litro
    private const val DEFAULT_CONSUMPTION = 7.0f   // litros cada 100 km

    private lateinit var prefs: SharedPreferences

    private val _fuelPricePerLiter = MutableStateFlow(DEFAULT_FUEL_PRICE)
    val fuelPricePerLiter: StateFlow<Float> = _fuelPricePerLiter.asStateFlow()

    private val _litersPer100Km = MutableStateFlow(DEFAULT_CONSUMPTION)
    val litersPer100Km: StateFlow<Float> = _litersPer100Km.asStateFlow()

    @Synchronized
    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _fuelPricePerLiter.value = prefs.getFloat(KEY_FUEL_PRICE, DEFAULT_FUEL_PRICE)
        _litersPer100Km.value = prefs.getFloat(KEY_CONSUMPTION, DEFAULT_CONSUMPTION)
    }

    fun setFuelPricePerLiter(value: Float) {
        _fuelPricePerLiter.value = value
        prefs.edit().putFloat(KEY_FUEL_PRICE, value).apply()
    }

    fun setLitersPer100Km(value: Float) {
        _litersPer100Km.value = value
        prefs.edit().putFloat(KEY_CONSUMPTION, value).apply()
    }

    /** Coste estimado: kilómetros x (litros por km) x precio del litro. */
    fun costFor(distanceMeters: Double): Double {
        val km = distanceMeters / 1000.0
        return km * (_litersPer100Km.value / 100.0) * _fuelPricePerLiter.value
    }
}
