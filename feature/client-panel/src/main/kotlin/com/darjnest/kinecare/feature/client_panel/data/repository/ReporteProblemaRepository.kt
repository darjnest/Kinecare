package com.darjnest.kinecare.feature.client_panel.data.repository

import com.darjnest.kinecare.core.common.result.Result
import com.darjnest.kinecare.feature.client_panel.domain.ReporteProblema
import com.darjnest.kinecare.feature.client_panel.domain.ReporteProblemaError

/**
 * Acceso a `reportesProblema/{reservaId}` (ver docs/DATA_MODEL.md). Lo usa
 * solo esta feature, asi que la interfaz y su implementacion viven aqui
 * (mismo criterio que `AuthRepository` de `:feature:auth`).
 */
interface ReporteProblemaRepository {
    /**
     * Reportes del cliente, sin orden (consulta de igualdad sobre
     * `clienteId`, no necesita indice compuesto). Sirve para marcar en
     * "Mis Citas" que reservas ya tienen reporte.
     */
    suspend fun obtenerPorCliente(clienteId: String): Result<List<ReporteProblema>, ReporteProblemaError>

    /** Reporte de una reserva (lectura directa por id), o `null` si aun no existe. */
    suspend fun obtenerPorReserva(reservaId: String): Result<ReporteProblema?, ReporteProblemaError>

    /**
     * Crea el reporte en `reportesProblema/{reporte.reservaId}` con `estado`
     * `ABIERTO` y `fecha` como server timestamp (se ignoran `reporte.estado`
     * y `reporte.fecha`). Un segundo reporte de la misma reserva seria un
     * `update`, que las reglas deniegan.
     *
     * [ReporteProblemaError.SIN_PERMISO] (PERMISSION_DENIED) significa que la
     * reserva no es del llamador, ya tiene reporte o los datos no cumplen las
     * reglas de validacion.
     */
    suspend fun crear(reporte: ReporteProblema): Result<Unit, ReporteProblemaError>
}
