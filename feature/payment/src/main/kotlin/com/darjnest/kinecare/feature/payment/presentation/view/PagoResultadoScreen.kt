package com.darjnest.kinecare.feature.payment.presentation.view

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.darjnest.kinecare.core.common.data.error.EstadoPagoError
import com.darjnest.kinecare.core.designsystem.components.bar.KineCareTopBar
import com.darjnest.kinecare.core.designsystem.components.button.KineCarePrimaryButton
import com.darjnest.kinecare.core.designsystem.components.button.KineCareSecondaryButton
import com.darjnest.kinecare.core.designsystem.components.feedback.KineCareStatusMessage
import com.darjnest.kinecare.core.designsystem.components.label.BadgeTono
import com.darjnest.kinecare.core.designsystem.theme.KineCareTheme
import com.darjnest.kinecare.feature.payment.presentation.util.esReintentable
import com.darjnest.kinecare.feature.payment.presentation.util.mensajeErrorEstado
import com.darjnest.kinecare.feature.payment.presentation.viewmodel.FasePagoResultado
import com.darjnest.kinecare.feature.payment.presentation.viewmodel.PagoResultadoAction
import com.darjnest.kinecare.feature.payment.presentation.viewmodel.PagoResultadoState
import com.darjnest.kinecare.feature.payment.presentation.viewmodel.PagoResultadoViewModel

@Composable
fun PagoResultadoRoot(
    modifier: Modifier = Modifier,
    viewModel: PagoResultadoViewModel = hiltViewModel(),
    onIrAMisCitas: () -> Unit = {},
    onReintentarPago: () -> Unit = {},
    onIniciarSesion: () -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    PagoResultadoScreen(
        state = state,
        onAction = { accion ->
            // Salir de esta pantalla es navegacion: el Root la resuelve contra el NavGraph.
            when (accion) {
                PagoResultadoAction.IrAMisCitas -> onIrAMisCitas()
                PagoResultadoAction.ReintentarPago -> onReintentarPago()
                PagoResultadoAction.IniciarSesion -> onIniciarSesion()
                PagoResultadoAction.Reintentar -> viewModel.onAction(accion)
            }
        },
        modifier = modifier,
    )
}

@Composable
fun PagoResultadoScreen(
    state: PagoResultadoState,
    onAction: (PagoResultadoAction) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = { KineCareTopBar(titulo = "Resultado del pago") },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            when (state.fase) {
                FasePagoResultado.CONSULTANDO -> KineCareStatusMessage(
                    titulo = "Confirmando tu pago",
                    mensaje = "Estamos verificando el pago con Mercado Pago. Puede tardar unos segundos.",
                    tono = BadgeTono.INFO,
                )

                FasePagoResultado.APROBADO -> KineCareStatusMessage(
                    titulo = "Pago aprobado",
                    mensaje = "Tu pago fue aprobado y tu reserva quedó pagada.",
                    tono = BadgeTono.EXITO,
                    icono = Icons.Filled.CheckCircle,
                ) {
                    BotonVolverAMisCitas(onAction, principal = true)
                }

                FasePagoResultado.EN_PROCESO -> KineCareStatusMessage(
                    titulo = "Tu pago está en proceso",
                    mensaje = "Mercado Pago todavía no confirma tu pago. Se reflejará en Mis Citas apenas lo confirme.",
                    tono = BadgeTono.INFO,
                    icono = Icons.Filled.Schedule,
                ) {
                    BotonVolverAMisCitas(onAction, principal = true)
                    KineCareSecondaryButton(
                        text = "Actualizar estado",
                        onClick = { onAction(PagoResultadoAction.Reintentar) },
                    )
                }

                FasePagoResultado.RECHAZADO -> KineCareStatusMessage(
                    titulo = "Pago rechazado",
                    mensaje = "Mercado Pago no aprobó tu pago. Puedes intentarlo de nuevo con otro medio de pago.",
                    tono = BadgeTono.ERROR,
                    icono = Icons.Filled.Error,
                ) {
                    KineCarePrimaryButton(
                        text = "Reintentar pago",
                        onClick = { onAction(PagoResultadoAction.ReintentarPago) },
                    )
                    BotonVolverAMisCitas(onAction, principal = false)
                }

                FasePagoResultado.REEMBOLSADO -> KineCareStatusMessage(
                    titulo = "Pago reembolsado",
                    mensaje = "Este pago fue reembolsado.",
                    tono = BadgeTono.NEUTRO,
                    icono = Icons.Filled.Replay,
                ) {
                    BotonVolverAMisCitas(onAction, principal = true)
                }

                FasePagoResultado.ERROR -> {
                    val error = state.error ?: EstadoPagoError.DESCONOCIDO
                    KineCareStatusMessage(
                        titulo = "No pudimos confirmar tu pago",
                        mensaje = mensajeErrorEstado(error),
                        tono = BadgeTono.ERROR,
                        icono = Icons.Filled.Error,
                    ) {
                        when {
                            error == EstadoPagoError.SIN_SESION -> KineCarePrimaryButton(
                                text = "Iniciar sesión",
                                onClick = { onAction(PagoResultadoAction.IniciarSesion) },
                            )
                            esReintentable(error) -> {
                                KineCarePrimaryButton(
                                    text = "Reintentar",
                                    onClick = { onAction(PagoResultadoAction.Reintentar) },
                                )
                                BotonVolverAMisCitas(onAction, principal = false)
                            }
                            else -> BotonVolverAMisCitas(onAction, principal = true)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BotonVolverAMisCitas(onAction: (PagoResultadoAction) -> Unit, principal: Boolean) {
    if (principal) {
        KineCarePrimaryButton(text = "Volver a Mis Citas", onClick = { onAction(PagoResultadoAction.IrAMisCitas) })
    } else {
        KineCareSecondaryButton(text = "Volver a Mis Citas", onClick = { onAction(PagoResultadoAction.IrAMisCitas) })
    }
}

@Preview(showBackground = true)
@Composable
private fun PagoResultadoConsultandoPreview() {
    KineCareTheme { PagoResultadoScreen(state = PagoResultadoState()) }
}

@Preview(showBackground = true)
@Composable
private fun PagoResultadoAprobadoPreview() {
    KineCareTheme { PagoResultadoScreen(state = PagoResultadoState(FasePagoResultado.APROBADO)) }
}

@Preview(showBackground = true)
@Composable
private fun PagoResultadoEnProcesoPreview() {
    KineCareTheme { PagoResultadoScreen(state = PagoResultadoState(FasePagoResultado.EN_PROCESO)) }
}

@Preview(showBackground = true)
@Composable
private fun PagoResultadoRechazadoPreview() {
    KineCareTheme { PagoResultadoScreen(state = PagoResultadoState(FasePagoResultado.RECHAZADO)) }
}

@Preview(showBackground = true)
@Composable
private fun PagoResultadoErrorPreview() {
    KineCareTheme {
        PagoResultadoScreen(state = PagoResultadoState(FasePagoResultado.ERROR, EstadoPagoError.SIN_INTERNET))
    }
}
