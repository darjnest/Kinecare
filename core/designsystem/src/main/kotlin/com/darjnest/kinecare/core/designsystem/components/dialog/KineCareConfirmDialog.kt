package com.darjnest.kinecare.core.designsystem.components.dialog

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.darjnest.kinecare.core.designsystem.theme.KineCareTheme

@Composable
fun KineCareConfirmDialog(
    titulo: String,
    mensaje: String,
    textoConfirmar: String,
    textoCancelar: String,
    onConfirmar: () -> Unit,
    onCancelar: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancelar,
        title = { Text(titulo) },
        text = { Text(mensaje) },
        confirmButton = {
            TextButton(onClick = onConfirmar) { Text(textoConfirmar) }
        },
        dismissButton = {
            TextButton(onClick = onCancelar) { Text(textoCancelar) }
        },
    )
}

@Preview(showBackground = true)
@Composable
private fun KineCareConfirmDialogPreview() {
    KineCareTheme {
        KineCareConfirmDialog(
            titulo = "Cancelar reserva",
            mensaje = "Esta accion no se puede deshacer.",
            textoConfirmar = "Cancelar reserva",
            textoCancelar = "Volver",
            onConfirmar = {},
            onCancelar = {},
        )
    }
}
