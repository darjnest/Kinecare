package com.darjnest.kinecare.core.common.domain.model

enum class TipoMetodoPago {
    TARJETA,
    TRANSFERENCIA,
}

data class MetodoPago(
    val tipo: TipoMetodoPago,
    val ultimosDigitos: String?,
    val tokenPasarela: String,
)
