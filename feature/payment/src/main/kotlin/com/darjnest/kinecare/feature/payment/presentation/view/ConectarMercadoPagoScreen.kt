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
import com.darjnest.kinecare.feature.payment.domain.ConectarMercadoPagoError
import com.darjnest.kinecare.core.designsystem.components.bar.KineCareTopBar
import com.darjnest.kinecare.core.designsystem.components.button.KineCarePrimaryButton
import com.darjnest.kinecare.core.designsystem.components.button.KineCareSecondaryButton
import com.darjnest.kinecare.core.designsystem.components.card.KineCareCard
import com.darjnest.kinecare.core.designsystem.theme.KineCareSpacing
import com.darjnest.kinecare.core.designsystem.theme.KineCareTheme
import com.darjnest.kinecare.feature.payment.presentation.util.MENSAJE_SIN_NAVEGADOR
import com.darjnest.kinecare.core.designsystem.util.abrirEnCustomTab
import com.darjnest.kinecare.feature.payment.presentation.util.mensajeErrorConexion
import com.darjnest.kinecare.feature.payment.presentation.viewmodel.ConectarMercadoPagoAction
import com.darjnest.kinecare.feature.payment.presentation.viewmodel.ConectarMercadoPagoEvent
import com.darjnest.kinecare.feature.payment.presentation.viewmodel.ConectarMercadoPagoState
import com.darjnest.kinecare.feature.payment.presentation.viewmodel.ConectarMercadoPagoViewModel

@Composable
fun ConectarMercadoPagoRoot(
    modifier: Modifier = Modifier,
    viewModel: ConectarMercadoPagoViewModel = hiltViewModel(),
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
                    is ConectarMercadoPagoEvent.AbrirAutorizacion ->
                        if (!abrirEnCustomTab(context, evento.url)) {
                            viewModel.onAction(ConectarMercadoPagoAction.NavegadorNoDisponible)
                        }
                }
            }
        }
    }

    ConectarMercadoPagoScreen(
        state = state,
        onAction = { accion ->
            // Salir de esta pantalla es navegacion: el Root la resuelve contra el NavGraph.
            when (accion) {
                ConectarMercadoPagoAction.VolverAtras -> onVolver()
                ConectarMercadoPagoAction.IniciarSesion -> onIniciarSesion()
                else -> viewModel.onAction(accion)
            }
        },
        modifier = modifier,
    )
}

@Composable
fun ConectarMercadoPagoScreen(
    state: ConectarMercadoPagoState,
    onAction: (ConectarMercadoPagoAction) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            KineCareTopBar(
                titulo = "Cobros con Mercado Pago",
                onVolver = { onAction(ConectarMercadoPagoAction.VolverAtras) },
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
                text = "Cobra tus atenciones directo en tu cuenta",
                style = MaterialTheme.typography.titleLarge,
            )
            KineCareCard {
                Text(
                    text = "Vincula tu cuenta de Mercado Pago para que tus clientes te paguen a ti directamente. " +
                        "KineCare retiene una comisión por cada reserva pagada; el monto exacto lo calcula " +
                        "KineCare al momento del cobro.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Text(
                text = "Se abrirá Mercado Pago en tu navegador para que autorices la vinculación. " +
                    "KineCare nunca ve tu contraseña de Mercado Pago.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val mensajeError = when {
                state.error != null -> mensajeErrorConexion(state.error)
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
            if (state.error == ConectarMercadoPagoError.SIN_SESION) {
                KineCareSecondaryButton(
                    text = "Iniciar sesión",
                    onClick = { onAction(ConectarMercadoPagoAction.IniciarSesion) },
                )
            }
            KineCarePrimaryButton(
                text = if (state.cargando) "Preparando la vinculación…" else "Vincular cuenta de Mercado Pago",
                onClick = { onAction(ConectarMercadoPagoAction.Conectar) },
                enabled = !state.cargando,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ConectarMercadoPagoScreenPreview() {
    KineCareTheme { ConectarMercadoPagoScreen(state = ConectarMercadoPagoState()) }
}

@Preview(showBackground = true)
@Composable
private fun ConectarMercadoPagoScreenErrorPreview() {
    KineCareTheme {
        ConectarMercadoPagoScreen(state = ConectarMercadoPagoState(error = ConectarMercadoPagoError.SIN_INTERNET))
    }
}
