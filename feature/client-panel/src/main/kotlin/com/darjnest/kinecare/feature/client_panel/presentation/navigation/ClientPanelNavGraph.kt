package com.darjnest.kinecare.feature.client_panel.presentation.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.darjnest.kinecare.feature.client_panel.presentation.view.FavoritosRoot
import com.darjnest.kinecare.feature.client_panel.presentation.view.InformacionPersonalClienteRoot
import com.darjnest.kinecare.feature.client_panel.presentation.view.MiPerfilClienteRoot
import com.darjnest.kinecare.feature.client_panel.presentation.view.MisCitasRoot

fun NavGraphBuilder.client_panelGraph(
    navController: NavController,
    onIrAExplorar: () -> Unit = {},
    onIrAMisCitas: () -> Unit = {},
    onIrAFavoritos: () -> Unit = {},
    onIrAMiPerfil: () -> Unit = {},
    onCerrarSesion: () -> Unit = {},
) {
    composable<MisCitasRoute> {
        MisCitasRoot(
            onIrAExplorar = onIrAExplorar,
            onIrAFavoritos = onIrAFavoritos,
            onIrAMiPerfil = onIrAMiPerfil,
        )
    }
    composable<FavoritosRoute> {
        FavoritosRoot(
            onIrAExplorar = onIrAExplorar,
            onIrAMisCitas = onIrAMisCitas,
            onIrAMiPerfil = onIrAMiPerfil,
        )
    }
    composable<MiPerfilClienteRoute> {
        MiPerfilClienteRoot(
            onIrAExplorar = onIrAExplorar,
            onIrAMisCitas = onIrAMisCitas,
            onIrAFavoritos = onIrAFavoritos,
            onEditarInformacionPersonal = { navController.navigate(InformacionPersonalClienteRoute) },
            onCerrarSesion = onCerrarSesion,
        )
    }
    composable<InformacionPersonalClienteRoute> {
        InformacionPersonalClienteRoot(onVolver = navController::popBackStack)
    }
}
