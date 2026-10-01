package com.darjnest.kinecare.feature.client_panel.presentation.view

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.darjnest.kinecare.core.designsystem.components.button.KineCarePrimaryButton
import com.darjnest.kinecare.core.designsystem.components.button.KineCareSecondaryButton
import com.darjnest.kinecare.core.designsystem.components.card.KineCareCard
import com.darjnest.kinecare.core.designsystem.components.label.KineCareBadge
import com.darjnest.kinecare.core.designsystem.components.loading.KineCareFullScreenLoading
import com.darjnest.kinecare.core.designsystem.theme.KineCareSpacing
import com.darjnest.kinecare.core.designsystem.theme.KineCareTheme
import com.darjnest.kinecare.feature.client_panel.domain.EstadoReporte
import com.darjnest.kinecare.feature.client_panel.domain.MotivoReporte
import com.darjnest.kinecare.feature.client_panel.domain.ReporteProblemaError
import com.darjnest.kinecare.feature.client_panel.presentation.viewmodel.ErrorEnvioReporte
import com.darjnest.kinecare.feature.client_panel.presentation.viewmodel.LARGO_MAXIMO_DESCRIPCION
import com.darjnest.kinecare.feature.client_panel.presentation.viewmodel.LARGO_MINIMO_DESCRIPCION
import com.darjnest.kinecare.feature.client_panel.presentation.viewmodel.ReportarProblemaAction
import com.darjnest.kinecare.feature.client_panel.presentation.viewmodel.ReportarProblemaState
import com.darjnest.kinecare.feature.client_panel.presentation.viewmodel.ReportarProblemaViewModel
import com.darjnest.kinecare.feature.client_panel.presentation.viewmodel.ReportePropio

@Composable
fun ReportarProblemaRoot(
    modifier: Modifier = Modifier,
    viewModel: ReportarProblemaViewModel = hiltViewModel(),
    onVolver: () -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    ReportarProblemaScreen(
        state = state,
        onAction = { accion ->
            // Volver atras es navegacion pura: el Root la resuelve contra el NavGraph.
            if (accion is ReportarProblemaAction.VolverAtras) onVolver() else viewModel.onAction(accion)
        },
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportarProblemaScreen(
    state: ReportarProblemaState,
    onAction: (ReportarProblemaAction) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Reportar un problema") },
                navigationIcon = {
                    IconButton(onClick = { onAction(ReportarProblemaAction.VolverAtras) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
            )
        },
    ) { innerPadding ->
        val contenidoModifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
        when {
            state.verificando -> KineCareFullScreenLoading(modifier = contenidoModifier)
            state.errorVerificacion != null -> EstadoErrorVerificacion(
                error = state.errorVerificacion,
                onReintentar = { onAction(ReportarProblemaAction.Reintentar) },
                modifier = contenidoModifier,
            )
            state.reporteExistente != null -> ReporteEnviado(
                reporte = state.reporteExistente,
                recienEnviado = state.recienEnviado,
                onVolver = { onAction(ReportarProblemaAction.VolverAtras) },
                modifier = contenidoModifier,
            )
            else -> Formulario(state = state, onAction = onAction, modifier = contenidoModifier)
        }
    }
}

@Composable
private fun ReporteEnviado(
    reporte: ReportePropio,
    recienEnviado: Boolean,
    onVolver: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(KineCareSpacing.l),
        verticalArrangement = Arrangement.spacedBy(KineCareSpacing.l),
    ) {
        KineCareCard {
            Text(
                text = if (recienEnviado) "Recibimos tu reporte" else "Ya reportaste un problema con esta cita",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "Nuestro equipo de soporte lo revisará. Cada cita admite un solo reporte.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = KineCareSpacing.xs),
            )
            KineCareBadge(
                texto = reporte.estado.aTexto(),
                tono = reporte.estado.aTono(),
                modifier = Modifier.padding(top = KineCareSpacing.m),
            )
            Text(
                text = reporte.motivo.aTexto(),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = KineCareSpacing.m),
            )
            if (reporte.fechaTexto != null) {
                Text(
                    text = "Enviado el ${reporte.fechaTexto}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = reporte.descripcion,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = KineCareSpacing.s),
            )
        }
        KineCareSecondaryButton(text = "Volver", onClick = onVolver)
    }
}

@Composable
private fun Formulario(
    state: ReportarProblemaState,
    onAction: (ReportarProblemaAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(KineCareSpacing.l),
        verticalArrangement = Arrangement.spacedBy(KineCareSpacing.l),
    ) {
        Text(text = "¿Qué pasó con tu cita?", style = MaterialTheme.typography.titleLarge)

        Column(modifier = Modifier.selectableGroup()) {
            MotivoReporte.entries.forEach { motivo ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = state.motivo == motivo,
                            enabled = !state.enviando,
                            role = Role.RadioButton,
                            onClick = { onAction(ReportarProblemaAction.SeleccionarMotivo(motivo)) },
                        )
                        .padding(vertical = KineCareSpacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // onClick = null: la fila entera es el objetivo tactil y anuncia el rol.
                    RadioButton(selected = state.motivo == motivo, onClick = null, enabled = !state.enviando)
                    Text(
                        text = motivo.aTexto(),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(start = KineCareSpacing.m),
                    )
                }
            }
        }

        OutlinedTextField(
            value = state.descripcion,
            onValueChange = { onAction(ReportarProblemaAction.CambiarDescripcion(it)) },
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.enviando,
            label = { Text("Cuéntanos qué pasó") },
            supportingText = {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = if (state.descripcionValida) "" else "Mínimo $LARGO_MINIMO_DESCRIPCION caracteres",
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "${state.descripcion.length}/$LARGO_MAXIMO_DESCRIPCION",
                        textAlign = TextAlign.End,
                    )
                }
            },
            minLines = 4,
            maxLines = 8,
        )

        Text(
            text = "Lo revisa el equipo de soporte de KineCare. El profesional no ve tu reporte.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (state.error != null) {
            Text(
                text = mensajeDeErrorEnvio(state.error),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }

        KineCarePrimaryButton(
            text = if (state.enviando) "Enviando…" else "Enviar reporte",
            onClick = { onAction(ReportarProblemaAction.Enviar) },
            enabled = state.puedeEnviar,
        )
    }
}

@Composable
private fun EstadoErrorVerificacion(
    error: ReporteProblemaError,
    onReintentar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(KineCareSpacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = when (error) {
                ReporteProblemaError.SIN_INTERNET -> "Sin conexión a internet. Revisa tu red e inténtalo de nuevo."
                ReporteProblemaError.SIN_PERMISO -> "No tienes permiso para ver esta cita. Inicia sesión e inténtalo de nuevo."
                ReporteProblemaError.DESCONOCIDO -> "Ocurrió un error inesperado al abrir el reporte."
            },
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        Box(modifier = Modifier.padding(top = KineCareSpacing.l)) {
            KineCarePrimaryButton(text = "Reintentar", onClick = onReintentar)
        }
    }
}

internal fun mensajeDeErrorEnvio(error: ErrorEnvioReporte): String = when (error) {
    ErrorEnvioReporte.SIN_SESION -> "Inicia sesión para reportar un problema."
    ErrorEnvioReporte.SIN_INTERNET -> "Sin conexión a internet. Revisa tu red e inténtalo de nuevo."
    ErrorEnvioReporte.SIN_PERMISO -> "No puedes reportar esta cita: no es tuya o ya enviaste un reporte."
    ErrorEnvioReporte.DESCONOCIDO -> "No pudimos enviar tu reporte. Inténtalo de nuevo."
}

@Preview(showBackground = true, heightDp = 900)
@Composable
private fun ReportarProblemaScreenPreview() {
    KineCareTheme {
        ReportarProblemaScreen(
            state = ReportarProblemaState(
                verificando = false,
                motivo = MotivoReporte.PROFESIONAL_NO_LLEGO,
                descripcion = "Esperé 40 minutos y nunca llegó.",
            ),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ReportarProblemaScreenEnviadoPreview() {
    KineCareTheme {
        ReportarProblemaScreen(
            state = ReportarProblemaState(
                verificando = false,
                reporteExistente = ReportePropio(
                    motivo = MotivoReporte.COBRO_INCORRECTO,
                    descripcion = "Me cobraron dos veces la misma sesión.",
                    estado = EstadoReporte.EN_REVISION,
                    fechaTexto = "02/10/2026",
                ),
            ),
        )
    }
}
