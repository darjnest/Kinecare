package com.darjnest.kinecare.core.common.data.repository

import com.darjnest.kinecare.core.common.data.error.DispositivoPushError
import com.darjnest.kinecare.core.common.result.Result

/**
 * Registro del dispositivo para notificaciones push (FCM), en
 * `usuarios/{uid}/dispositivos/{token}` (ver docs/DATA_MODEL.md). El token identifica al
 * dispositivo; el backend lo usa para enviar las notificaciones de reservas. Siempre actua sobre
 * el usuario autenticado en ese momento.
 */
interface DispositivoPushRepository {

    /** Pide el token actual a FCM y lo registra para el usuario autenticado (al iniciar sesion). */
    suspend fun registrarDispositivoActual(): Result<Unit, DispositivoPushError>

    /**
     * Registra [token] para el usuario autenticado (FCM lo rota y avisa por `onNewToken`). Sin
     * sesion devuelve `SIN_SESION`: el token se registrara en el proximo inicio de sesion.
     */
    suspend fun registrarToken(token: String): Result<Unit, DispositivoPushError>

    /**
     * Borra el registro del token de este dispositivo (al cerrar sesion, **antes** de cerrarla:
     * las reglas exigen estar autenticado) e invalida el token de FCM para que la proxima cuenta
     * que inicie sesion aqui reciba uno nuevo y no las notificaciones de la anterior.
     */
    suspend fun eliminarDispositivoActual(): Result<Unit, DispositivoPushError>
}
