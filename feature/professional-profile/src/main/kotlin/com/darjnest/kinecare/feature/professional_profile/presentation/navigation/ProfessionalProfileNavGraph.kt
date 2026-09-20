package com.darjnest.kinecare.feature.professional_profile.presentation.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.darjnest.kinecare.feature.professional_profile.presentation.view.ProfessionalProfileRoot

fun NavGraphBuilder.professional_profileGraph() {
    composable<ProfessionalProfileRoute> {
        ProfessionalProfileRoot()
    }
}
