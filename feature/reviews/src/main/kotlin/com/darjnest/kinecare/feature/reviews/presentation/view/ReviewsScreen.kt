package com.darjnest.kinecare.feature.reviews.presentation.view

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.darjnest.kinecare.core.designsystem.theme.KineCareTheme
import com.darjnest.kinecare.feature.reviews.presentation.viewmodel.ReviewsState
import com.darjnest.kinecare.feature.reviews.presentation.viewmodel.ReviewsViewModel

@Composable
fun ReviewsRoot(
    modifier: Modifier = Modifier,
    viewModel: ReviewsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ReviewsScreen(state = state, modifier = modifier)
}

@Composable
fun ReviewsScreen(
    state: ReviewsState,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Reseñas — próximamente")
    }
}

@Preview(showBackground = true)
@Composable
private fun ReviewsScreenPreview() {
    KineCareTheme {
        ReviewsScreen(state = ReviewsState())
    }
}
