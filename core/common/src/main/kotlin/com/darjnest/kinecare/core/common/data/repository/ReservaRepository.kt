package com.darjnest.kinecare.core.common.data.repository

import com.darjnest.kinecare.core.common.data.error.CrearReservaError
import com.darjnest.kinecare.core.common.data.error.ReservaError
import com.darjnest.kinecare.core.common.domain.model.Reserva
import com.darjnest.kinecare.core.common.domain.model.SolicitudReserva
import com.darjnest.kinecare.core.common.result.Result

/**
 * Acceso a `reservas/{reservaId}` (ver docs/DATA_MODEL.md). La lectura va
 * directo a Firestore; la escritura **nunca**: [crear] llama a la Cloud
 * Function `crearReserva` (firestore.rules deniega `write` desde el
 * cliente).
 */
interface ReservaRepository {
    /** Reservas de un cliente ordenadas por `fechaHora` descendente. */
    suspend fun obtenerPorCliente(clienteId: String): Result<List<Reserva>, ReservaError>

    /** Crea la reserva via `crearReserva` y retorna el id del documento creado. */
    suspend fun crear(solicitud: SolicitudReserva): Result<String, CrearReservaError>
}
