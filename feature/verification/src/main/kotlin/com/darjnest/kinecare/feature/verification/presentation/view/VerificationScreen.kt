package com.darjnest.kinecare.feature.verification.presentation.view

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.darjnest.kinecare.core.designsystem.components.bar.KineCareTopBar
import com.darjnest.kinecare.core.designsystem.components.button.KineCarePrimaryButton
import com.darjnest.kinecare.core.designsystem.components.button.KineCareSecondaryButton
import com.darjnest.kinecare.core.designsystem.components.card.KineCareCard
import com.darjnest.kinecare.core.designsystem.components.feedback.KineCareStatusMessage
import com.darjnest.kinecare.core.designsystem.components.label.BadgeTono
import com.darjnest.kinecare.core.designsystem.components.loading.KineCareFullScreenLoading
import com.darjnest.kinecare.core.designsystem.theme.KineCareSpacing
import com.darjnest.kinecare.core.designsystem.theme.KineCareTheme
import com.darjnest.kinecare.core.designsystem.util.abrirEnCustomTab
import com.darjnest.kinecare.feature.verification.domain.EstadoVerificacionError
import com.darjnest.kinecare.feature.verification.domain.MotivoRechazoVerificacion
import com.darjnest.kinecare.feature.verification.domain.SolicitarVerificacionError
import com.darjnest.kinecare.feature.verification.presentation.util.MENSAJE_SIN_NAVEGADOR
import com.darjnest.kinecare.feature.verification.presentation.util.esReintentable
import com.darjnest.kinecare.feature.verification.presentation.util.mensajeErrorEstado
import com.darjnest.kinecare.feature.verification.presentation.util.mensajeErrorSolicitar
import com.darjnest.kinecare.feature.verification.presentation.util.mensajeRechazo
import com.darjnest.kinecare.feature.verification.presentation.util.tituloRechazo
import com.darjnest.kinecare.feature.verification.presentation.viewmodel.FaseVerificacion
import com.darjnest.kinecare.feature.verification.presentation.viewmodel.VerificationAction
import com.darjnest.kinecare.feature.verification.presentation.viewmodel.VerificationEvent
import com.darjnest.kinecare.feature.verification.presentation.viewmodel.VerificationState
import com.darjnest.kinecare.feature.verification.presentation.viewmodel.VerificationViewModel

@Composable
fun VerificationRoot(
    modifier: Modifier = Modifier,
    viewModel: VerificationViewModel = hiltViewModel(),
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
                    is VerificationEvent.AbrirVerificacion ->
                        if (!abrirEnCustomTab(context, evento.url)) {
                            viewModel.onAction(VerificationAction.NavegadorNoDisponible)
                        }
                }
            }
        }
    }

    // Al volver del Custom Tab (o del resultado) se refresca una verificacion en curso.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.onAction(VerificationAction.Reanudar)
    }

    VerificationScreen(
        state = state,
        onAction = { accion ->
            // Salir de esta pantalla es navegacion: el Root la resuelve contra el NavGraph.
            when (accion) {
                VerificationAction.VolverAtras -> onVolver()
                VerificationAction.IniciarSesion -> onIniciarSesion()
                else -> viewModel.onAction(accion)
            }
        },
        modifier = modifier,
    )
}

@Composable
fun VerificationScreen(
    state: VerificationState,
    onAction: (VerificationAction) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            KineCareTopBar(
                titulo = "Verificar identidad",
                onVolver = { onAction(VerificationAction.VolverAtras) },
            )
        },
    ) { innerPadding ->
        if (state.fase == FaseVerificacion.CARGANDO) {
            KineCareFullScreenLoading(modifier = Modifier.padding(innerPadding))
            return@Scaffold
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            val ocupado = state.solicitando || state.actualizando
            when (state.fase) {
                // Se resuelve arriba: el spinner a pantalla completa no cabe dentro de un scroll.
                FaseVerificacion.CARGANDO -> Unit

                FaseVerificacion.NO_SOLICITADO -> ExplicacionVerificacion(state, onAction)

                FaseVerificacion.PENDIENTE -> KineCareStatusMessage(
                    titulo = "Verificación en curso",
                    mensaje = "Empezaste tu verificación de identidad pero aún no termina. " +
                        "Continúa donde la dejaste o actualiza el estado si ya la completaste.",
                    tono = BadgeTono.INFO,
                    icono = Icons.Filled.Schedule,
                ) {
                    MensajesDeError(state, onAction)
                    KineCarePrimaryButton(
                        text = if (state.solicitando) "Abriendo la verificación…" else "Continuar verificación",
                        onClick = { onAction(VerificationAction.Verificar) },
                        enabled = !ocupado,
                    )
                    KineCareSecondaryButton(
                        text = if (state.actualizando) "Actualizando…" else "Actualizar estado",
                        onClick = { onAction(VerificationAction.Actualizar) },
                        enabled = !ocupado,
                    )
                }

                FaseVerificacion.APROBADO -> KineCareStatusMessage(
                    titulo = "Identidad verificada",
                    mensaje = "Tu identidad quedó verificada. No necesitas hacer nada más.",
                    tono = BadgeTono.EXITO,
                    icono = Icons.Filled.CheckCircle,
                )

                FaseVerificacion.RECHAZADO -> KineCareStatusMessage(
                    titulo = tituloRechazo(state.motivoRechazo),
                    mensaje = mensajeRechazo(state.motivoRechazo),
                    tono = BadgeTono.ERROR,
                    icono = Icons.Filled.Error,
                ) {
                    MensajesDeError(state, onAction)
                    KineCarePrimaryButton(
                        text = if (state.solicitando) "Abriendo la verificación…" else "Intentar de nuevo",
                        onClick = { onAction(VerificationAction.Verificar) },
                        enabled = !ocupado,
                    )
                }

                FaseVerificacion.ERROR -> {
                    val error = state.errorEstado ?: EstadoVerificacionError.DESCONOCIDO
                    KineCareStatusMessage(
                        titulo = "No pudimos consultar tu verificación",
                        mensaje = mensajeErrorEstado(error),
                        tono = BadgeTono.ERROR,
                        icono = Icons.Filled.Error,
                    ) {
                        when {
                            error == EstadoVerificacionError.SIN_SESION -> KineCarePrimaryButton(
                                text = "Iniciar sesión",
                                onClick = { onAction(VerificationAction.IniciarSesion) },
                            )
                            esReintentable(error) -> KineCarePrimaryButton(
                                text = "Reintentar",
                                onClick = { onAction(VerificationAction.Reintentar) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Antes de empezar: que se pide, que no se guarda y quien lo procesa. */
@Composable
private fun ExplicacionVerificacion(
    state: VerificationState,
    onAction: (VerificationAction) -> Unit,
) {
    Column(
        modifier = Modifier.padding(KineCareSpacing.l),
        verticalArrangement = Arrangement.spacedBy(KineCareSpacing.l),
    ) {
        Text(
            text = "Verifica tu identidad",
            style = MaterialTheme.typography.titleLarge,
        )
        KineCareCard {
            Text(
                text = "Para mostrar la insignia de identidad verificada te pediremos dos cosas: " +
                    "una foto de tu cédula de identidad y una selfie con prueba de vida " +
                    "(un breve movimiento frente a la cámara).",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Text(
            text = "La verificación la procesa un proveedor externo (Didit) en tu navegador. " +
                "KineCare no guarda tu cédula, tu selfie ni tus datos biométricos en tu teléfono: " +
                "solo muestra el resultado que nos informa el proveedor.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(verticalArrangement = Arrangement.spacedBy(KineCareSpacing.s)) {
            MensajesDeError(state, onAction)
            KineCarePrimaryButton(
                text = if (state.solicitando) "Abriendo la verificación…" else "Verificar mi identidad",
                onClick = { onAction(VerificationAction.Verificar) },
                enabled = !state.solicitando,
            )
        }
    }
}

/** Errores de "Verificar" y de "Actualizar estado" en linea, con "Iniciar sesion" si la sesion expiro. */
@Composable
private fun ColumnScope.MensajesDeError(
    state: VerificationState,
    onAction: (VerificationAction) -> Unit,
) {
    val mensaje = when {
        state.errorSolicitud != null -> mensajeErrorSolicitar(state.errorSolicitud)
        state.errorEstado != null && state.fase != FaseVerificacion.ERROR -> mensajeErrorEstado(state.errorEstado)
        state.navegadorNoDisponible -> MENSAJE_SIN_NAVEGADOR
        else -> null
    }
    if (mensaje != null) {
        Text(
            text = mensaje,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
    }
    val sinSesion = state.errorSolicitud == SolicitarVerificacionError.SIN_SESION ||
        (state.fase != FaseVerificacion.ERROR && state.errorEstado == EstadoVerificacionError.SIN_SESION)
    if (sinSesion) {
        KineCareSecondaryButton(
            text = "Iniciar sesión",
            onClick = { onAction(VerificationAction.IniciarSesion) },
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun VerificationScreenCargandoPreview() {
    KineCareTheme { VerificationScreen(state = VerificationState()) }
}

@Preview(showBackground = true)
@Composable
private fun VerificationScreenNoSolicitadoPreview() {
    KineCareTheme { VerificationScreen(state = VerificationState(fase = FaseVerificacion.NO_SOLICITADO)) }
}

@Preview(showBackground = true)
@Composable
private fun VerificationScreenPendientePreview() {
    KineCareTheme { VerificationScreen(state = VerificationState(fase = FaseVerificacion.PENDIENTE)) }
}

@Preview(showBackground = true)
@Composable
private fun VerificationScreenAprobadoPreview() {
    KineCareTheme { VerificationScreen(state = VerificationState(fase = FaseVerificacion.APROBADO)) }
}

@Preview(showBackground = true)
@Composable
private fun VerificationScreenRechazadoPreview() {
    KineCareTheme {
        VerificationScreen(
            state = VerificationState(
                fase = FaseVerificacion.RECHAZADO,
                motivoRechazo = MotivoRechazoVerificacion.RUT_NO_COINCIDE,
            ),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun VerificationScreenErrorPreview() {
    KineCareTheme {
        VerificationScreen(
            state = VerificationState(
                fase = FaseVerificacion.ERROR,
                errorEstado = EstadoVerificacionError.SIN_INTERNET,
            ),
        )
    }
}
