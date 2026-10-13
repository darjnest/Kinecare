package com.darjnest.kinecare.push

import com.darjnest.kinecare.R
import com.darjnest.kinecare.core.common.data.repository.DispositivoPushRepository
import com.darjnest.kinecare.core.common.domain.model.AperturaNotificacion
import com.darjnest.kinecare.core.common.domain.model.DestinoNotificacion
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

/**
 * Notificaciones push de reservas. Payload esperado: `notification {title, body}` en espanol y
 * `data {tipo, reservaId}` (ver docs/DATA_MODEL.md#notificaciones-push-fcm).
 *
 * - [onNewToken]: FCM rota el token; se registra en `usuarios/{uid}/dispositivos/{token}`. Sin sesion
 *   no hace nada: el token se registra al iniciar sesion.
 * - [onMessageReceived]: solo se invoca con la app en primer plano (en segundo plano el sistema
 *   muestra la notificacion y, al tocarla, `MainActivity` recibe el `data` como extras del intent).
 */
@AndroidEntryPoint
class KineCareMessagingService : FirebaseMessagingService() {

    @Inject
    lateinit var dispositivoPushRepository: DispositivoPushRepository

    override fun onNewToken(token: String) {
        // El SDK llama esto en un hilo de fondo y el servicio puede morir al volver: se espera el
        // resultado (acotado) en vez de lanzar una corrutina que nadie garantiza que termine.
        runBlocking(Dispatchers.IO) {
            withTimeoutOrNull(TIEMPO_MAXIMO_REGISTRO_MS) { dispositivoPushRepository.registrarToken(token) }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val aviso = message.notification ?: return
        val cuerpo = aviso.body?.takeIf { it.isNotBlank() } ?: return
        val titulo = aviso.title?.takeIf { it.isNotBlank() } ?: getString(R.string.notificacion_titulo_por_defecto)
        val apertura = AperturaNotificacion.desdeDatos(
            tipo = message.data[DestinoNotificacion.CLAVE_TIPO],
            reservaId = message.data[DestinoNotificacion.CLAVE_RESERVA_ID],
        )
        mostrarNotificacionCita(applicationContext, titulo, cuerpo, apertura)
    }

    private companion object {
        const val TIEMPO_MAXIMO_REGISTRO_MS = 10_000L
    }
}
