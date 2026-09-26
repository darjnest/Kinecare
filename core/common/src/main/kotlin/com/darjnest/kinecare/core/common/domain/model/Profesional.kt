package com.darjnest.kinecare.core.common.domain.model

data class Profesional(
    val usuario: Usuario,
    val especialidades: List<String>,
    val rnpi: String,
    val servicios: List<Servicio>,
    val insignias: List<Insignia>,
    val disponibilidad: List<Disponibilidad>,
    val calificacionPromedio: Double,
    val totalResenas: Int,
    val descripcion: String,
    val estadoVerificacionGeneral: EstadoVerificacion,
)
