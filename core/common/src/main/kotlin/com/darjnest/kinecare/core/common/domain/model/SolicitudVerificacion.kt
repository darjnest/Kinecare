package com.darjnest.kinecare.core.common.domain.model

import kotlinx.datetime.Instant

data class SolicitudVerificacion(
    val id: String,
    val profesionalId: String,
    val tipo: TipoInsignia,
    val estado: EstadoVerificacion,
    val proveedorExterno: String?,
    val fechaSolicitud: Instant,
    val fechaResolucion: Instant?,
)
