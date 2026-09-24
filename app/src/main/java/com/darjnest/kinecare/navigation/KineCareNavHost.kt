package com.darjnest.kinecare.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import com.darjnest.kinecare.core.common.domain.model.RolUsuario
import com.darjnest.kinecare.feature.auth.presentation.navigation.AuthRoute
import com.darjnest.kinecare.feature.auth.presentation.navigation.authGraph
import com.darjnest.kinecare.feature.auth.presentation.viewmodel.AuthAction
import com.darjnest.kinecare.feature.auth.presentation.viewmodel.AuthViewModel
import com.darjnest.kinecare.feature.booking.presentation.navigation.bookingGraph
import com.darjnest.kinecare.feature.client_panel.presentation.navigation.FavoritosRoute
import com.darjnest.kinecare.feature.client_panel.presentation.navigation.MiPerfilClienteRoute
import com.darjnest.kinecare.feature.client_panel.presentation.navigation.MisCitasRoute
import com.darjnest.kinecare.feature.client_panel.presentation.navigation.client_panelGraph
import com.darjnest.kinecare.feature.payment.presentation.navigation.paymentGraph
import com.darjnest.kinecare.feature.professional_panel.presentation.navigation.ProfessionalPanelRoute
import com.darjnest.kinecare.feature.professional_panel.presentation.navigation.professional_panelGraph
import com.darjnest.kinecare.feature.professional_profile.presentation.navigation.professional_profileGraph
import com.darjnest.kinecare.feature.reviews.presentation.navigation.reviewsGraph
import com.darjnest.kinecare.feature.search.presentation.navigation.SearchRoute
import com.darjnest.kinecare.feature.search.presentation.navigation.searchGraph
import com.darjnest.kinecare.feature.verification.presentation.navigation.verificationGraph

/**
 * Grafo raiz. Hoy es plano (todas las features cuelgan directo de Auth)
 * porque ninguna feature tiene logica real todavia (docs/TASKS.md Fase 1).
 * Al iniciar sesion o registrarse, el rol del Usuario autenticado decide
 * el home (SearchRoute para Cliente, ProfessionalPanelRoute para
 * Profesional) y AuthRoute sale del back stack.
 */
@Composable
fun KineCareNavHost(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val authViewModel: AuthViewModel = hiltViewModel()

    NavHost(
        navController = navController,
        startDestination = AuthRoute,
        modifier = modifier,
    ) {
        authGraph(
            onSesionIniciada = { usuario ->
                val destino = when (usuario.rol) {
                    RolUsuario.CLIENTE -> SearchRoute
                    RolUsuario.PROFESIONAL -> ProfessionalPanelRoute
                }
                navController.navigate(destino) {
                    popUpTo(AuthRoute) { inclusive = true }
                }
            },
        )
        val onCerrarSesionCliente: () -> Unit = {
            authViewModel.onAction(AuthAction.CerrarSesion)
            navController.navigate(AuthRoute) {
                popUpTo(SearchRoute) { inclusive = true }
            }
        }
        searchGraph(
            onCerrarSesion = onCerrarSesionCliente,
            onIrAMisCitas = { navController.navigate(MisCitasRoute) { launchSingleTop = true } },
            onIrAFavoritos = { navController.navigate(FavoritosRoute) { launchSingleTop = true } },
            onIrAMiPerfil = { navController.navigate(MiPerfilClienteRoute) { launchSingleTop = true } },
        )
        client_panelGraph(
            onIrAExplorar = { navController.navigate(SearchRoute) { launchSingleTop = true } },
            onIrAMisCitas = { navController.navigate(MisCitasRoute) { launchSingleTop = true } },
            onIrAFavoritos = { navController.navigate(FavoritosRoute) { launchSingleTop = true } },
            onIrAMiPerfil = { navController.navigate(MiPerfilClienteRoute) { launchSingleTop = true } },
            onCerrarSesion = onCerrarSesionCliente,
        )
        val onCerrarSesionProfesional: () -> Unit = {
            authViewModel.onAction(AuthAction.CerrarSesion)
            navController.navigate(AuthRoute) {
                popUpTo(ProfessionalPanelRoute) { inclusive = true }
            }
        }
        professional_profileGraph()
        bookingGraph()
        paymentGraph()
        verificationGraph()
        professional_panelGraph(navController, onCerrarSesion = onCerrarSesionProfesional)
        reviewsGraph()
    }
}
