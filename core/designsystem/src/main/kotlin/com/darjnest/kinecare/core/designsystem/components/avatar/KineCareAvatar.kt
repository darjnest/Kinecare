package com.darjnest.kinecare.core.designsystem.components.avatar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import coil3.compose.SubcomposeAsyncImage
import com.darjnest.kinecare.core.designsystem.theme.KineCareSpacing
import com.darjnest.kinecare.core.designsystem.theme.KineCareTheme

/**
 * Avatar circular: la foto de [fotoUrl] (cargada con Coil) o, si no hay, no se pudo cargar o aun
 * esta descargando, las [iniciales] (y un icono de persona si tampoco hay iniciales). Con
 * [cargando] muestra un indicador de progreso encima (subida de una foto nueva).
 */
@Composable
fun KineCareAvatar(
    iniciales: String,
    fotoUrl: String?,
    modifier: Modifier = Modifier,
    tamano: Dp = KineCareSpacing.avatarGrande,
    colorFondo: Color = MaterialTheme.colorScheme.primaryContainer,
    colorContenido: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    cargando: Boolean = false,
    contentDescription: String? = null,
) {
    Box(
        modifier = modifier
            .size(tamano)
            .clip(CircleShape)
            .background(colorFondo),
        contentAlignment = Alignment.Center,
    ) {
        val respaldo: @Composable () -> Unit = { Iniciales(iniciales = iniciales, color = colorContenido, tamano = tamano) }
        if (fotoUrl.isNullOrBlank()) {
            respaldo()
        } else {
            SubcomposeAsyncImage(
                model = fotoUrl,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                loading = { respaldo() },
                error = { respaldo() },
            )
        }
        if (cargando) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = ALPHA_VELO_CARGA)),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(tamano / FRACCION_INDICADOR),
                    color = MaterialTheme.colorScheme.surface,
                    strokeWidth = KineCareSpacing.xs / 2,
                )
            }
        }
    }
}

@Composable
private fun Iniciales(iniciales: String, color: Color, tamano: Dp) {
    if (iniciales.isNotBlank()) {
        Text(
            text = iniciales,
            style = if (tamano >= KineCareSpacing.avatarGrande) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = color,
        )
    } else {
        Icon(Icons.Filled.Person, contentDescription = null, tint = color, modifier = Modifier.size(tamano / 2))
    }
}

private const val ALPHA_VELO_CARGA = 0.45f
private const val FRACCION_INDICADOR = 2

@Preview(showBackground = true)
@Composable
private fun KineCareAvatarPreview() {
    KineCareTheme {
        Box {
            KineCareAvatar(iniciales = "AS", fotoUrl = null)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun KineCareAvatarCargandoPreview() {
    KineCareTheme {
        Box {
            KineCareAvatar(iniciales = "AS", fotoUrl = null, cargando = true)
        }
    }
}
