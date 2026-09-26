package com.darjnest.kinecare.core.common.data.repository

import com.darjnest.kinecare.core.common.data.error.ReservaError
import com.darjnest.kinecare.core.common.domain.model.Reserva
import com.darjnest.kinecare.core.common.result.Result

/**
 * Acceso de lectura a `reservas/{reservaId}` (ver docs/DATA_MODEL.md). Sin
 * escritura: `crearReserva`/`iniciarPago` (Cloud Functions, Fase 4/5) son el
 * unico camino de escritura (firestore.rules ya lo deniega desde el
 * cliente).
 */
interface ReservaRepository {
    /** Reservas de un cliente ordenadas por `fechaHora` descendente. */
    suspend fun obtenerPorCliente(clienteId: String): Result<List<Reserva>, ReservaError>
}
