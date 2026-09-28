package com.darjnest.kinecare.feature.reviews.presentation.view

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.darjnest.kinecare.core.common.data.error.ResenaError
import com.darjnest.kinecare.core.designsystem.components.button.KineCarePrimaryButton
import com.darjnest.kinecare.core.designsystem.components.button.KineCareSecondaryButton
import com.darjnest.kinecare.core.designsystem.components.card.KineCareCard
import com.darjnest.kinecare.core.designsystem.components.loading.KineCareFullScreenLoading
import com.darjnest.kinecare.core.designsystem.theme.InicioDorado
import com.darjnest.kinecare.core.designsystem.theme.KineCareSpacing
import com.darjnest.kinecare.core.designsystem.theme.KineCareTheme
import com.darjnest.kinecare.feature.reviews.presentation.viewmodel.CALIFICACION_MAXIMA
import com.darjnest.kinecare.feature.reviews.presentation.viewmodel.CrearResenaAction
import com.darjnest.kinecare.feature.reviews.presentation.viewmodel.CrearResenaState
import com.darjnest.kinecare.feature.reviews.presentation.viewmodel.CrearResenaViewModel
import com.darjnest.kinecare.feature.reviews.presentation.viewmodel.ErrorEnvioResena
import com.darjnest.kinecare.feature.reviews.presentation.viewmodel.LARGO_MAXIMO_COMENTARIO
import com.darjnest.kinecare.feature.reviews.presentation.viewmodel.ResenaPropia

@Composable
fun CrearResenaRoot(
    modifier: Modifier = Modifier,
    viewModel: CrearResenaViewModel = hiltViewModel(),
    onVolver: () -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Enviar es asincronico (escribe en `resenas/{reservaId}`): solo volvemos
    // atras cuando el ViewModel confirma el guardado. Mismo patron que
    // `InformacionPersonalClienteRoot` (`guardadoExitoso`).
    LaunchedEffect(state.enviada) {
        if (state.enviada) onVolver()
    }

    CrearResenaScreen(
        state = state,
        onAction = { accion ->
            // Volver atras es navegacion pura: el Root la resuelve contra el NavGraph.
            if (accion is CrearResenaAction.VolverAtras) onVolver() else viewModel.onAction(accion)
        },
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CrearResenaScreen(
    state: CrearResenaState,
    onAction: (CrearResenaAction) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Reseñar atención") },
                navigationIcon = {
                    IconButton(onClick = { onAction(CrearResenaAction.VolverAtras) }) {
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
            state.errorVerificacion != null -> EstadoErrorResenas(
                error = state.errorVerificacion,
                onReintentar = { onAction(CrearResenaAction.Reintentar) },
                modifier = contenidoModifier,
            )
            state.resenaExistente != null -> YaResenada(
                resena = state.resenaExistente,
                onVolver = { onAction(CrearResenaAction.VolverAtras) },
                modifier = contenidoModifier,
            )
            else -> Formulario(state = state, onAction = onAction, modifier = contenidoModifier)
        }
    }
}

@Composable
private fun YaResenada(
    resena: ResenaPropia,
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
            Text(text = "Ya reseñaste esta atención", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "Gracias por compartir tu experiencia. Una atención solo se puede reseñar una vez.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = KineCareSpacing.xs),
            )
            EstrellasCalificacion(
                calificacion = resena.calificacion,
                modifier = Modifier.padding(top = KineCareSpacing.m),
            )
            if (resena.comentario != null) {
                Text(
                    text = resena.comentario,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = KineCareSpacing.s),
                )
            }
        }
        KineCareSecondaryButton(text = "Volver", onClick = onVolver)
    }
}

@Composable
private fun Formulario(
    state: CrearResenaState,
    onAction: (CrearResenaAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(KineCareSpacing.l),
        verticalArrangement = Arrangement.spacedBy(KineCareSpacing.l),
    ) {
        Text(text = "¿Cómo fue tu atención?", style = MaterialTheme.typography.titleLarge)

        SelectorEstrellas(
            calificacion = state.calificacion,
            habilitado = !state.enviando,
            onSeleccionar = { onAction(CrearResenaAction.SeleccionarCalificacion(it)) },
        )

        OutlinedTextField(
            value = state.comentario,
            onValueChange = { onAction(CrearResenaAction.CambiarComentario(it)) },
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.enviando,
            label = { Text("Comentario (opcional)") },
            supportingText = {
                Text(
                    text = "${state.comentario.length}/$LARGO_MAXIMO_COMENTARIO",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.End,
                )
            },
            minLines = 3,
            maxLines = 6,
        )

        if (state.error != null) {
            Text(
                text = mensajeDeErrorEnvio(state.error),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }

        KineCarePrimaryButton(
            text = if (state.enviando) "Enviando…" else "Enviar reseña",
            onClick = { onAction(CrearResenaAction.Enviar) },
            enabled = state.puedeEnviar,
        )
    }
}

/** Selector tactil de 1 a 5 estrellas; tocar la estrella N deja calificacion = N. */
@Composable
private fun SelectorEstrellas(
    calificacion: Int,
    habilitado: Boolean,
    onSeleccionar: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.selectableGroup(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            (1..CALIFICACION_MAXIMA).forEach { posicion ->
                val seleccionada = posicion <= calificacion
                IconButton(
                    onClick = { onSeleccionar(posicion) },
                    enabled = habilitado,
                    modifier = Modifier
                        .size(KineCareSpacing.iconoTactil)
                        .semantics {
                            role = Role.RadioButton
                            selected = posicion == calificacion
                        },
                ) {
                    Icon(
                        imageVector = if (seleccionada) Icons.Filled.Star else Icons.Filled.StarBorder,
                        contentDescription = if (posicion == 1) "1 estrella" else "$posicion estrellas",
                        tint = if (seleccionada) InicioDorado else MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(KineCareSpacing.xl),
                    )
                }
            }
        }
        Text(
            text = if (calificacion == 0) "Toca una estrella para calificar" else "$calificacion de $CALIFICACION_MAXIMA",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = KineCareSpacing.xs),
        )
    }
}

internal fun mensajeDeErrorEnvio(error: ErrorEnvioResena): String = when (error) {
    ErrorEnvioResena.SIN_SESION -> "Inicia sesión para dejar una reseña."
    ErrorEnvioResena.SIN_INTERNET -> "Sin conexión a internet. Revisa tu red e inténtalo de nuevo."
    ErrorEnvioResena.SIN_PERMISO ->
        "No puedes reseñar esta atención: aún no está completada o ya la reseñaste."
    ErrorEnvioResena.DESCONOCIDO -> "No pudimos enviar tu reseña. Inténtalo de nuevo."
}

@Preview(showBackground = true)
@Composable
private fun CrearResenaScreenPreview() {
    KineCareTheme {
        CrearResenaScreen(
            state = CrearResenaState(
                verificando = false,
                calificacion = 4,
                comentario = "Muy buena atención.",
            ),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun CrearResenaScreenErrorPreview() {
    KineCareTheme {
        CrearResenaScreen(
            state = CrearResenaState(verificando = false, calificacion = 5, error = ErrorEnvioResena.SIN_PERMISO),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun CrearResenaScreenYaResenadaPreview() {
    KineCareTheme {
        CrearResenaScreen(
            state = CrearResenaState(
                verificando = false,
                resenaExistente = ResenaPropia(calificacion = 5, comentario = "Excelente."),
            ),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun CrearResenaScreenErrorVerificacionPreview() {
    KineCareTheme {
        CrearResenaScreen(state = CrearResenaState(verificando = false, errorVerificacion = ResenaError.SIN_INTERNET))
    }
}
