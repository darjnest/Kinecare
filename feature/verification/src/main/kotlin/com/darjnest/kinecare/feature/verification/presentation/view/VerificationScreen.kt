package com.darjnest.kinecare.feature.verification.presentation.view

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
import com.darjnest.kinecare.feature.verification.presentation.viewmodel.VerificationState
import com.darjnest.kinecare.feature.verification.presentation.viewmodel.VerificationViewModel

@Composable
fun VerificationRoot(
    modifier: Modifier = Modifier,
    viewModel: VerificationViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    VerificationScreen(state = state, modifier = modifier)
}

@Composable
fun VerificationScreen(
    state: VerificationState,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Verificación — próximamente")
    }
}

@Preview(showBackground = true)
@Composable
private fun VerificationScreenPreview() {
    KineCareTheme {
        VerificationScreen(state = VerificationState())
    }
}
