package com.darjnest.kinecare.navigation

import android.Manifest
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.util.Consumer
import com.darjnest.kinecare.core.common.domain.model.AperturaNotificacion
import com.darjnest.kinecare.core.common.domain.model.DestinoNotificacion
import com.darjnest.kinecare.core.common.domain.model.RolUsuario
import com.darjnest.kinecare.core.common.domain.model.ResolucionApertura

/**
 * Entrega a [alAbrir] cada apertura pedida por una notificacion push: la del intent que arranco la
 * Activity y las de `onNewIntent` (`MainActivity` es `singleTask`). No usa `navController.graph.hasDeepLink`
 * como los deep links de Mercado Pago porque el destino depende de la sesion: ver
 * [AbrirNotificacionPendiente].
 */
@Composable
internal fun EntregarAperturasDeNotificacion(alAbrir: (AperturaNotificacion) -> Unit) {
    val activity = LocalContext.current.buscarActivity() ?: return
    val alAbrirActual by rememberUpdatedState(alAbrir)
    // El intent de arranque sigue en `activity.intent` tras girar la pantalla o restaurar el proceso:
    // se procesa una sola vez.
    var intentInicialProcesado by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(activity) {
        if (!intentInicialProcesado) {
            intentInicialProcesado = true
            activity.intent.aAperturaNotificacion()?.let(alAbrirActual)
        }
    }
    DisposableEffect(activity) {
        val oyente = Consumer<Intent> { intent -> intent.aAperturaNotificacion()?.let(alAbrirActual) }
        activity.addOnNewIntentListener(oyente)
        onDispose { activity.removeOnNewIntentListener(oyente) }
    }
}

/**
 * Navega a la apertura [pendiente] cuando corresponde (ver [AperturaNotificacion.resolver]). [alResolver]
 * la quita del estado del llamador; [alNavegar] hace el `navigate` real hacia el [DestinoNotificacion] del rol.
 */
@Composable
internal fun AbrirNotificacionPendiente(
    pendiente: AperturaNotificacion?,
    rolUsuario: RolUsuario?,
    verificandoSesion: Boolean,
    enPantallaDeAcceso: Boolean,
    alResolver: () -> Unit,
    alNavegar: (DestinoNotificacion) -> Unit,
) {
    LaunchedEffect(pendiente, rolUsuario, verificandoSesion, enPantallaDeAcceso) {
        val apertura = pendiente ?: return@LaunchedEffect
        when (apertura.resolver(rolUsuario, verificandoSesion, enPantallaDeAcceso)) {
            ResolucionApertura.ESPERAR -> Unit
            ResolucionApertura.DESCARTAR -> alResolver()
            ResolucionApertura.ABRIR -> {
                alResolver()
                rolUsuario?.let(apertura::destinoPara)?.let(alNavegar)
            }
        }
    }
}

/**
 * Pide `POST_NOTIFICATIONS` (Android 13+) una vez que hay sesion: es el momento en que los avisos
 * tienen sentido y la pantalla de inicio ya esta visible. Nunca bloquea: la respuesta se ignora (sin
 * el permiso la app funciona igual y solo no avisa) y se pide una sola vez por apertura de la app.
 */
@Composable
internal fun SolicitarPermisoNotificaciones(haySesion: Boolean) {
    val context = LocalContext.current
    var yaPedido by rememberSaveable { mutableStateOf(false) }
    val lanzador = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(haySesion) {
        if (!haySesion || yaPedido || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return@LaunchedEffect
        val concedido = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!concedido) {
            yaPedido = true
            lanzador.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

/**
 * La apertura que pide este intent: el enlace interno `kinecare://notificacion?tipo=` (notificacion
 * mostrada por la app) o el `data` de FCM como extras (`tipo`, `reservaId`; notificacion mostrada por el
 * sistema). Un intent relanzado desde "recientes" conserva los extras de la vez anterior y no cuenta.
 */
internal fun Intent.aAperturaNotificacion(): AperturaNotificacion? {
    if (flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0) return null
    AperturaNotificacion.desdeEnlace(dataString)?.let { return it }
    val extras = extras ?: return null
    return AperturaNotificacion.desdeDatos(
        tipo = extras.getString(DestinoNotificacion.CLAVE_TIPO),
        reservaId = extras.getString(DestinoNotificacion.CLAVE_RESERVA_ID),
    )
}

internal fun Context.buscarActivity(): ComponentActivity? {
    var contexto: Context? = this
    while (contexto is ContextWrapper) {
        if (contexto is ComponentActivity) return contexto
        contexto = contexto.baseContext
    }
    return null
}
