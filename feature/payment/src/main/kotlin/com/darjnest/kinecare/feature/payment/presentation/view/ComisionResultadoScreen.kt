package com.darjnest.kinecare.feature.payment.presentation.view

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.darjnest.kinecare.core.designsystem.components.bar.KineCareTopBar
import com.darjnest.kinecare.core.designsystem.components.button.KineCarePrimaryButton
import com.darjnest.kinecare.core.designsystem.components.feedback.KineCareStatusMessage
import com.darjnest.kinecare.core.designsystem.components.label.BadgeTono
import com.darjnest.kinecare.core.designsystem.theme.KineCareTheme

/**
 * Vuelta de Mercado Pago tras pagar comisiones. No afirma que el pago salio bien: el resultado llega
 * por webhook y el saldo real se ve en Liquidaciones y Finanzas, que se recarga al abrirse.
 */
@Composable
fun ComisionResultadoRoot(
    modifier: Modifier = Modifier,
    onIrAlPanel: () -> Unit = {},
) {
    ComisionResultadoScreen(onIrAlPanel = onIrAlPanel, modifier = modifier)
}

@Composable
fun ComisionResultadoScreen(
    onIrAlPanel: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = { KineCareTopBar(titulo = "Pagar comisiones") },
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
                titulo = "Estamos confirmando tu pago",
                mensaje = "Cuando Mercado Pago lo confirme, tus comisiones aparecerán como pagadas en " +
                    "Liquidaciones y Finanzas. Si cerraste el pago sin terminar, puedes intentarlo otra vez.",
                tono = BadgeTono.INFO,
            ) {
                KineCarePrimaryButton(text = "Volver al panel", onClick = onIrAlPanel)
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ComisionResultadoScreenPreview() {
    KineCareTheme { ComisionResultadoScreen() }
}
