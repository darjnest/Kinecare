package com.darjnest.kinecare.feature.payment.domain

import com.darjnest.kinecare.core.common.result.Error

/**
 * Errores de [com.darjnest.kinecare.feature.payment.data.repository.PagoRepository.iniciar].
 * Salvo [SIN_INTERNET] y [DESCONOCIDO], cada valor corresponde 1:1 a un
 * `details.motivo` de la Cloud Function `iniciarPago` (docs/DATA_MODEL.md).
 */
enum class IniciarPagoError : Error {
    SIN_SESION,
    SIN_INTERNET,

    /** El usuario autenticado no es Cliente. */
    ROL_INVALIDO,
    DATOS_INVALIDOS,

    /** La reserva no existe o es de otro cliente. */
    RESERVA_NO_ENCONTRADA,

    /** La reserva no esta `CONFIRMADA` (el profesional aun no acepta, o ya no corresponde pagar). */
    RESERVA_NO_PAGABLE,

    /** La reserva ya esta pagada (o reembolsada). */
    PAGO_YA_REALIZADO,

    /** El profesional aun no vincula su cuenta de Mercado Pago (o debe reautorizar). */
    PROFESIONAL_SIN_CUENTA_MP,

    /** El monto de la reserva no se puede cobrar. */
    MONTO_INVALIDO,

    /** Mercado Pago no respondio. */
    PASARELA_NO_DISPONIBLE,
    DESCONOCIDO,
}
