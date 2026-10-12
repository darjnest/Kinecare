package com.darjnest.kinecare.core.common.data.repository

import com.darjnest.kinecare.core.common.data.error.CompletarReservaError
import com.darjnest.kinecare.core.common.data.error.CrearReservaError
import com.darjnest.kinecare.core.common.data.error.ReservaError
import com.darjnest.kinecare.core.common.data.error.ResponderReservaError
import com.darjnest.kinecare.core.common.domain.model.EstadoReserva
import com.darjnest.kinecare.core.common.domain.model.Reserva
import com.darjnest.kinecare.core.common.domain.model.RespuestaReserva
import com.darjnest.kinecare.core.common.domain.model.SolicitudReserva
import com.darjnest.kinecare.core.common.result.Result

/**
 * Acceso a `reservas/{reservaId}` (ver docs/DATA_MODEL.md). La lectura va
 * directo a Firestore; la escritura **nunca**: [crear] y [responder] llaman
 * a las Cloud Functions `crearReserva`, `responderReserva` y `completarReserva`
 * (firestore.rules deniega `write` desde el cliente).
 */
interface ReservaRepository {
    /** Reservas de un cliente ordenadas por `fechaHora` descendente. */
    suspend fun obtenerPorCliente(clienteId: String): Result<List<Reserva>, ReservaError>

    /**
     * Reservas de un profesional ordenadas por `fechaHora` ascendente (la mas
     * proxima primero). Usa el indice `profesionalId` + `fechaHora` (ASC).
     */
    suspend fun obtenerPorProfesional(profesionalId: String): Result<List<Reserva>, ReservaError>

    /** Crea la reserva via `crearReserva` y retorna el id del documento creado. */
    suspend fun crear(solicitud: SolicitudReserva): Result<String, CrearReservaError>

    /**
     * El profesional autenticado acepta o rechaza una reserva en
     * `SOLICITADA` via `responderReserva`. Retorna el estado en que quedo
     * (`CONFIRMADA` o `RECHAZADA`).
     */
    suspend fun responder(
        reservaId: String,
        respuesta: RespuestaReserva,
    ): Result<EstadoReserva, ResponderReservaError>

    /**
     * El profesional autenticado da por realizada una atencion `CONFIRMADA`
     * cuya hora ya llego, via `completarReserva`. Habilita la resena y el
     * "por liquidar". Retorna el estado en que quedo (`COMPLETADA`).
     */
    suspend fun completar(reservaId: String): Result<EstadoReserva, CompletarReservaError>
}
