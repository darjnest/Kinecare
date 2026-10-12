package com.darjnest.kinecare.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.darjnest.kinecare.R

/**
 * Canal "Citas" de las notificaciones de reservas. Se crea al arrancar la app (idempotente) y no solo
 * al mostrar la primera notificacion: las que el sistema muestra con la app en segundo plano usan el
 * canal por defecto del manifest, que tiene que existir ya para entonces.
 */
internal fun crearCanalCitas(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val canal = NotificationChannel(
        context.getString(R.string.canal_citas_id),
        context.getString(R.string.canal_citas_nombre),
        NotificationManager.IMPORTANCE_HIGH,
    ).apply {
        description = context.getString(R.string.canal_citas_descripcion)
    }
    context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(canal)
}
