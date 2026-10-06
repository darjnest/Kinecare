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
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.darjnest.kinecare.core.designsystem.components.bar.KineCareTopBar
import com.darjnest.kinecare.core.designsystem.components.button.KineCarePrimaryButton
import com.darjnest.kinecare.core.designsystem.components.button.KineCareSecondaryButton
import com.darjnest.kinecare.core.designsystem.components.feedback.KineCareStatusMessage
import com.darjnest.kinecare.core.designsystem.components.label.BadgeTono
import com.darjnest.kinecare.core.designsystem.theme.KineCareTheme
import com.darjnest.kinecare.feature.payment.presentation.viewmodel.FaseConexion
import com.darjnest.kinecare.feature.payment.presentation.viewmodel.MercadoPagoConectadoAction
import com.darjnest.kinecare.feature.payment.presentation.viewmodel.MercadoPagoConectadoState
import com.darjnest.kinecare.feature.payment.presentation.viewmodel.MercadoPagoConectadoViewModel

@Composable
fun MercadoPagoConectadoRoot(
    modifier: Modifier = Modifier,
    viewModel: MercadoPagoConectadoViewModel = hiltViewModel(),
    onIrAlPanel: () -> Unit = {},
    onVolverAConectar: () -> Unit = {},
    onIniciarSesion: () -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    MercadoPagoConectadoScreen(
        state = state,
        onAction = { accion ->
            // Salir de esta pantalla es navegacion: el Root la resuelve contra el NavGraph.
            when (accion) {
                MercadoPagoConectadoAction.IrAlPanel -> onIrAlPanel()
                MercadoPagoConectadoAction.VolverAConectar -> onVolverAConectar()
                MercadoPagoConectadoAction.IniciarSesion -> onIniciarSesion()
                MercadoPagoConectadoAction.Reintentar -> viewModel.onAction(accion)
            }
        },
        modifier = modifier,
    )
}

@Composable
fun MercadoPagoConectadoScreen(
    state: MercadoPagoConectadoState,
    onAction: (MercadoPagoConectadoAction) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = { KineCareTopBar(titulo = "Cobros con Mercado Pago") },
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
                FaseConexion.VERIFICANDO -> KineCareStatusMessage(
                    titulo = "Verificando tu cuenta",
                    mensaje = "Estamos confirmando la vinculación con Mercado Pago.",
                    tono = BadgeTono.INFO,
                )

                FaseConexion.CONECTADA -> KineCareStatusMessage(
                    titulo = "Cuenta vinculada",
                    mensaje = "Listo: ya puedes recibir pagos de tus clientes con Mercado Pago.",
                    tono = BadgeTono.EXITO,
                    icono = Icons.Filled.CheckCircle,
                ) {
                    KineCarePrimaryButton(
                        text = "Volver al panel",
                        onClick = { onAction(MercadoPagoConectadoAction.IrAlPanel) },
                    )
                }

                FaseConexion.NO_CONFIRMADA -> KineCareStatusMessage(
                    titulo = "No pudimos confirmar la vinculación",
                    mensaje = "No vemos tu cuenta de Mercado Pago como vinculada. Vuelve a intentarlo.",
                    tono = BadgeTono.ERROR,
                    icono = Icons.Filled.Error,
                ) {
                    KineCarePrimaryButton(
                        text = "Volver a intentar",
                        onClick = { onAction(MercadoPagoConectadoAction.VolverAConectar) },
                    )
                    KineCareSecondaryButton(
                        text = "Volver al panel",
                        onClick = { onAction(MercadoPagoConectadoAction.IrAlPanel) },
                    )
                }

                FaseConexion.SIN_SESION -> KineCareStatusMessage(
                    titulo = "No pudimos verificar tu cuenta",
                    mensaje = "Tu sesión expiró. Inicia sesión de nuevo para revisar la vinculación.",
                    tono = BadgeTono.ERROR,
                    icono = Icons.Filled.Error,
                ) {
                    KineCarePrimaryButton(
                        text = "Iniciar sesión",
                        onClick = { onAction(MercadoPagoConectadoAction.IniciarSesion) },
                    )
                }

                FaseConexion.SIN_INTERNET,
                FaseConexion.DESCONOCIDO,
                -> KineCareStatusMessage(
                    titulo = "No pudimos verificar tu cuenta",
                    mensaje = if (state.fase == FaseConexion.SIN_INTERNET) {
                        "Sin conexión a internet. Revisa tu red e inténtalo de nuevo."
                    } else {
                        "Ocurrió un error inesperado. Inténtalo de nuevo."
                    },
                    tono = BadgeTono.ERROR,
                    icono = Icons.Filled.Error,
                ) {
                    KineCarePrimaryButton(
                        text = "Reintentar",
                        onClick = { onAction(MercadoPagoConectadoAction.Reintentar) },
                    )
                    KineCareSecondaryButton(
                        text = "Volver al panel",
                        onClick = { onAction(MercadoPagoConectadoAction.IrAlPanel) },
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun MercadoPagoConectadoVerificandoPreview() {
    KineCareTheme { MercadoPagoConectadoScreen(state = MercadoPagoConectadoState()) }
}

@Preview(showBackground = true)
@Composable
private fun MercadoPagoConectadoConectadaPreview() {
    KineCareTheme { MercadoPagoConectadoScreen(state = MercadoPagoConectadoState(FaseConexion.CONECTADA)) }
}

@Preview(showBackground = true)
@Composable
private fun MercadoPagoConectadoNoConfirmadaPreview() {
    KineCareTheme { MercadoPagoConectadoScreen(state = MercadoPagoConectadoState(FaseConexion.NO_CONFIRMADA)) }
}
