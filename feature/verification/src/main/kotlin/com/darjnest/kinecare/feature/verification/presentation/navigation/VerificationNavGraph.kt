package com.darjnest.kinecare.feature.verification.presentation.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.darjnest.kinecare.feature.verification.presentation.view.VerificationRoot

fun NavGraphBuilder.verificationGraph() {
    composable<VerificationRoute> {
        VerificationRoot()
    }
}
