package com.darjnest.kinecare.core.common.domain.model

/**
 * Lo unico que la app muestra de otra persona (`perfilesPublicos/{id}`, ver docs/DATA_MODEL.md):
 * el profesional de una cita, el paciente de una solicitud, el autor de una resena. Los datos
 * privados ([Usuario.rut], correo, telefono) solo los lee su dueno.
 */
data class PerfilPublico(
    val id: String,
    val nombre: String,
    val fotoUrl: String?,
)
