package com.darjnest.kinecare.feature.professional_panel.presentation.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.darjnest.kinecare.feature.professional_panel.presentation.view.DisponibilidadYHorariosRoot
import com.darjnest.kinecare.feature.professional_panel.presentation.view.DocumentosYValidacionRoot
import com.darjnest.kinecare.feature.professional_panel.presentation.view.LiquidacionesYFinanzasRoot
import com.darjnest.kinecare.feature.professional_panel.presentation.view.MiPerfilProfesionalRoot
import com.darjnest.kinecare.feature.professional_panel.presentation.view.ProfessionalPanelRoot
import com.darjnest.kinecare.feature.professional_panel.presentation.view.ServiciosYTarifasRoot
import com.darjnest.kinecare.feature.professional_panel.presentation.view.SolicitudesDeAtencionRoot
import com.darjnest.kinecare.feature.professional_panel.presentation.viewmodel.AccesoGestionId

fun NavGraphBuilder.professional_panelGraph(
    navController: NavController,
    onCerrarSesion: () -> Unit = {},
    onVerResenas: (String) -> Unit = {},
    onConectarMercadoPago: () -> Unit = {},
    onVerificarIdentidad: () -> Unit = {},
) {
    composable<ProfessionalPanelRoute> {
        ProfessionalPanelRoot(
            onAbrirAccesoGestion = { accesoId ->
                rutaParaAccesoGestion(accesoId)?.let(navController::navigate)
            },
            onAbrirSolicitudes = { navController.navigate(SolicitudesDeAtencionRoute) },
            onAbrirMiPerfil = { navController.navigate(MiPerfilProfesionalRoute) },
            onConectarMercadoPago = onConectarMercadoPago,
        )
    }
    composable<MiPerfilProfesionalRoute> {
        MiPerfilProfesionalRoot(
            onVolver = navController::popBackStack,
            onVerResenas = onVerResenas,
            onCerrarSesion = onCerrarSesion,
        )
    }
    composable<ServiciosYTarifasRoute> {
        ServiciosYTarifasRoot(onVolver = navController::popBackStack)
    }
    composable<DisponibilidadYHorariosRoute> {
        DisponibilidadYHorariosRoot(onVolver = navController::popBackStack)
    }
    composable<SolicitudesDeAtencionRoute> {
        SolicitudesDeAtencionRoot(onVolver = navController::popBackStack)
    }
    composable<DocumentosYValidacionRoute> {
        DocumentosYValidacionRoot(
            onVolver = navController::popBackStack,
            onVerificarIdentidad = onVerificarIdentidad,
        )
    }
    composable<LiquidacionesYFinanzasRoute> {
        LiquidacionesYFinanzasRoot(onVolver = navController::popBackStack)
    }
}

/** Traduce el id estable de un [AccesoGestion] a su ruta de destino. */
private fun rutaParaAccesoGestion(accesoId: String): Any? = when (accesoId) {
    AccesoGestionId.MI_PERFIL -> MiPerfilProfesionalRoute
    AccesoGestionId.SERVICIOS -> ServiciosYTarifasRoute
    AccesoGestionId.DISPONIBILIDAD -> DisponibilidadYHorariosRoute
    AccesoGestionId.SOLICITUDES -> SolicitudesDeAtencionRoute
    AccesoGestionId.LIQUIDACIONES -> LiquidacionesYFinanzasRoute
    AccesoGestionId.DOCUMENTOS -> DocumentosYValidacionRoute
    else -> null
}
