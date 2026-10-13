package com.darjnest.kinecare.feature.verification.presentation.util

import com.darjnest.kinecare.feature.verification.domain.EstadoVerificacionError
import com.darjnest.kinecare.feature.verification.domain.MotivoRechazoVerificacion
import com.darjnest.kinecare.feature.verification.domain.SolicitarVerificacionError

private const val MENSAJE_SIN_INTERNET = "Sin conexión a internet. Revisa tu red e inténtalo de nuevo."

fun mensajeErrorSolicitar(error: SolicitarVerificacionError): String = when (error) {
    SolicitarVerificacionError.SIN_SESION -> "Tu sesión expiró. Inicia sesión de nuevo para verificar tu identidad."
    SolicitarVerificacionError.SIN_INTERNET -> MENSAJE_SIN_INTERNET
    SolicitarVerificacionError.NO_ES_PROFESIONAL -> "Solo una cuenta de profesional puede verificar su identidad."
    SolicitarVerificacionError.TIPO_NO_SOPORTADO ->
        "Este tipo de verificación todavía no está disponible. Actualiza la app e inténtalo de nuevo."
    SolicitarVerificacionError.PERFIL_NO_ENCONTRADO ->
        "No encontramos tu perfil profesional. Completa tu perfil e inténtalo de nuevo."
    SolicitarVerificacionError.YA_VERIFICADO -> "Tu identidad ya está verificada."
    SolicitarVerificacionError.PROVEEDOR_NO_CONFIGURADO ->
        "La verificación de identidad no está disponible por ahora. Contacta a soporte de KineCare."
    SolicitarVerificacionError.PROVEEDOR_NO_DISPONIBLE ->
        "El proveedor de verificación no está disponible en este momento. Inténtalo de nuevo en unos minutos."
    SolicitarVerificacionError.DESCONOCIDO -> "No pudimos iniciar la verificación. Inténtalo de nuevo en unos minutos."
}

fun mensajeErrorEstado(error: EstadoVerificacionError): String = when (error) {
    EstadoVerificacionError.SIN_SESION ->
        "Tu sesión expiró. Inicia sesión de nuevo para ver el estado de tu verificación."
    EstadoVerificacionError.SIN_INTERNET -> MENSAJE_SIN_INTERNET
    EstadoVerificacionError.SOLICITUD_NO_ENCONTRADA ->
        "No encontramos esta verificación. Vuelve a Verificar identidad para revisar su estado."
    EstadoVerificacionError.DESCONOCIDO -> "No pudimos consultar el estado de tu verificación. Inténtalo de nuevo."
}

/** `true` si volver a consultar tiene sentido: los demas errores no cambian reintentando. */
fun esReintentable(error: EstadoVerificacionError): Boolean = when (error) {
    EstadoVerificacionError.SIN_INTERNET,
    EstadoVerificacionError.DESCONOCIDO,
    -> true
    EstadoVerificacionError.SIN_SESION,
    EstadoVerificacionError.SOLICITUD_NO_ENCONTRADA,
    -> false
}

/** Titulo de una verificacion `RECHAZADO`; un [motivo] ausente o desconocido cae en el generico. */
fun tituloRechazo(motivo: MotivoRechazoVerificacion?): String = when (motivo) {
    MotivoRechazoVerificacion.RUT_NO_COINCIDE -> "El documento no coincide con el RUT de tu cuenta"
    MotivoRechazoVerificacion.EXPIRADA,
    MotivoRechazoVerificacion.ABANDONADA,
    -> "No completaste la verificación"
    MotivoRechazoVerificacion.KYC_VENCIDO -> "Tu verificación venció"
    MotivoRechazoVerificacion.DECLINED,
    null,
    -> "No pudimos verificar tu identidad"
}

/** Que hacer despues de una verificacion `RECHAZADO`, segun el [motivo]. */
fun mensajeRechazo(motivo: MotivoRechazoVerificacion?): String = when (motivo) {
    MotivoRechazoVerificacion.RUT_NO_COINCIDE ->
        "Usa la cédula de identidad del titular de esta cuenta y vuelve a intentarlo."
    MotivoRechazoVerificacion.EXPIRADA ->
        "La sesión de verificación venció antes de terminar. Puedes volver a intentarlo cuando quieras."
    MotivoRechazoVerificacion.ABANDONADA ->
        "Dejaste la verificación a medias. Puedes volver a intentarlo cuando quieras."
    MotivoRechazoVerificacion.KYC_VENCIDO ->
        "Tu verificación anterior ya no está vigente. Vuelve a verificar tu identidad para mantener tu insignia."
    MotivoRechazoVerificacion.DECLINED,
    null,
    -> "Revisa que tu cédula se vea completa y legible, y que haya buena luz para la selfie. Puedes volver a intentarlo."
}

const val MENSAJE_SIN_NAVEGADOR =
    "No encontramos un navegador para abrir la verificación. Instala uno e inténtalo de nuevo."
