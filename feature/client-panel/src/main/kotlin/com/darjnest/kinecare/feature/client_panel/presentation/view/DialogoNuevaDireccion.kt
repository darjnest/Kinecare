package com.darjnest.kinecare.feature.client_panel.presentation.view

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.darjnest.kinecare.core.designsystem.theme.KineCareTheme
import com.darjnest.kinecare.core.designsystem.theme.LoginPrimario
import com.darjnest.kinecare.feature.client_panel.presentation.viewmodel.CampoDireccion
import com.darjnest.kinecare.feature.client_panel.presentation.viewmodel.FormularioDireccion
import com.darjnest.kinecare.feature.client_panel.presentation.viewmodel.MiPerfilClienteAction

/** Diálogo "Agregar nueva dirección" de Mi Perfil (atenciones a domicilio). */
@Composable
internal fun DialogoNuevaDireccion(
    formulario: FormularioDireccion,
    onAction: (MiPerfilClienteAction) -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!formulario.guardando) onAction(MiPerfilClienteAction.CancelarDireccion) },
        containerColor = Color.White,
        title = { Text("Nueva dirección") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                CampoDeDireccion(formulario.calle, "Calle", CampoDireccion.CALLE, formulario.guardando, onAction)
                CampoDeDireccion(
                    formulario.numero, "Número", CampoDireccion.NUMERO, formulario.guardando, onAction,
                    teclado = KeyboardType.Text,
                )
                CampoDeDireccion(formulario.comuna, "Comuna", CampoDireccion.COMUNA, formulario.guardando, onAction)
                CampoDeDireccion(formulario.ciudad, "Ciudad", CampoDireccion.CIUDAD, formulario.guardando, onAction)
                CampoDeDireccion(
                    formulario.indicaciones, "Indicaciones (opcional)", CampoDireccion.INDICACIONES,
                    formulario.guardando, onAction,
                    ayuda = "Ej. depto 45, portón negro",
                )
                if (formulario.error != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = formulario.error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onAction(MiPerfilClienteAction.GuardarDireccion) },
                enabled = formulario.puedeGuardar,
            ) {
                if (formulario.guardando) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text("Guardar")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = { onAction(MiPerfilClienteAction.CancelarDireccion) },
                enabled = !formulario.guardando,
            ) { Text("Cancelar") }
        },
    )
}

@Composable
private fun CampoDeDireccion(
    valor: String,
    etiqueta: String,
    campo: CampoDireccion,
    deshabilitado: Boolean,
    onAction: (MiPerfilClienteAction) -> Unit,
    teclado: KeyboardType = KeyboardType.Text,
    ayuda: String? = null,
) {
    OutlinedTextField(
        value = valor,
        onValueChange = { onAction(MiPerfilClienteAction.CambiarCampoDireccion(campo, it)) },
        label = { Text(etiqueta, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingText = ayuda?.let { { Text(it) } },
        singleLine = true,
        enabled = !deshabilitado,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, keyboardType = teclado),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = LoginPrimario,
            focusedLabelColor = LoginPrimario,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(modifier = Modifier.height(8.dp))
}

@Preview(showBackground = true)
@Composable
private fun DialogoNuevaDireccionPreview() {
    KineCareTheme {
        DialogoNuevaDireccion(
            formulario = FormularioDireccion(calle = "Los Aromos", numero = "123", comuna = "Providencia"),
            onAction = {},
        )
    }
}
