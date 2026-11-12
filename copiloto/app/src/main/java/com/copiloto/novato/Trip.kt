package com.copiloto.novato

/** Un viaje terminado: cuándo empezó y cuándo acabó (en milisegundos desde 1970). */
data class Trip(
    val startMillis: Long,
    val endMillis: Long,
) {
    val durationMillis: Long
        get() = endMillis - startMillis
}
