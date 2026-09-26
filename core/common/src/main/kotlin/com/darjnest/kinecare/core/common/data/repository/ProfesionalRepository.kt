package com.darjnest.kinecare.core.common.data.repository

import com.darjnest.kinecare.core.common.data.error.ProfesionalError
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
}
