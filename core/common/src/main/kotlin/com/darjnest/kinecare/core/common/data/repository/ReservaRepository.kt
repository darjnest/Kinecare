package com.darjnest.kinecare.core.common.data.repository

import com.darjnest.kinecare.core.common.data.error.CancelarReservaError
import com.darjnest.kinecare.core.common.data.error.CompletarReservaError
import com.darjnest.kinecare.core.common.data.error.CrearReservaError
import com.darjnest.kinecare.core.common.data.error.CuposOcupadosError
import com.darjnest.kinecare.core.common.data.error.ReservaError
import com.darjnest.kinecare.core.common.data.error.ResponderReservaError
import com.darjnest.kinecare.core.common.domain.model.CupoOcupado
import com.darjnest.kinecare.core.common.domain.model.EstadoReserva
import com.darjnest.kinecare.core.common.domain.model.Reserva
import com.darjnest.kinecare.core.common.domain.model.ResultadoCancelacion
import com.darjnest.kinecare.core.common.domain.model.RespuestaReserva
import com.darjnest.kinecare.core.common.domain.model.SolicitudReserva
import com.darjnest.kinecare.core.common.result.Result
import kotlinx.datetime.Instant

/**
 * Acceso a `reservas/{reservaId}` (ver docs/DATA_MODEL.md). La lectura va
 * directo a Firestore; la escritura **nunca**: [crear] y [responder] llaman
 * a las Cloud Functions `crearReserva`, `responderReserva`, `completarReserva` y
 * `cancelarReserva`
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

    /**
     * El cliente o el profesional de la reserva la cancela (`SOLICITADA` o
     * `CONFIRMADA`, antes de la hora de la cita) via `cancelarReserva`.
     * [motivo] es opcional. Retorna el estado en que quedo
     * (`CANCELADA_CLIENTE` o `CANCELADA_PROFESIONAL`, segun quien llama) y que
     * pasa con el pago (`reembolso`).
     * Reembolso: si cancela el profesional, total; si cancela el cliente con
     * 24 horas o mas de anticipacion, total; con menos, sin reembolso
     * automatico (lo decide y aplica el backend).
     */
    suspend fun cancelar(reservaId: String, motivo: String? = null): Result<ResultadoCancelacion, CancelarReservaError>

    /**
     * Tramos ya ocupados de la agenda del profesional entre [desde] y [hasta]
     * (ventana de 15 dias como maximo) via `obtenerCuposOcupados`. Sirve para
     * no ofrecer al cliente horarios ya tomados, que no puede ver leyendo
     * `reservas` directamente.
     */
    suspend fun obtenerCuposOcupados(
        profesionalId: String,
        desde: Instant,
        hasta: Instant,
    ): Result<List<CupoOcupado>, CuposOcupadosError>
}
