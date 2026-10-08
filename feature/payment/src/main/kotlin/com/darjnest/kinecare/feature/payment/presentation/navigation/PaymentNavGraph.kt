package com.darjnest.kinecare.feature.payment.presentation.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.navDeepLink
import androidx.navigation.toRoute
import com.darjnest.kinecare.feature.payment.presentation.view.ConectarMercadoPagoRoot
import com.darjnest.kinecare.feature.payment.presentation.view.MercadoPagoConectadoRoot
import com.darjnest.kinecare.feature.payment.presentation.view.MercadoPagoErrorScreen
import com.darjnest.kinecare.feature.payment.presentation.view.PagoResultadoRoot
import com.darjnest.kinecare.feature.payment.presentation.view.PaymentRoot

/**
 * Pagar una reserva (cliente) y vincular la cuenta de Mercado Pago (profesional).
 * Los destinos de salida los resuelve `:app` por callback (las features no se
 * dependen entre si); la navegacion interna del flujo de vinculacion usa
 * [navController].
 *
 * Las tres pantallas de retorno cuelgan de deep links `kinecare://...`: el
 * Custom Tab termina en ellos y `:app` los entrega a Navigation.
 */
fun NavGraphBuilder.paymentGraph(
    navController: NavController,
    onVolver: () -> Unit = {},
    onIrAMisCitas: () -> Unit = {},
    onReintentarPago: () -> Unit = {},
    onIrAlPanelProfesional: () -> Unit = {},
    onIniciarSesion: () -> Unit = {},
) {
    composable<PaymentRoute> {
        PaymentRoot(onVolver = onVolver)
    }
    composable<PagoResultadoRoute>(
        deepLinks = listOf(navDeepLink<PagoResultadoRoute>(basePath = PAGO_RESULTADO_DEEP_LINK)),
    ) {
        PagoResultadoRoot(
            onIrAMisCitas = onIrAMisCitas,
            onReintentarPago = onReintentarPago,
            onIniciarSesion = onIniciarSesion,
        )
    }
    composable<ConectarMercadoPagoRoute> {
        ConectarMercadoPagoRoot(onVolver = onVolver, onIniciarSesion = onIniciarSesion)
    }
    composable<MercadoPagoConectadoRoute>(
        deepLinks = listOf(navDeepLink<MercadoPagoConectadoRoute>(basePath = MP_CONECTADO_DEEP_LINK)),
    ) {
        MercadoPagoConectadoRoot(
            onIrAlPanel = onIrAlPanelProfesional,
            onVolverAConectar = { navController.irAConectarMercadoPago() },
            onIniciarSesion = onIniciarSesion,
        )
    }
    composable<MercadoPagoErrorRoute>(
        deepLinks = listOf(navDeepLink<MercadoPagoErrorRoute>(basePath = MP_ERROR_DEEP_LINK)),
    ) { entrada ->
        MercadoPagoErrorScreen(
            motivo = entrada.toRoute<MercadoPagoErrorRoute>().motivo,
            onReintentar = { navController.irAConectarMercadoPago() },
            onIrAlPanel = onIrAlPanelProfesional,
        )
    }
}

/** Abre la pantalla de vinculacion en lugar de la de resultado desde la que se pidio reintentar. */
private fun NavController.irAConectarMercadoPago() {
    val actual = currentDestination?.id
    navigate(ConectarMercadoPagoRoute) {
        if (actual != null) popUpTo(actual) { inclusive = true }
    }
}
