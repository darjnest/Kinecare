package com.darjnest.kinecare.core.common.domain.model

import kotlinx.datetime.Instant

/**
 * Lo que el Cliente envia a la Cloud Function `crearReserva`. No lleva
 * modalidad, precio, estado ni comision: la funcion los deriva del servicio
 * en el backend (docs/ARCHITECTURE.md#seguridad).
 */
data class SolicitudReserva(
    val profesionalId: String,
    val servicioId: String,
    val fechaHora: Instant,
    /** Solo para servicios `DOMICILIO`; nulo en el resto. */
    val direccion: Direccion?,
)
