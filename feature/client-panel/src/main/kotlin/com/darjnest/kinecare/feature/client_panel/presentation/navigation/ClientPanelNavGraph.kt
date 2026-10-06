package com.darjnest.kinecare.feature.client_panel.presentation.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.darjnest.kinecare.feature.client_panel.presentation.view.FavoritosRoot
import com.darjnest.kinecare.feature.client_panel.presentation.view.InformacionPersonalClienteRoot
import com.darjnest.kinecare.feature.client_panel.presentation.view.MiPerfilClienteRoot
import com.darjnest.kinecare.feature.client_panel.presentation.view.MisCitasRoot
import com.darjnest.kinecare.feature.client_panel.presentation.view.ReportarProblemaRoot

fun NavGraphBuilder.client_panelGraph(
    navController: NavController,
    onIrAExplorar: () -> Unit = {},
    onIrAMisCitas: () -> Unit = {},
    onIrAFavoritos: () -> Unit = {},
    onIrAMiPerfil: () -> Unit = {},
    onCerrarSesion: () -> Unit = {},
    onProfesionalClick: (String) -> Unit = {},
    onDejarResena: (reservaId: String, profesionalId: String) -> Unit = { _, _ -> },
    onPagar: (reservaId: String, titulo: String, montoClp: Long) -> Unit = { _, _, _ -> },
) {
    composable<MisCitasRoute> {
        MisCitasRoot(
            onDejarResena = onDejarResena,
            onPagar = onPagar,
            // Pantalla de esta misma feature: navegacion interna, sin pasar por `:app`.
            onReportarProblema = { reservaId, profesionalId ->
                navController.navigate(ReportarProblemaRoute(reservaId, profesionalId))
            },
            onIrAExplorar = onIrAExplorar,
            onIrAFavoritos = onIrAFavoritos,
            onIrAMiPerfil = onIrAMiPerfil,
        )
    }
    composable<FavoritosRoute> {
        FavoritosRoot(
            onProfesionalClick = onProfesionalClick,
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
    composable<ReportarProblemaRoute> {
        ReportarProblemaRoot(onVolver = navController::popBackStack)
    }
}
