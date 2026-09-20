package com.darjnest.kinecare.feature.professional_profile.presentation.viewmodel

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

data class ProfessionalProfileState(
    val cargando: Boolean = false,
)

sealed interface ProfessionalProfileAction

@HiltViewModel
class ProfessionalProfileViewModel @Inject constructor() : ViewModel() {

    private val _state = MutableStateFlow(ProfessionalProfileState())
    val state: StateFlow<ProfessionalProfileState> = _state.asStateFlow()

    fun onAction(action: ProfessionalProfileAction) {
        // TODO: implementar cuando la feature "Perfil profesional" salga de Fase 1 (docs/TASKS.md)
    }
}
