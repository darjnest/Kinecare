package com.darjnest.kinecare.feature.search.presentation.view

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
import com.darjnest.kinecare.feature.search.presentation.viewmodel.SearchState
import com.darjnest.kinecare.feature.search.presentation.viewmodel.SearchViewModel

@Composable
fun SearchRoot(
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SearchScreen(state = state, modifier = modifier)
}

@Composable
fun SearchScreen(
    state: SearchState,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Búsqueda — próximamente")
    }
}

@Preview(showBackground = true)
@Composable
private fun SearchScreenPreview() {
    KineCareTheme {
        SearchScreen(state = SearchState())
    }
}
