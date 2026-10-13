package com.darjnest.kinecare.feature.professional_panel.presentation.view

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.darjnest.kinecare.core.common.domain.model.ModalidadServicio
import com.darjnest.kinecare.core.designsystem.theme.KineCareTheme
import com.darjnest.kinecare.core.designsystem.theme.LoginPrimario
import com.darjnest.kinecare.feature.professional_panel.presentation.viewmodel.CampoServicio
import com.darjnest.kinecare.feature.professional_panel.presentation.viewmodel.FormularioServicio
import com.darjnest.kinecare.feature.professional_panel.presentation.viewmodel.ServiciosYTarifasAction

/** Formulario para crear o editar un servicio del catálogo del profesional. */
@Composable
internal fun DialogoServicio(
    formulario: FormularioServicio,
    onAction: (ServiciosYTarifasAction) -> Unit,
) {
    val editando = formulario.servicioId != null
    AlertDialog(
        onDismissRequest = { if (!formulario.guardando) onAction(ServiciosYTarifasAction.CancelarServicio) },
        containerColor = Color.White,
        title = { Text(if (editando) "Editar servicio" else "Nuevo servicio") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Campo(
                    valor = formulario.nombre,
                    etiqueta = "Nombre",
                    campo = CampoServicio.NOMBRE,
                    deshabilitado = formulario.guardando,
                    onAction = onAction,
                    ayuda = "Ej. Kinesiología motora a domicilio",
                )
                Text(
                    text = "Modalidad",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    // ONLINE solo si el servicio ya la tenia: hoy no hay sala ni enlace de videollamada.
                    val opciones = listOfNotNull(
                        ModalidadServicio.DOMICILIO,
                        ModalidadServicio.CONSULTA,
                        ModalidadServicio.ONLINE.takeIf { formulario.modalidad == ModalidadServicio.ONLINE },
                    )
                    opciones.forEach { modalidad ->
                        FilterChip(
                            selected = formulario.modalidad == modalidad,
                            onClick = { onAction(ServiciosYTarifasAction.CambiarModalidadServicio(modalidad)) },
                            enabled = !formulario.guardando,
                            label = {
                                Text(
                                    when (modalidad) {
                                        ModalidadServicio.DOMICILIO -> "A domicilio"
                                        ModalidadServicio.CONSULTA -> "En consulta"
                                        ModalidadServicio.ONLINE -> "Online"
                                    },
                                )
                            },
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Campo(
                    valor = formulario.duracionTexto,
                    etiqueta = "Duración (minutos)",
                    campo = CampoServicio.DURACION,
                    deshabilitado = formulario.guardando,
                    onAction = onAction,
                    numerico = true,
                    ayuda = "Entre ${FormularioServicio.DURACION_MIN} y ${FormularioServicio.DURACION_MAX}",
                    esError = formulario.duracionTexto.isNotBlank() && formulario.duracionMinutos == null,
                )
                Campo(
                    valor = formulario.precioTexto,
                    etiqueta = "Precio (CLP)",
                    campo = CampoServicio.PRECIO,
                    deshabilitado = formulario.guardando,
                    onAction = onAction,
                    numerico = true,
                    ayuda = "Entre $1.000 y $1.000.000",
                    esError = formulario.precioTexto.isNotBlank() && formulario.precio == null,
                )
                Campo(
                    valor = formulario.descripcion,
                    etiqueta = "Descripción (opcional)",
                    campo = CampoServicio.DESCRIPCION,
                    deshabilitado = formulario.guardando,
                    onAction = onAction,
                    unaLinea = false,
                )
                if (formulario.error != null) {
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
                onClick = { onAction(ServiciosYTarifasAction.GuardarServicio) },
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
                onClick = { onAction(ServiciosYTarifasAction.CancelarServicio) },
                enabled = !formulario.guardando,
            ) { Text("Cancelar") }
        },
    )
}

@Composable
private fun Campo(
    valor: String,
    etiqueta: String,
    campo: CampoServicio,
    deshabilitado: Boolean,
    onAction: (ServiciosYTarifasAction) -> Unit,
    numerico: Boolean = false,
    unaLinea: Boolean = true,
    ayuda: String? = null,
    esError: Boolean = false,
) {
    OutlinedTextField(
        value = valor,
        onValueChange = { onAction(ServiciosYTarifasAction.CambiarCampoServicio(campo, it)) },
        label = { Text(etiqueta) },
        supportingText = ayuda?.let { { Text(it) } },
        singleLine = unaLinea,
        minLines = if (unaLinea) 1 else 2,
        maxLines = if (unaLinea) 1 else 4,
        isError = esError,
        enabled = !deshabilitado,
        keyboardOptions = KeyboardOptions(
            capitalization = if (numerico) KeyboardCapitalization.None else KeyboardCapitalization.Sentences,
            keyboardType = if (numerico) KeyboardType.Number else KeyboardType.Text,
        ),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = LoginPrimario,
            focusedLabelColor = LoginPrimario,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(modifier = Modifier.height(4.dp))
}

@Preview(showBackground = true)
@Composable
private fun DialogoServicioPreview() {
    KineCareTheme {
        DialogoServicio(
            formulario = FormularioServicio(nombre = "Kinesiología motora", duracionTexto = "60", precioTexto = "35000"),
            onAction = {},
        )
    }
}
