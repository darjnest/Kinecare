package com.darjnest.kinecare.feature.verification.presentation.view

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.VerifiedUser
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
import com.darjnest.kinecare.feature.verification.domain.EstadoVerificacionError
import com.darjnest.kinecare.feature.verification.domain.MotivoRechazoVerificacion
import com.darjnest.kinecare.feature.verification.presentation.util.esReintentable
import com.darjnest.kinecare.feature.verification.presentation.util.mensajeErrorEstado
import com.darjnest.kinecare.feature.verification.presentation.util.mensajeRechazo
import com.darjnest.kinecare.feature.verification.presentation.util.tituloRechazo
import com.darjnest.kinecare.feature.verification.presentation.viewmodel.FaseVerificacionResultado
import com.darjnest.kinecare.feature.verification.presentation.viewmodel.VerificacionResultadoAction
import com.darjnest.kinecare.feature.verification.presentation.viewmodel.VerificacionResultadoState
import com.darjnest.kinecare.feature.verification.presentation.viewmodel.VerificacionResultadoViewModel

@Composable
fun VerificacionResultadoRoot(
    modifier: Modifier = Modifier,
    viewModel: VerificacionResultadoViewModel = hiltViewModel(),
    onListo: () -> Unit = {},
    onIniciarSesion: () -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    VerificacionResultadoScreen(
        state = state,
        onAction = { accion ->
            // Salir de esta pantalla es navegacion: el Root la resuelve contra el NavGraph.
            when (accion) {
                VerificacionResultadoAction.Listo -> onListo()
                VerificacionResultadoAction.IniciarSesion -> onIniciarSesion()
                VerificacionResultadoAction.Reintentar -> viewModel.onAction(accion)
            }
        },
        modifier = modifier,
    )
}

@Composable
fun VerificacionResultadoScreen(
    state: VerificacionResultadoState,
    onAction: (VerificacionResultadoAction) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = { KineCareTopBar(titulo = "Resultado de la verificación") },
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
                FaseVerificacionResultado.CONSULTANDO -> KineCareStatusMessage(
                    titulo = "Revisando tu verificación",
                    mensaje = "Estamos consultando el resultado con el proveedor. Puede tardar unos segundos.",
                    tono = BadgeTono.INFO,
                )

                FaseVerificacionResultado.APROBADO -> KineCareStatusMessage(
                    titulo = "Identidad verificada",
                    mensaje = "Tu verificación fue aprobada y tu identidad quedó verificada.",
                    tono = BadgeTono.EXITO,
                    icono = Icons.Filled.CheckCircle,
                ) {
                    BotonListo(onAction)
                }

                FaseVerificacionResultado.EN_REVISION -> KineCareStatusMessage(
                    titulo = "Estamos revisando tu verificación",
                    mensaje = "Todavía no tenemos el resultado. Te avisaremos cuando termine.",
                    tono = BadgeTono.INFO,
                    icono = Icons.Filled.Schedule,
                ) {
                    BotonListo(onAction)
                    KineCareSecondaryButton(
                        text = "Actualizar estado",
                        onClick = { onAction(VerificacionResultadoAction.Reintentar) },
                    )
                }

                FaseVerificacionResultado.RECHAZADO -> KineCareStatusMessage(
                    titulo = tituloRechazo(state.motivo),
                    mensaje = mensajeRechazo(state.motivo),
                    tono = BadgeTono.ERROR,
                    icono = Icons.Filled.Error,
                ) {
                    BotonListo(onAction)
                }

                FaseVerificacionResultado.NO_SOLICITADO -> KineCareStatusMessage(
                    titulo = "No encontramos una verificación",
                    mensaje = "Todavía no iniciaste la verificación de tu identidad.",
                    tono = BadgeTono.NEUTRO,
                    icono = Icons.Filled.VerifiedUser,
                ) {
                    BotonListo(onAction)
                }

                FaseVerificacionResultado.ERROR -> {
                    val error = state.error ?: EstadoVerificacionError.DESCONOCIDO
                    KineCareStatusMessage(
                        titulo = "No pudimos consultar tu verificación",
                        mensaje = mensajeErrorEstado(error),
                        tono = BadgeTono.ERROR,
                        icono = Icons.Filled.Error,
                    ) {
                        when {
                            error == EstadoVerificacionError.SIN_SESION -> KineCarePrimaryButton(
                                text = "Iniciar sesión",
                                onClick = { onAction(VerificacionResultadoAction.IniciarSesion) },
                            )
                            esReintentable(error) -> {
                                KineCarePrimaryButton(
                                    text = "Reintentar",
                                    onClick = { onAction(VerificacionResultadoAction.Reintentar) },
                                )
                                KineCareSecondaryButton(
                                    text = "Listo",
                                    onClick = { onAction(VerificacionResultadoAction.Listo) },
                                )
                            }
                            else -> BotonListo(onAction)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BotonListo(onAction: (VerificacionResultadoAction) -> Unit) {
    KineCarePrimaryButton(text = "Listo", onClick = { onAction(VerificacionResultadoAction.Listo) })
}

@Preview(showBackground = true)
@Composable
private fun VerificacionResultadoConsultandoPreview() {
    KineCareTheme { VerificacionResultadoScreen(state = VerificacionResultadoState()) }
}

@Preview(showBackground = true)
@Composable
private fun VerificacionResultadoAprobadoPreview() {
    KineCareTheme {
        VerificacionResultadoScreen(state = VerificacionResultadoState(FaseVerificacionResultado.APROBADO))
    }
}

@Preview(showBackground = true)
@Composable
private fun VerificacionResultadoEnRevisionPreview() {
    KineCareTheme {
        VerificacionResultadoScreen(state = VerificacionResultadoState(FaseVerificacionResultado.EN_REVISION))
    }
}

@Preview(showBackground = true)
@Composable
private fun VerificacionResultadoRechazadoPreview() {
    KineCareTheme {
        VerificacionResultadoScreen(
            state = VerificacionResultadoState(
                FaseVerificacionResultado.RECHAZADO,
                motivo = MotivoRechazoVerificacion.DECLINED,
            ),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun VerificacionResultadoErrorPreview() {
    KineCareTheme {
        VerificacionResultadoScreen(
            state = VerificacionResultadoState(
                FaseVerificacionResultado.ERROR,
                error = EstadoVerificacionError.SIN_INTERNET,
            ),
        )
    }
}
