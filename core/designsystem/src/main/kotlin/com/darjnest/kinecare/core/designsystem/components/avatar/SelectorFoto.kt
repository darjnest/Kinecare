package com.darjnest.kinecare.core.designsystem.components.avatar

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState

/**
 * Selector de fotos del sistema (`PickVisualMedia`, solo imagenes): no pide ningun permiso porque la
 * app nunca ve la galeria, solo la imagen que el usuario elige. Devuelve la funcion que abre el
 * selector; [alElegir] recibe el `content://` de la imagen (nada si el usuario cancela).
 */
@Composable
fun rememberSelectorFoto(alElegir: (uri: String) -> Unit): () -> Unit {
    val alElegirActual = rememberUpdatedState(alElegir)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) alElegirActual.value(uri.toString())
    }
    return remember(launcher) {
        { launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
    }
}
