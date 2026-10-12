package com.darjnest.kinecare.core.designsystem.components.dialog

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.tooling.preview.Preview
import com.darjnest.kinecare.core.designsystem.theme.KineCareSpacing
import com.darjnest.kinecare.core.designsystem.theme.KineCareTheme

/**
 * Dialogo de confirmacion con un campo de texto multilinea y contador: sirve
 * para "motivo de cancelacion" (opcional) y para responder una resena
 * (obligatorio, con [textoConfirmar] deshabilitado mientras [puedeConfirmar]
 * sea falso). [mensaje] es opcional y se muestra sobre el campo; [aviso], si
 * existe, queda destacado debajo del mensaje (p. ej. la politica de reembolso).
 * El texto lo controla quien llama ([valor] / [onValorChange]); el limite
 * [maxCaracteres] se aplica aqui.
 */
@Composable
fun KineCareTextInputDialog(
    titulo: String,
    etiquetaCampo: String,
    valor: String,
    onValorChange: (String) -> Unit,
    maxCaracteres: Int,
    textoConfirmar: String,
    textoCancelar: String,
    onConfirmar: () -> Unit,
    onCancelar: () -> Unit,
    modifier: Modifier = Modifier,
    mensaje: String? = null,
    aviso: String? = null,
    error: String? = null,
    enProgreso: Boolean = false,
    puedeConfirmar: Boolean = true,
) {
    AlertDialog(
        modifier = modifier,
        onDismissRequest = { if (!enProgreso) onCancelar() },
        title = { Text(titulo) },
        text = {
            Column {
                if (mensaje != null) {
                    Text(mensaje, style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(KineCareSpacing.s))
                }
                if (aviso != null) {
                    Text(
                        text = aviso,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.height(KineCareSpacing.s))
                }
                OutlinedTextField(
                    value = valor,
                    onValueChange = { if (it.length <= maxCaracteres) onValorChange(it) },
                    label = { Text(etiquetaCampo) },
                    enabled = !enProgreso,
                    minLines = 3,
                    maxLines = 5,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    supportingText = { Text("${valor.length}/$maxCaracteres") },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (error != null) {
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirmar, enabled = puedeConfirmar && !enProgreso) {
                if (enProgreso) {
                    CircularProgressIndicator(modifier = Modifier.size(KineCareSpacing.icono), strokeWidth = KineCareSpacing.xs / 2)
                } else {
                    Text(textoConfirmar)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onCancelar, enabled = !enProgreso) { Text(textoCancelar) }
        },
    )
}

@Preview(showBackground = true)
@Composable
private fun KineCareTextInputDialogPreview() {
    KineCareTheme {
        KineCareTextInputDialog(
            titulo = "Cancelar cita",
            etiquetaCampo = "Motivo (opcional)",
            valor = "Me surgio un imprevisto",
            onValorChange = {},
            maxCaracteres = 300,
            textoConfirmar = "Cancelar cita",
            textoCancelar = "Volver",
            onConfirmar = {},
            onCancelar = {},
            mensaje = "Cita con Ana Soto, 14/10/2026 10:00.",
            aviso = "Cancelas con menos de 24 horas: no hay reembolso automatico.",
        )
    }
}
