package com.darjnest.kinecare.feature.search.presentation.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.darjnest.kinecare.feature.search.presentation.view.SearchRoot

fun NavGraphBuilder.searchGraph() {
    composable<SearchRoute> {
        SearchRoot()
    }
}
