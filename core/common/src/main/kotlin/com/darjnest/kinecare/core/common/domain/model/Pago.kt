package com.darjnest.kinecare.core.common.domain.model

enum class EstadoPago {
    PENDIENTE,
    AUTORIZADO,
    RECHAZADO,
    REEMBOLSADO,
}

data class Pago(
    val id: String,
    val reservaId: String,
    val monto: Long,
    val metodo: MetodoPago,
    val estado: EstadoPago,
    val idTransaccionPasarela: String?,
)
