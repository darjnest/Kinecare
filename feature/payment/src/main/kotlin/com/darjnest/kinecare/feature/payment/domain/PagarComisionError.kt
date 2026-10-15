package com.darjnest.kinecare.feature.payment.domain

import com.darjnest.kinecare.core.common.result.Error

/**
 * Errores de [com.darjnest.kinecare.feature.payment.data.repository.PagoRepository.pagarComisiones].
 * Salvo [SIN_INTERNET] y [DESCONOCIDO], 1:1 con los `details.motivo` de `pagarSaldoComision`.
 */
enum class PagarComisionError : Error {
    SIN_SESION,
    SIN_INTERNET,

    /** El usuario autenticado no es Profesional. */
    ROL_INVALIDO,

    /** No hay comisiones libres que cobrar (ya pagadas, o reservadas por un pago en curso). */
    SIN_SALDO_PENDIENTE,
    PASARELA_NO_DISPONIBLE,
    DESCONOCIDO,
}
