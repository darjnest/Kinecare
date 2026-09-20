package com.darjnest.kinecare.feature.auth.presentation.view

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
import com.darjnest.kinecare.feature.auth.presentation.viewmodel.AuthState
import com.darjnest.kinecare.feature.auth.presentation.viewmodel.AuthViewModel

@Composable
fun AuthRoot(
    modifier: Modifier = Modifier,
    viewModel: AuthViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    AuthScreen(state = state, modifier = modifier)
}

@Composable
fun AuthScreen(
    state: AuthState,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Autenticación — próximamente")
    }
}

@Preview(showBackground = true)
@Composable
private fun AuthScreenPreview() {
    KineCareTheme {
        AuthScreen(state = AuthState())
    }
}
