package com.darjnest.kinecare.feature.professional_panel.presentation.view

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
import com.darjnest.kinecare.feature.professional_panel.presentation.viewmodel.ProfessionalPanelState
import com.darjnest.kinecare.feature.professional_panel.presentation.viewmodel.ProfessionalPanelViewModel

@Composable
fun ProfessionalPanelRoot(
    modifier: Modifier = Modifier,
    viewModel: ProfessionalPanelViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ProfessionalPanelScreen(state = state, modifier = modifier)
}

@Composable
fun ProfessionalPanelScreen(
    state: ProfessionalPanelState,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Panel profesional — próximamente")
    }
}

@Preview(showBackground = true)
@Composable
private fun ProfessionalPanelScreenPreview() {
    KineCareTheme {
        ProfessionalPanelScreen(state = ProfessionalPanelState())
    }
}
