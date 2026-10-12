package com.darjnest.kinecare.push

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import com.darjnest.kinecare.MainActivity
import com.darjnest.kinecare.R
import com.darjnest.kinecare.core.common.domain.model.AperturaNotificacion

/**
 * Muestra una notificacion de reserva en el canal "Citas" cuando la app esta en primer plano (con la
 * app en segundo plano la muestra el sistema). Al tocarla abre `MainActivity` con el enlace interno
 * `kinecare://notificacion?tipo=...` (intent explicito: no se expone a otras apps ni al navegador); un
 * `tipo` desconocido solo abre la app donde estaba.
 */
internal fun mostrarNotificacionCita(
    context: Context,
    titulo: String,
    cuerpo: String,
    apertura: AperturaNotificacion?,
) {
    val manager = NotificationManagerCompat.from(context)
    // Sin permiso (Android 13+) o con las notificaciones apagadas no hay nada que mostrar.
    if (!manager.areNotificationsEnabled()) return

    val intent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        if (apertura != null) {
            action = Intent.ACTION_VIEW
            data = apertura.enlace.toUri()
        }
    }
    // Una notificacion por reserva: la siguiente de la misma reserva reemplaza a la anterior.
    val id = (apertura?.enlace ?: "general").hashCode()
    val pendingIntent = PendingIntent.getActivity(
        context,
        id,
        intent,
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
    val notificacion = NotificationCompat.Builder(context, context.getString(R.string.canal_citas_id))
        .setSmallIcon(R.drawable.ic_notificacion)
        .setContentTitle(titulo)
        .setContentText(cuerpo)
        .setStyle(NotificationCompat.BigTextStyle().bigText(cuerpo))
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setAutoCancel(true)
        .setContentIntent(pendingIntent)
        .build()
    notificar(manager, id, notificacion)
}

// El permiso POST_NOTIFICATIONS ya se comprobo con `areNotificationsEnabled()`.
@SuppressLint("MissingPermission")
private fun notificar(manager: NotificationManagerCompat, id: Int, notificacion: android.app.Notification) {
    try {
        manager.notify(id, notificacion)
    } catch (_: SecurityException) {
        // El usuario revoco el permiso entre la comprobacion y el aviso: no hay nada que hacer.
    }
}
