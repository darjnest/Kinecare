package com.darjnest.kinecare.feature.payment.presentation.view

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.darjnest.kinecare.core.designsystem.components.bar.KineCareTopBar
import com.darjnest.kinecare.core.designsystem.components.button.KineCarePrimaryButton
import com.darjnest.kinecare.core.designsystem.components.button.KineCareSecondaryButton
import com.darjnest.kinecare.core.designsystem.components.card.KineCareCard
import com.darjnest.kinecare.core.designsystem.theme.KineCareSpacing
import com.darjnest.kinecare.core.designsystem.theme.KineCareTheme
import com.darjnest.kinecare.core.designsystem.util.abrirEnCustomTab
import com.darjnest.kinecare.feature.payment.domain.PagarComisionError
import com.darjnest.kinecare.feature.payment.presentation.util.MENSAJE_SIN_NAVEGADOR
import com.darjnest.kinecare.feature.payment.presentation.util.mensajeErrorPagarComision
import com.darjnest.kinecare.feature.payment.presentation.viewmodel.PagarComisionAction
import com.darjnest.kinecare.feature.payment.presentation.viewmodel.PagarComisionEvent
import com.darjnest.kinecare.feature.payment.presentation.viewmodel.PagarComisionState
import com.darjnest.kinecare.feature.payment.presentation.viewmodel.PagarComisionViewModel

@Composable
fun PagarComisionRoot(
    modifier: Modifier = Modifier,
    viewModel: PagarComisionViewModel = hiltViewModel(),
    onVolver: () -> Unit = {},
    onIniciarSesion: () -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // El Custom Tab se abre solo con la pantalla visible: un evento que llega en
    // segundo plano espera en el canal hasta volver a STARTED.
    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.events.collect { evento ->
                when (evento) {
                    is PagarComisionEvent.AbrirPago ->
                        if (!abrirEnCustomTab(context, evento.url)) {
                            viewModel.onAction(PagarComisionAction.NavegadorNoDisponible)
                        }
                }
            }
        }
    }

    PagarComisionScreen(
        state = state,
        onAction = { accion ->
            // Salir de esta pantalla es navegacion: el Root la resuelve contra el NavGraph.
            when (accion) {
                PagarComisionAction.VolverAtras -> onVolver()
                PagarComisionAction.IniciarSesion -> onIniciarSesion()
                else -> viewModel.onAction(accion)
            }
        },
        modifier = modifier,
    )
}

@Composable
fun PagarComisionScreen(
    state: PagarComisionState,
    onAction: (PagarComisionAction) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            KineCareTopBar(
                titulo = "Pagar comisiones",
                onVolver = { onAction(PagarComisionAction.VolverAtras) },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(KineCareSpacing.l)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(KineCareSpacing.l),
        ) {
            Text(
                text = "Comisiones de atenciones en efectivo",
                style = MaterialTheme.typography.titleLarge,
            )
            KineCareCard {
                Text(
                    text = "Cuando una atención no se paga por la app, el dinero no pasa por Mercado Pago y " +
                        "KineCare no puede descontar su comisión. Ese monto queda como saldo por pagar. " +
                        "Si pagas con tarjeta por la app, se descuenta solo de ese pago; también puedes " +
                        "pagarlo ahora.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Text(
                text = "Se abrirá Mercado Pago en tu navegador. El monto lo calcula KineCare con tus " +
                    "comisiones pendientes.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val mensajeError = when {
                state.error != null -> mensajeErrorPagarComision(state.error)
                state.navegadorNoDisponible -> MENSAJE_SIN_NAVEGADOR
                else -> null
            }
            if (mensajeError != null) {
                Text(
                    text = mensajeError,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (state.error == PagarComisionError.SIN_SESION) {
                KineCareSecondaryButton(
                    text = "Iniciar sesión",
                    onClick = { onAction(PagarComisionAction.IniciarSesion) },
                )
            }
            KineCarePrimaryButton(
                text = if (state.cargando) "Preparando el pago…" else "Pagar con Mercado Pago",
                onClick = { onAction(PagarComisionAction.Pagar) },
                enabled = !state.cargando,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PagarComisionScreenPreview() {
    KineCareTheme { PagarComisionScreen(state = PagarComisionState()) }
}

@Preview(showBackground = true)
@Composable
private fun PagarComisionScreenErrorPreview() {
    KineCareTheme {
        PagarComisionScreen(state = PagarComisionState(error = PagarComisionError.SIN_SALDO_PENDIENTE))
    }
}
