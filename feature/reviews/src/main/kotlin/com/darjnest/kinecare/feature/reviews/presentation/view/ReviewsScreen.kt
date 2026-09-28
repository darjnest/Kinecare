package com.darjnest.kinecare.feature.reviews.presentation.view

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.darjnest.kinecare.core.common.data.error.ResenaError
import com.darjnest.kinecare.core.designsystem.components.card.KineCareCard
import com.darjnest.kinecare.core.designsystem.components.loading.KineCareFullScreenLoading
import com.darjnest.kinecare.core.designsystem.theme.InicioDorado
import com.darjnest.kinecare.core.designsystem.theme.KineCareSpacing
import com.darjnest.kinecare.core.designsystem.theme.KineCareTheme
import com.darjnest.kinecare.feature.reviews.presentation.util.formatearCalificacion
import com.darjnest.kinecare.feature.reviews.presentation.util.formatearFecha
import com.darjnest.kinecare.feature.reviews.presentation.util.textoResenas
import com.darjnest.kinecare.feature.reviews.presentation.viewmodel.ResenaItem
import com.darjnest.kinecare.feature.reviews.presentation.viewmodel.ResumenResenas
import com.darjnest.kinecare.feature.reviews.presentation.viewmodel.ReviewsAction
import com.darjnest.kinecare.feature.reviews.presentation.viewmodel.ReviewsState
import com.darjnest.kinecare.feature.reviews.presentation.viewmodel.ReviewsViewModel
import kotlin.math.roundToInt

@Composable
fun ReviewsRoot(
    modifier: Modifier = Modifier,
    viewModel: ReviewsViewModel = hiltViewModel(),
    onVolver: () -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ReviewsScreen(
        state = state,
        onAction = { accion ->
            // Volver atras es navegacion, no estado del ViewModel: el Root la
            // resuelve directo contra el NavGraph.
            if (accion is ReviewsAction.VolverAtras) onVolver() else viewModel.onAction(accion)
        },
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewsScreen(
    state: ReviewsState,
    onAction: (ReviewsAction) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Reseñas") },
                navigationIcon = {
                    IconButton(onClick = { onAction(ReviewsAction.VolverAtras) }) {
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
            state.cargando -> KineCareFullScreenLoading(modifier = contenidoModifier)
            state.error != null -> EstadoErrorResenas(
                error = state.error,
                onReintentar = { onAction(ReviewsAction.Reintentar) },
                modifier = contenidoModifier,
            )
            state.resenas.isEmpty() -> EstadoSinResenas(modifier = contenidoModifier)
            else -> ListadoResenas(state = state, modifier = contenidoModifier)
        }
    }
}

@Composable
private fun EstadoSinResenas(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(KineCareSpacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Aún no hay reseñas",
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Text(
            text = "Cuando un cliente califique una atención completada, su reseña aparecerá aquí.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = KineCareSpacing.s),
        )
    }
}

@Composable
private fun ListadoResenas(
    state: ReviewsState,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(KineCareSpacing.l),
        verticalArrangement = Arrangement.spacedBy(KineCareSpacing.m),
    ) {
        state.resumen?.let { resumen ->
            item(key = "resumen") { ResumenCalificaciones(resumen = resumen) }
        }
        items(items = state.resenas, key = { it.id }) { resena ->
            TarjetaResena(resena = resena)
        }
    }
}

@Composable
private fun ResumenCalificaciones(resumen: ResumenResenas) {
    KineCareCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = formatearCalificacion(resumen.promedio),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                )
                EstrellasCalificacion(calificacion = resumen.promedio.roundToInt())
                Text(
                    text = textoResenas(resumen.total),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = KineCareSpacing.xs),
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = KineCareSpacing.l),
                verticalArrangement = Arrangement.spacedBy(KineCareSpacing.xs),
            ) {
                resumen.distribucion.forEach { (estrellas, cantidad) ->
                    FilaDistribucion(
                        estrellas = estrellas,
                        cantidad = cantidad,
                        total = resumen.total,
                    )
                }
            }
        }
    }
}

@Composable
private fun FilaDistribucion(
    estrellas: Int,
    cantidad: Int,
    total: Int,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription = "$cantidad de $total reseñas con $estrellas estrellas"
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.width(KineCareSpacing.etiquetaDistribucion),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = "$estrellas", style = MaterialTheme.typography.labelMedium)
            Icon(
                imageVector = Icons.Filled.Star,
                contentDescription = null,
                tint = InicioDorado,
                modifier = Modifier
                    .padding(start = KineCareSpacing.xs)
                    .size(KineCareSpacing.l),
            )
        }
        LinearProgressIndicator(
            progress = { if (total == 0) 0f else cantidad.toFloat() / total },
            modifier = Modifier
                .weight(1f)
                .height(KineCareSpacing.barraDistribucion)
                .clip(MaterialTheme.shapes.small),
        )
        Text(
            text = "$cantidad",
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.End,
            modifier = Modifier
                .width(KineCareSpacing.etiquetaDistribucion)
                .padding(start = KineCareSpacing.s),
        )
    }
}

@Composable
private fun TarjetaResena(resena: ResenaItem) {
    KineCareCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = resena.autor,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            EstrellasCalificacion(calificacion = resena.calificacion)
        }
        Text(
            text = formatearFecha(resena.fechaMillis),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = KineCareSpacing.xs),
        )
        if (resena.comentario != null) {
            Text(
                text = resena.comentario,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = KineCareSpacing.s),
            )
        }
        if (resena.respuestaProfesional != null) {
            RespuestaProfesional(texto = resena.respuestaProfesional)
        }
    }
}

@Composable
private fun RespuestaProfesional(texto: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = KineCareSpacing.xl, top = KineCareSpacing.m)
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(KineCareSpacing.m),
        verticalArrangement = Arrangement.spacedBy(KineCareSpacing.xs),
    ) {
        Text(
            text = "Respuesta del profesional",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text = texto, style = MaterialTheme.typography.bodyMedium)
    }
}

private val resumenDePrueba = ResumenResenas(
    promedio = 4.5,
    total = 4,
    distribucion = mapOf(5 to 2, 4 to 2, 3 to 0, 2 to 0, 1 to 0),
)

private val resenasDePrueba = listOf(
    ResenaItem(
        id = "r1",
        autor = "María P.",
        calificacion = 5,
        comentario = "Excelente atención, muy profesional y puntual.",
        fechaMillis = 1_772_000_000_000L,
        respuestaProfesional = "¡Gracias por tu confianza, María!",
    ),
    ResenaItem(
        id = "r2",
        autor = "Cliente",
        calificacion = 4,
        comentario = null,
        fechaMillis = 1_771_000_000_000L,
        respuestaProfesional = null,
    ),
)

@Preview(showBackground = true)
@Composable
private fun ReviewsScreenPreview() {
    KineCareTheme {
        ReviewsScreen(
            state = ReviewsState(cargando = false, resumen = resumenDePrueba, resenas = resenasDePrueba),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ReviewsScreenEmptyPreview() {
    KineCareTheme {
        ReviewsScreen(state = ReviewsState(cargando = false))
    }
}

@Preview(showBackground = true)
@Composable
private fun ReviewsScreenErrorPreview() {
    KineCareTheme {
        ReviewsScreen(state = ReviewsState(cargando = false, error = ResenaError.SIN_INTERNET))
    }
}

@Preview(showBackground = true)
@Composable
private fun ReviewsScreenLoadingPreview() {
    KineCareTheme {
        ReviewsScreen(state = ReviewsState(cargando = true))
    }
}
