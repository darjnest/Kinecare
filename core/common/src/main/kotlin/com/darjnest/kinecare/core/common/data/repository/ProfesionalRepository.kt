package com.darjnest.kinecare.core.common.data.repository

import com.darjnest.kinecare.core.common.data.error.ProfesionalError
import com.darjnest.kinecare.core.common.domain.model.Disponibilidad
import com.darjnest.kinecare.core.common.domain.model.Profesional
import com.darjnest.kinecare.core.common.result.Result

/**
 * Acceso a `profesionales/{usuarioId}` (ver docs/DATA_MODEL.md). Movido
 * desde `:feature:search` a `:core:common` + `:core:network`:
 * `:feature:client-panel` tambien necesita resolver un profesional por id
 * (para la pantalla de Favoritos) y las features nunca se dependen entre si
 * (docs/ARCHITECTURE.md).
 */
interface ProfesionalRepository {
    suspend fun buscarPorEspecialidad(especialidad: String): Result<List<Profesional>, ProfesionalError>

    suspend fun obtenerPorId(id: String): Result<Profesional, ProfesionalError>

    /**
     * Reemplaza el arreglo `disponibilidad` completo de `profesionales/{id}`.
     * Solo lo puede escribir el dueno; la Security Rule exige ademas que
     * `insignias` y `estadoVerificacionGeneral` no cambien, cosa que esta
     * operacion nunca toca.
     */
    suspend fun actualizarDisponibilidad(
        id: String,
        disponibilidad: List<Disponibilidad>,
    ): Result<Unit, ProfesionalError>

    /** Actualiza la biografia (`descripcion`) de `profesionales/{id}`; solo el dueno puede escribirla. */
    suspend fun actualizarDescripcion(id: String, descripcion: String): Result<Unit, ProfesionalError>
}
