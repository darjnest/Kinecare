package com.darjnest.kinecare.feature.professional_panel.presentation.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.darjnest.kinecare.feature.professional_panel.presentation.view.DisponibilidadYHorariosRoot
import com.darjnest.kinecare.feature.professional_panel.presentation.view.DocumentosYValidacionRoot
import com.darjnest.kinecare.feature.professional_panel.presentation.view.LiquidacionesYFinanzasRoot
import com.darjnest.kinecare.feature.professional_panel.presentation.view.MiPerfilProfesionalRoot
import com.darjnest.kinecare.feature.professional_panel.presentation.view.ProfessionalPanelRoot
import com.darjnest.kinecare.feature.professional_panel.presentation.view.ServiciosYTarifasRoot
import com.darjnest.kinecare.feature.professional_panel.presentation.view.SolicitudesDeAtencionRoot

fun NavGraphBuilder.professional_panelGraph() {
    composable<ProfessionalPanelRoute> {
        ProfessionalPanelRoot()
    }
    composable<DocumentosYValidacionRoute> {
        DocumentosYValidacionRoot()
    }
    composable<LiquidacionesYFinanzasRoute> {
        LiquidacionesYFinanzasRoot()
    }
    composable<MiPerfilProfesionalRoute> {
        MiPerfilProfesionalRoot()
    }
    composable<ServiciosYTarifasRoute> {
        ServiciosYTarifasRoot()
    }
    composable<DisponibilidadYHorariosRoute> {
        DisponibilidadYHorariosRoot()
    }
    composable<SolicitudesDeAtencionRoute> {
        SolicitudesDeAtencionRoot()
    }
}
