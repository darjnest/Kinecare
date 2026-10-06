package com.darjnest.kinecare.core.designsystem.components.bar

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.darjnest.kinecare.core.designsystem.theme.KineCareTheme

/**
 * Barra superior con titulo centrado y, si [onVolver] no es nulo, flecha de
 * retroceso. Las pantallas de un flujo corto (pago, vinculacion de cuenta) la
 * comparten en vez de armar cada una su propio encabezado.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KineCareTopBar(
    titulo: String,
    modifier: Modifier = Modifier,
    onVolver: (() -> Unit)? = null,
) {
    CenterAlignedTopAppBar(
        title = { Text(titulo) },
        modifier = modifier,
        navigationIcon = {
            if (onVolver != null) {
                IconButton(onClick = onVolver) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                }
            }
        },
    )
}

@Preview(showBackground = true)
@Composable
private fun KineCareTopBarPreview() {
    KineCareTheme {
        KineCareTopBar(titulo = "Pagar reserva", onVolver = {})
    }
}
