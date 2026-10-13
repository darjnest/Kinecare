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
    onPrevisualizarPerfil: (String) -> Unit = {},
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
            onAbrirAgenda = { navController.navigate(DisponibilidadYHorariosRoute) },
            onConectarMercadoPago = onConectarMercadoPago,
        )
    }
    composable<MiPerfilProfesionalRoute> {
        MiPerfilProfesionalRoot(
            onVolver = navController::popBackStack,
            onVerResenas = onVerResenas,
            onPrevisualizarPerfil = onPrevisualizarPerfil,
            onEditarCredenciales = { navController.navigate(DocumentosYValidacionRoute) },
            onCerrarSesion = onCerrarSesion,
            onIrAInicio = navController::irAInicioPanel,
            onIrASolicitudes = { navController.irADestinoDeBarra(SolicitudesDeAtencionRoute) },
            onIrAAgenda = { navController.irADestinoDeBarra(DisponibilidadYHorariosRoute) },
        )
    }
    composable<ServiciosYTarifasRoute> {
        ServiciosYTarifasRoot(onVolver = navController::popBackStack)
    }
    composable<DisponibilidadYHorariosRoute> {
        DisponibilidadYHorariosRoot(onVolver = navController::popBackStack)
    }
    composable<SolicitudesDeAtencionRoute> {
        SolicitudesDeAtencionRoot(
            onVolver = navController::popBackStack,
            onIrAInicio = navController::irAInicioPanel,
            onIrAAgenda = { navController.irADestinoDeBarra(DisponibilidadYHorariosRoute) },
            onIrAPerfil = { navController.irADestinoDeBarra(MiPerfilProfesionalRoute) },
        )
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

/**
 * Inicio de la barra inferior: vuelve al panel que ya esta en la pila en vez
 * de apilar otro; si no estuviera (p. ej. tras un deep link), lo abre.
 */
private fun NavController.irAInicioPanel() {
    if (!popBackStack(ProfessionalPanelRoute, inclusive = false)) {
        navigate(ProfessionalPanelRoute) { launchSingleTop = true }
    }
}

/**
 * Destino de la barra inferior: se apila sobre el panel (no sobre la pantalla
 * anterior) para que la pila no crezca al saltar entre pestanas y "atras"
 * vuelva siempre al panel.
 */
private fun NavController.irADestinoDeBarra(ruta: Any) {
    navigate(ruta) {
        popUpTo(ProfessionalPanelRoute) { inclusive = false }
        launchSingleTop = true
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
