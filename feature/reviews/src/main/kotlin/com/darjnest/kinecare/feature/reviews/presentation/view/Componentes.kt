package com.darjnest.kinecare.feature.reviews.presentation.view

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import com.darjnest.kinecare.core.common.data.error.ResenaError
import com.darjnest.kinecare.core.designsystem.components.button.KineCarePrimaryButton
import com.darjnest.kinecare.core.designsystem.theme.InicioDorado
import com.darjnest.kinecare.core.designsystem.theme.KineCareSpacing
import com.darjnest.kinecare.feature.reviews.presentation.util.textoEstrellas

/** Fila de 5 estrellas de solo lectura (las que superan [calificacion] van vacias), una sola descripcion para lectores de pantalla. */
@Composable
internal fun EstrellasCalificacion(
    calificacion: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.clearAndSetSemantics { contentDescription = textoEstrellas(calificacion) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        (1..5).forEach { posicion ->
            Icon(
                imageVector = if (posicion <= calificacion) Icons.Filled.Star else Icons.Filled.StarBorder,
                contentDescription = null,
                tint = if (posicion <= calificacion) InicioDorado else MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(KineCareSpacing.icono),
            )
        }
    }
}

/** Mensaje de error de carga, con "Reintentar" (compartido por el listado y la comprobacion del formulario). */
@Composable
internal fun EstadoErrorResenas(
    error: ResenaError,
    onReintentar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(KineCareSpacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = mensajeDeError(error),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        Box(modifier = Modifier.padding(top = KineCareSpacing.l)) {
            KineCarePrimaryButton(text = "Reintentar", onClick = onReintentar)
        }
    }
}

internal fun mensajeDeError(error: ResenaError): String = when (error) {
    ResenaError.SIN_INTERNET -> "Sin conexión a internet. Revisa tu red e inténtalo de nuevo."
    ResenaError.SIN_PERMISO -> "No tienes permiso para ver estas reseñas. Inicia sesión e inténtalo de nuevo."
    ResenaError.DESCONOCIDO -> "Ocurrió un error inesperado al cargar las reseñas."
}
