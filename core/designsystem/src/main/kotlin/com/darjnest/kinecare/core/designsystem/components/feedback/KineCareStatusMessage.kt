package com.darjnest.kinecare.core.designsystem.components.feedback

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.darjnest.kinecare.core.designsystem.components.label.BadgeTono
import com.darjnest.kinecare.core.designsystem.theme.KineCareSpacing
import com.darjnest.kinecare.core.designsystem.theme.KineCareTheme

/**
 * Mensaje de estado a pantalla completa: icono en circulo de color segun
 * [tono] (o spinner si [icono] es nulo), titulo, texto y, debajo, las
 * [acciones] (botones). Lo usan los resultados de un flujo (pago aprobado,
 * cuenta vinculada, error) para verse igual entre si.
 */
@Composable
fun KineCareStatusMessage(
    titulo: String,
    mensaje: String,
    tono: BadgeTono,
    modifier: Modifier = Modifier,
    icono: ImageVector? = null,
    acciones: @Composable ColumnScope.() -> Unit = {},
) {
    val (fondo, contenido) = coloresPara(tono)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(KineCareSpacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(KineCareSpacing.circuloEstado)
                .clip(CircleShape)
                .background(fondo),
            contentAlignment = Alignment.Center,
        ) {
            if (icono != null) {
                Icon(
                    imageVector = icono,
                    contentDescription = null,
                    tint = contenido,
                    modifier = Modifier.size(KineCareSpacing.iconoEstado),
                )
            } else {
                CircularProgressIndicator(color = contenido)
            }
        }
        Spacer(modifier = Modifier.height(KineCareSpacing.xl))
        Text(
            text = titulo,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(KineCareSpacing.s))
        Text(
            text = mensaje,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(KineCareSpacing.xl))
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(KineCareSpacing.s),
        ) {
            acciones()
        }
    }
}

@Composable
private fun coloresPara(tono: BadgeTono): Pair<Color, Color> = when (tono) {
    BadgeTono.EXITO -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
    BadgeTono.INFO -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
    BadgeTono.NEUTRO -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    BadgeTono.ERROR -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.error
}

@Preview(showBackground = true)
@Composable
private fun KineCareStatusMessagePreview() {
    KineCareTheme {
        KineCareStatusMessage(
            titulo = "Pago aprobado",
            mensaje = "Tu reserva quedó pagada.",
            tono = BadgeTono.EXITO,
            icono = Icons.Filled.CheckCircle,
        )
    }
}
