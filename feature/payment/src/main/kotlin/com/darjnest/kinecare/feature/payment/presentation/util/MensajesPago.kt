package com.darjnest.kinecare.feature.payment.presentation.util

import com.darjnest.kinecare.core.common.data.error.ConectarMercadoPagoError
import com.darjnest.kinecare.core.common.data.error.EstadoPagoError
import com.darjnest.kinecare.core.common.data.error.IniciarPagoError

private const val MENSAJE_SIN_INTERNET = "Sin conexión a internet. Revisa tu red e inténtalo de nuevo."

fun mensajeErrorIniciar(error: IniciarPagoError): String = when (error) {
    IniciarPagoError.SIN_SESION -> "Tu sesión expiró. Inicia sesión de nuevo para pagar."
    IniciarPagoError.SIN_INTERNET -> MENSAJE_SIN_INTERNET
    IniciarPagoError.ROL_INVALIDO -> "Solo una cuenta de cliente puede pagar una reserva."
    IniciarPagoError.DATOS_INVALIDOS -> "No pudimos identificar la reserva. Vuelve a Mis Citas e inténtalo de nuevo."
    IniciarPagoError.RESERVA_NO_ENCONTRADA -> "No encontramos esta reserva. Puede que ya no exista."
    IniciarPagoError.RESERVA_NO_PAGABLE ->
        "Esta reserva todavía no se puede pagar. Debe estar confirmada por el profesional y vigente."
    IniciarPagoError.PAGO_YA_REALIZADO -> "Esta reserva ya está pagada. Revisa su estado en Mis Citas."
    IniciarPagoError.PROFESIONAL_SIN_CUENTA_MP ->
        "El profesional aún no habilitó los pagos con Mercado Pago. Intenta más tarde."
    IniciarPagoError.MONTO_INVALIDO -> "No pudimos calcular el cobro de esta reserva. Contacta a soporte de KineCare."
    IniciarPagoError.PASARELA_NO_DISPONIBLE ->
        "Mercado Pago no está disponible en este momento. Inténtalo de nuevo en unos minutos."
    IniciarPagoError.DESCONOCIDO -> "No pudimos iniciar el pago. Inténtalo de nuevo en unos minutos."
}

fun mensajeErrorEstado(error: EstadoPagoError): String = when (error) {
    EstadoPagoError.SIN_SESION -> "Tu sesión expiró. Inicia sesión de nuevo para ver el estado de tu pago."
    EstadoPagoError.SIN_INTERNET -> MENSAJE_SIN_INTERNET
    EstadoPagoError.DATOS_INVALIDOS -> "No pudimos identificar tu pago. Revisa su estado en Mis Citas."
    EstadoPagoError.PAGO_NO_ENCONTRADO -> "No encontramos este pago. Revisa el estado de tu reserva en Mis Citas."
    EstadoPagoError.PROFESIONAL_SIN_CUENTA_MP ->
        "No pudimos confirmar tu pago porque el profesional debe volver a vincular su cuenta de Mercado Pago. " +
            "Revisa Mis Citas más tarde."
    EstadoPagoError.PASARELA_NO_DISPONIBLE ->
        "Mercado Pago no está disponible en este momento. Inténtalo de nuevo en unos minutos."
    EstadoPagoError.DESCONOCIDO -> "No pudimos consultar el estado de tu pago. Inténtalo de nuevo."
}

/** `true` si volver a consultar tiene sentido: los demas errores no cambian reintentando. */
fun esReintentable(error: EstadoPagoError): Boolean = when (error) {
    EstadoPagoError.SIN_INTERNET,
    EstadoPagoError.PASARELA_NO_DISPONIBLE,
    EstadoPagoError.DESCONOCIDO,
    -> true
    EstadoPagoError.SIN_SESION,
    EstadoPagoError.DATOS_INVALIDOS,
    EstadoPagoError.PAGO_NO_ENCONTRADO,
    EstadoPagoError.PROFESIONAL_SIN_CUENTA_MP,
    -> false
}

fun mensajeErrorConexion(error: ConectarMercadoPagoError): String = when (error) {
    ConectarMercadoPagoError.SIN_SESION -> "Tu sesión expiró. Inicia sesión de nuevo para vincular tu cuenta."
    ConectarMercadoPagoError.SIN_INTERNET -> MENSAJE_SIN_INTERNET
    ConectarMercadoPagoError.ROL_INVALIDO -> "Solo una cuenta de profesional puede vincular Mercado Pago."
    ConectarMercadoPagoError.DESCONOCIDO -> "No pudimos iniciar la vinculación. Inténtalo de nuevo en unos minutos."
}

/**
 * Mensaje de la vuelta del OAuth con error. [motivo] viene de un deep link, asi
 * que cualquier valor desconocido (o ausente) cae en un mensaje generico de
 * error: nunca se interpreta como exito.
 */
fun mensajeMotivoVinculacion(motivo: String?): String = when (motivo) {
    "cancelado" -> "Cancelaste la vinculación en Mercado Pago. Puedes volver a intentarlo cuando quieras."
    "estado_invalido" -> "El enlace de vinculación venció o ya se usó. Vuelve a iniciar la vinculación."
    "sin_codigo" -> "Mercado Pago no nos entregó la autorización. Vuelve a iniciar la vinculación."
    "pasarela" -> "Mercado Pago no está disponible en este momento. Inténtalo de nuevo en unos minutos."
    else -> "No pudimos vincular tu cuenta de Mercado Pago. Inténtalo de nuevo."
}

const val MENSAJE_SIN_NAVEGADOR =
    "No encontramos un navegador para abrir Mercado Pago. Instala uno e inténtalo de nuevo."
