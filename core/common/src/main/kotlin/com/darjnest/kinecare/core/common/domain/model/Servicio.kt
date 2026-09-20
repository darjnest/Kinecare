package com.darjnest.kinecare.core.common.domain.model

enum class ModalidadServicio {
    DOMICILIO,
    CONSULTA,
    ONLINE,
}

data class Servicio(
    val id: String,
    val nombre: String,
    val descripcion: String,
    val modalidad: ModalidadServicio,
    val duracionMinutos: Int,
    val precio: Long,
)
