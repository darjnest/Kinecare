package com.darjnest.kinecare.feature.payment.presentation.view

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import com.darjnest.kinecare.feature.payment.domain.IniciarPagoError
import com.darjnest.kinecare.core.common.util.formatComoClp
import com.darjnest.kinecare.core.designsystem.components.bar.KineCareTopBar
import com.darjnest.kinecare.core.designsystem.components.button.KineCarePrimaryButton
import com.darjnest.kinecare.core.designsystem.components.card.KineCareCard
import com.darjnest.kinecare.core.designsystem.theme.KineCareSpacing
import com.darjnest.kinecare.core.designsystem.theme.KineCareTheme
import com.darjnest.kinecare.feature.payment.presentation.util.MENSAJE_SIN_NAVEGADOR
import com.darjnest.kinecare.core.designsystem.util.abrirEnCustomTab
import com.darjnest.kinecare.feature.payment.presentation.util.mensajeErrorIniciar
import com.darjnest.kinecare.feature.payment.presentation.viewmodel.PaymentAction
import com.darjnest.kinecare.feature.payment.presentation.viewmodel.PaymentEvent
import com.darjnest.kinecare.feature.payment.presentation.viewmodel.PaymentState
import com.darjnest.kinecare.feature.payment.presentation.viewmodel.PaymentViewModel

@Composable
fun PaymentRoot(
    modifier: Modifier = Modifier,
    viewModel: PaymentViewModel = hiltViewModel(),
    onVolver: () -> Unit = {},
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
                    is PaymentEvent.AbrirPago ->
                        if (!abrirEnCustomTab(context, evento.url)) {
                            viewModel.onAction(PaymentAction.NavegadorNoDisponible)
                        }
                }
            }
        }
    }

    PaymentScreen(
        state = state,
        onAction = { accion ->
            // Volver atras es navegacion: el Root la resuelve contra el NavGraph.
            if (accion == PaymentAction.VolverAtras) onVolver() else viewModel.onAction(accion)
        },
        modifier = modifier,
    )
}

@Composable
fun PaymentScreen(
    state: PaymentState,
    onAction: (PaymentAction) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            KineCareTopBar(titulo = "Pagar reserva", onVolver = { onAction(PaymentAction.VolverAtras) })
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(KineCareSpacing.l),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(KineCareSpacing.l)) {
                KineCareCard {
                    Text(
                        text = "Servicio",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(KineCareSpacing.xs))
                    Text(
                        text = state.titulo.ifBlank { "Reserva" },
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                Text(
                    text = "Pagas de forma segura en Mercado Pago. Se abrirá en tu navegador y " +
                        "cuando termines volverás a KineCare. KineCare no ve ni guarda los datos de tu tarjeta.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val mensajeError = when {
                    state.error != null -> mensajeErrorIniciar(state.error)
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
            }

            Column(verticalArrangement = Arrangement.spacedBy(KineCareSpacing.m)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(text = "Total a pagar", style = MaterialTheme.typography.titleMedium)
                    // Solo informativo: el monto real lo fija el backend al iniciar el pago.
                    Text(text = state.montoClp.formatComoClp(), style = MaterialTheme.typography.titleLarge)
                }
                KineCarePrimaryButton(
                    text = if (state.cargando) "Preparando el pago…" else "Pagar con Mercado Pago",
                    onClick = { onAction(PaymentAction.Pagar) },
                    enabled = !state.cargando,
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PaymentScreenPreview() {
    KineCareTheme {
        PaymentScreen(state = PaymentState(titulo = "Kinesiología deportiva", montoClp = 25_000))
    }
}

@Preview(showBackground = true)
@Composable
private fun PaymentScreenCargandoPreview() {
    KineCareTheme {
        PaymentScreen(state = PaymentState(titulo = "Kinesiología deportiva", montoClp = 25_000, cargando = true))
    }
}

@Preview(showBackground = true)
@Composable
private fun PaymentScreenErrorPreview() {
    KineCareTheme {
        PaymentScreen(
            state = PaymentState(
                titulo = "Kinesiología deportiva",
                montoClp = 25_000,
                error = IniciarPagoError.PROFESIONAL_SIN_CUENTA_MP,
            ),
        )
    }
}
