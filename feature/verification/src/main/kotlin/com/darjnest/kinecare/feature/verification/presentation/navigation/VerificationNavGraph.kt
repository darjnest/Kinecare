package com.darjnest.kinecare.feature.verification.presentation.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.navDeepLink
import com.darjnest.kinecare.feature.verification.presentation.view.VerificacionResultadoRoot
import com.darjnest.kinecare.feature.verification.presentation.view.VerificationRoot

/**
 * Verificar la identidad del profesional. Los destinos de salida los resuelve
 * `:app` por callback (las features no se dependen entre si).
 *
 * [VerificacionResultadoRoute] cuelga del deep link `kinecare://verificacion/resultado`:
 * el Custom Tab termina en el y `:app` lo entrega a Navigation.
 */
fun NavGraphBuilder.verificationGraph(
    onVolver: () -> Unit = {},
    onListo: () -> Unit = {},
    onIniciarSesion: () -> Unit = {},
) {
    composable<VerificationRoute> {
        VerificationRoot(onVolver = onVolver, onIniciarSesion = onIniciarSesion)
    }
    composable<VerificacionResultadoRoute>(
        deepLinks = listOf(
            navDeepLink<VerificacionResultadoRoute>(basePath = VERIFICACION_RESULTADO_DEEP_LINK),
        ),
    ) {
        VerificacionResultadoRoot(onListo = onListo, onIniciarSesion = onIniciarSesion)
    }
}
