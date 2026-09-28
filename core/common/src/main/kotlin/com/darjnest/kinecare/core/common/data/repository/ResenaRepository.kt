package com.darjnest.kinecare.core.common.data.repository

import com.darjnest.kinecare.core.common.data.error.ResenaError
import com.darjnest.kinecare.core.common.domain.model.Resena
import com.darjnest.kinecare.core.common.result.Result

/**
 * Acceso a `resenas/{resenaId}` (ver docs/DATA_MODEL.md). El id del documento
 * es siempre el `reservaId` de la reserva reseñada: una reserva admite a lo
 * mas una resena.
 */
interface ResenaRepository {
    /**
     * Resenas de un profesional ordenadas por `fecha` descendente, hasta 100.
     * Usa el indice compuesto `profesionalId` (ASC) + `fecha` (DESC).
     */
    suspend fun obtenerPorProfesional(profesionalId: String): Result<List<Resena>, ResenaError>

    /** Resena de una reserva (lectura directa de `resenas/{reservaId}`), o `null` si aun no existe. */
    suspend fun obtenerPorReserva(reservaId: String): Result<Resena?, ResenaError>

    /**
     * Crea la resena en `resenas/{resena.reservaId}` (id determinista). Escribir
     * una segunda resena de la misma reserva seria un `update` sobre un
     * documento existente, que firestore.rules deniega al cliente — asi se
     * evitan duplicados sin Cloud Functions. `fecha` se escribe como server
     * timestamp (se ignora `resena.fecha`) y `respuestaProfesional` no se
     * escribe al crear.
     *
     * [ResenaError.SIN_PERMISO] (PERMISSION_DENIED) significa que la reserva no
     * esta `COMPLETADA`, no pertenece al llamador, la resena ya existe o los
     * datos no cumplen las reglas de validacion.
     */
    suspend fun crear(resena: Resena): Result<Unit, ResenaError>
}
