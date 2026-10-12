package com.darjnest.kinecare.feature.client_panel.presentation.view

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Modifier
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import com.darjnest.kinecare.core.designsystem.theme.KineCareSpacing
import com.darjnest.kinecare.core.designsystem.theme.KineCareTheme

/**
 * Derechos que la Ley N° 20.584 (derechos y deberes de las personas en
 * atencion de salud) reconoce a un paciente, en lenguaje simple. Es texto
 * informativo y general: no reemplaza el texto de la ley.
 */
internal val DERECHOS_DEL_PACIENTE: List<Pair<String, String>> = listOf(
    "Trato digno y sin discriminación" to
        "Debes ser tratado con respeto, sin discriminación arbitraria, y se debe cuidar tu privacidad.",
    "Información clara y oportuna" to
        "Tienes derecho a que te expliquen, en palabras comprensibles, tu estado de salud, el tratamiento y sus riesgos.",
    "Consentimiento informado" to
        "Puedes aceptar o rechazar un tratamiento, una vez informado. Tu decisión se respeta.",
    "Reserva de tus datos" to
        "La información de tu ficha clínica y de tu atención es confidencial y solo puede conocerse en los casos que la ley permite.",
    "Seguridad en la atención" to
        "Tienes derecho a una atención segura y de calidad, con profesionales que cumplan la normativa.",
    "Reclamar" to
        "Si tus derechos no se respetan, puedes reclamar ante el prestador y, si no te responde o no estás conforme, ante la Superintendencia de Salud.",
)

/** Dialogo informativo "Derechos del Paciente (Ley N° 20.584)", abierto desde Mi Perfil. */
@Composable
internal fun DialogoDerechosDelPaciente(onCerrar: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCerrar,
        title = { Text("Derechos del Paciente") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(KineCareSpacing.m),
            ) {
                Text(
                    text = "Ley N° 20.584 sobre derechos y deberes de las personas en la atención de salud. " +
                        "Estos son los principales:",
                    style = MaterialTheme.typography.bodyMedium,
                )
                DERECHOS_DEL_PACIENTE.forEach { (titulo, detalle) ->
                    Column {
                        Text(text = titulo, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        Text(text = detalle, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onCerrar) { Text("Entendido") } },
    )
}

@Preview(showBackground = true)
@Composable
private fun DialogoDerechosDelPacientePreview() {
    KineCareTheme { DialogoDerechosDelPaciente(onCerrar = {}) }
}
