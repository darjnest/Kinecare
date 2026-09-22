package com.darjnest.kinecare.feature.auth.presentation.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.darjnest.kinecare.core.common.domain.model.Usuario
import com.darjnest.kinecare.feature.auth.presentation.view.AuthRoot

fun NavGraphBuilder.authGraph(onSesionIniciada: (Usuario) -> Unit = {}) {
    composable<AuthRoute> {
        AuthRoot(onSesionIniciada = onSesionIniciada)
    }
}
