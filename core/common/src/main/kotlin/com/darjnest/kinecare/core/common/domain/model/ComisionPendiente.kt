package com.darjnest.kinecare.core.common.domain.model

import kotlinx.datetime.Instant

/**
 * Comision de KineCare que un profesional adeuda por una atencion que no se
 * pago por la app (efectivo): el dinero no paso por Mercado Pago, asi que no
 * se pudo cobrar como `marketplace_fee`. La crea el backend al completar la
 * atencion (`comisionesPendientes/{reservaId}`, docs/DATA_MODEL.md).
 */
data class ComisionPendiente(
    val reservaId: String,
    /** CLP enteros. */
    val monto: Long,
    val creadoEn: Instant?,
)

/** Lo que un profesional adeuda: la suma de sus [ComisionPendiente] aun sin pagar. */
data class SaldoComision(
    val pendientes: List<ComisionPendiente>,
) {
    val total: Long get() = pendientes.sumOf { it.monto }
    val hayDeuda: Boolean get() = total > 0
}
