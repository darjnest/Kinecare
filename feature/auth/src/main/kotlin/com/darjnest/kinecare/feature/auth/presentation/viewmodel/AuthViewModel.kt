package com.darjnest.kinecare.feature.auth.presentation.viewmodel

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

data class AuthState(
    val cargando: Boolean = false,
)

sealed interface AuthAction

@HiltViewModel
class AuthViewModel @Inject constructor() : ViewModel() {

    private val _state = MutableStateFlow(AuthState())
    val state: StateFlow<AuthState> = _state.asStateFlow()

    fun onAction(action: AuthAction) {
        // TODO: implementar cuando la feature "Autenticación" salga de Fase 1 (docs/TASKS.md)
    }
}
