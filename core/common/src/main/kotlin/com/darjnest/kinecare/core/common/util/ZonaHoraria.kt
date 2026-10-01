package com.darjnest.kinecare.core.common.util

import kotlinx.datetime.TimeZone

/**
 * Zona en la que se expresa `profesionales.disponibilidad` (los turnos
 * "09:00-13:00" son hora de Chile, no del dispositivo). La Cloud Function
 * `crearReserva` valida contra la misma zona.
 */
val ZonaHorariaChile: TimeZone by lazy { TimeZone.of("America/Santiago") }
