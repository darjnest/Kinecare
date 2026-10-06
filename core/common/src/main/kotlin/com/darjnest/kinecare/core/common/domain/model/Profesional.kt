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
    /**
     * Disciplinas que ofrece (`profesionales/{id}.tiposAtencion`), por las que
     * filtra la busqueda. Distinto de [especialidades], que es texto libre.
     */
    val tiposAtencion: List<TipoAtencion> = emptyList(),
    /**
     * `true` si vinculo su cuenta de Mercado Pago y puede cobrar
     * (`profesionales/{id}.mercadoPagoConectado`, docs/DATA_MODEL.md). Lo
     * escribe solo el backend al terminar el OAuth; el cliente nunca lo cambia.
     */
    val mercadoPagoConectado: Boolean = false,
)
