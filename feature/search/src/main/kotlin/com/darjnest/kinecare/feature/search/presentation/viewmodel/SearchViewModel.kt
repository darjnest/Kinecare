package com.darjnest.kinecare.feature.search.presentation.viewmodel

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

data class SearchState(
    val cargando: Boolean = false,
)

sealed interface SearchAction

@HiltViewModel
class SearchViewModel @Inject constructor() : ViewModel() {

    private val _state = MutableStateFlow(SearchState())
    val state: StateFlow<SearchState> = _state.asStateFlow()

    fun onAction(action: SearchAction) {
        // TODO: implementar cuando la feature "Búsqueda" salga de Fase 1 (docs/TASKS.md)
    }
}
