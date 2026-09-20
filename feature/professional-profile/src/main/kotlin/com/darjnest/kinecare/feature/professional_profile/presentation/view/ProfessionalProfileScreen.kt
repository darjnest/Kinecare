package com.darjnest.kinecare.feature.professional_profile.presentation.view

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
import com.darjnest.kinecare.feature.professional_profile.presentation.viewmodel.ProfessionalProfileState
import com.darjnest.kinecare.feature.professional_profile.presentation.viewmodel.ProfessionalProfileViewModel

@Composable
fun ProfessionalProfileRoot(
    modifier: Modifier = Modifier,
    viewModel: ProfessionalProfileViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ProfessionalProfileScreen(state = state, modifier = modifier)
}

@Composable
fun ProfessionalProfileScreen(
    state: ProfessionalProfileState,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Perfil profesional — próximamente")
    }
}

@Preview(showBackground = true)
@Composable
private fun ProfessionalProfileScreenPreview() {
    KineCareTheme {
        ProfessionalProfileScreen(state = ProfessionalProfileState())
    }
}
