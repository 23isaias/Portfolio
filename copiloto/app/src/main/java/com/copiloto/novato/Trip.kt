package com.copiloto.novato

/** Un punto de la ruta (coordenadas GPS). */
data class RoutePoint(
    val latitude: Double,
    val longitude: Double,
)

/**
 * Un viaje terminado.
 *
 * - [startMillis] / [endMillis]: inicio y fin, en milisegundos desde 1970.
 * - [distanceMeters]: metros recorridos según el GPS (0 si no había GPS).
 * - [cost]: coste estimado de combustible en balboas, calculado al terminar.
 * - [route]: puntos del recorrido, para dibujarlo.
 */
data class Trip(
    val startMillis: Long,
    val endMillis: Long,
    val distanceMeters: Double = 0.0,
    val cost: Double = 0.0,
    val route: List<RoutePoint> = emptyList(),
) {
    val durationMillis: Long
        get() = endMillis - startMillis

    val hasDistance: Boolean
        get() = distanceMeters > 0.0
}
