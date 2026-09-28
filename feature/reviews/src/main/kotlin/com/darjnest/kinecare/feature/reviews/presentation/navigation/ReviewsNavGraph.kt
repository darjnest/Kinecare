package com.darjnest.kinecare.feature.reviews.presentation.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.darjnest.kinecare.feature.reviews.presentation.view.CrearResenaRoot
import com.darjnest.kinecare.feature.reviews.presentation.view.ReviewsRoot

fun NavGraphBuilder.reviewsGraph(
    onVolver: () -> Unit = {},
) {
    composable<ReviewsRoute> {
        ReviewsRoot(onVolver = onVolver)
    }
    composable<CrearResenaRoute> {
        CrearResenaRoot(onVolver = onVolver)
    }
}
