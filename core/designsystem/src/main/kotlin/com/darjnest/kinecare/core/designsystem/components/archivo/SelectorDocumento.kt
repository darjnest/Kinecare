package com.darjnest.kinecare.core.designsystem.components.archivo

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState

private val TIPOS_DOCUMENTO = arrayOf("application/pdf", "image/jpeg", "image/png")

/**
 * Selector de archivos del sistema (`OpenDocument`, solo PDF, JPEG y PNG): no pide ningun permiso
 * porque la app solo recibe el archivo que el usuario elige. Devuelve la funcion que abre el
 * selector; [alElegir] recibe el `content://` del documento (nada si el usuario cancela).
 */
@Composable
fun rememberSelectorDocumento(alElegir: (uri: String) -> Unit): () -> Unit {
    val alElegirActual = rememberUpdatedState(alElegir)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) alElegirActual.value(uri.toString())
    }
    return remember(launcher) { { launcher.launch(TIPOS_DOCUMENTO) } }
}
