package com.darjnest.kinecare.core.common.data.repository

import com.darjnest.kinecare.core.common.data.error.ServicioError
import com.darjnest.kinecare.core.common.domain.model.Servicio
import com.darjnest.kinecare.core.common.result.Result

/**
 * Catalogo propio del profesional: subcoleccion
 * `profesionales/{id}/servicios` (ver docs/DATA_MODEL.md). A diferencia de
 * `ProfesionalRepository.obtenerPorId` (vista publica de solo lectura),
 * este repositorio incluye los servicios pausados y permite modificarlos
 * (la Security Rule solo lo permite al dueno).
 */
interface ServicioRepository {
    suspend fun obtenerPorProfesional(profesionalId: String): Result<List<Servicio>, ServicioError>

    suspend fun actualizarActivo(
        profesionalId: String,
        servicioId: String,
        activo: Boolean,
    ): Result<Unit, ServicioError>
}
