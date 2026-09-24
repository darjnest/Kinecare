package com.darjnest.kinecare.feature.search.presentation.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.darjnest.kinecare.feature.search.presentation.view.SearchRoot

fun NavGraphBuilder.searchGraph(
    onCerrarSesion: () -> Unit = {},
    onIrAMisCitas: () -> Unit = {},
    onIrAFavoritos: () -> Unit = {},
    onIrAMiPerfil: () -> Unit = {},
) {
    composable<SearchRoute> {
        SearchRoot(
            onCerrarSesion = onCerrarSesion,
            onIrAMisCitas = onIrAMisCitas,
            onIrAFavoritos = onIrAFavoritos,
            onIrAMiPerfil = onIrAMiPerfil,
        )
    }
}
