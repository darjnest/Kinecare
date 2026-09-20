package com.darjnest.kinecare.feature.professional_panel.presentation.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.darjnest.kinecare.feature.professional_panel.presentation.view.ProfessionalPanelRoot

fun NavGraphBuilder.professional_panelGraph() {
    composable<ProfessionalPanelRoute> {
        ProfessionalPanelRoot()
    }
}
