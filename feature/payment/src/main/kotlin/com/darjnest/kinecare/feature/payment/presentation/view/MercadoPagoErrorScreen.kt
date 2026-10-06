package com.darjnest.kinecare.feature.payment.presentation.view

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.darjnest.kinecare.core.designsystem.components.bar.KineCareTopBar
import com.darjnest.kinecare.core.designsystem.components.button.KineCarePrimaryButton
import com.darjnest.kinecare.core.designsystem.components.button.KineCareSecondaryButton
import com.darjnest.kinecare.core.designsystem.components.feedback.KineCareStatusMessage
import com.darjnest.kinecare.core.designsystem.components.label.BadgeTono
import com.darjnest.kinecare.core.designsystem.theme.KineCareTheme
import com.darjnest.kinecare.feature.payment.presentation.util.mensajeMotivoVinculacion

/**
 * Vuelta del OAuth con error. Sin estado propio ni `ViewModel`: solo traduce
 * [motivo] (que viene de un deep link y puede ser nulo o desconocido) a un mensaje.
 */
@Composable
fun MercadoPagoErrorScreen(
    motivo: String?,
    onReintentar: () -> Unit = {},
    onIrAlPanel: () -> Unit = {},
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
            KineCareStatusMessage(
                titulo = "No pudimos vincular tu cuenta",
                mensaje = mensajeMotivoVinculacion(motivo),
                tono = BadgeTono.ERROR,
                icono = Icons.Filled.Error,
            ) {
                KineCarePrimaryButton(text = "Volver a intentar", onClick = onReintentar)
                KineCareSecondaryButton(text = "Volver al panel", onClick = onIrAlPanel)
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun MercadoPagoErrorScreenPreview() {
    KineCareTheme { MercadoPagoErrorScreen(motivo = "cancelado") }
}
