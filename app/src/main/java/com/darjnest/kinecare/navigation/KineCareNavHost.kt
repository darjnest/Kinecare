package com.darjnest.kinecare.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import com.darjnest.kinecare.feature.auth.presentation.navigation.AuthRoute
import com.darjnest.kinecare.feature.auth.presentation.navigation.authGraph
import com.darjnest.kinecare.feature.booking.presentation.navigation.bookingGraph
import com.darjnest.kinecare.feature.payment.presentation.navigation.paymentGraph
import com.darjnest.kinecare.feature.professional_panel.presentation.navigation.professional_panelGraph
import com.darjnest.kinecare.feature.professional_profile.presentation.navigation.professional_profileGraph
import com.darjnest.kinecare.feature.reviews.presentation.navigation.reviewsGraph
import com.darjnest.kinecare.feature.search.presentation.navigation.searchGraph
import com.darjnest.kinecare.feature.verification.presentation.navigation.verificationGraph

/**
 * Grafo raiz. Hoy es plano (todas las features cuelgan directo de Auth)
 * porque ninguna feature tiene logica real todavia (docs/TASKS.md Fase 1).
 * La separacion clienteGraph/profesionalGraph segun el rol del Usuario
 * autenticado se arma en Fase 2, cuando exista el estado de sesion real.
 */
@Composable
fun KineCareNavHost(modifier: Modifier = Modifier) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = AuthRoute,
        modifier = modifier,
    ) {
        authGraph()
        searchGraph()
        professional_profileGraph()
        bookingGraph()
        paymentGraph()
        verificationGraph()
        professional_panelGraph()
        reviewsGraph()
    }
}
