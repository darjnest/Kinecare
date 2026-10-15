package com.darjnest.kinecare.core.common.data.repository

import com.darjnest.kinecare.core.common.data.error.ComisionError
import com.darjnest.kinecare.core.common.domain.model.SaldoComision
import com.darjnest.kinecare.core.common.result.Result

/**
 * Lectura de `comisionesPendientes` (docs/DATA_MODEL.md): las comisiones que
 * un profesional adeuda por atenciones pagadas en efectivo. Solo lectura: las
 * escribe el backend (al completar la atencion y al cobrarlas), nunca la app.
 * Pagarlas es una Cloud Function (`pagarSaldoComision`, en `:feature:payment`).
 */
interface ComisionRepository {
    /** Comisiones `PENDIENTE` del profesional, de la mas reciente a la mas antigua. */
    suspend fun obtenerSaldo(profesionalId: String): Result<SaldoComision, ComisionError>
}
