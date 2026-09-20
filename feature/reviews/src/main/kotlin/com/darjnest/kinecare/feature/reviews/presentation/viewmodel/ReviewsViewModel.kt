package com.darjnest.kinecare.feature.reviews.presentation.viewmodel

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

data class ReviewsState(
    val cargando: Boolean = false,
)

sealed interface ReviewsAction

@HiltViewModel
class ReviewsViewModel @Inject constructor() : ViewModel() {

    private val _state = MutableStateFlow(ReviewsState())
    val state: StateFlow<ReviewsState> = _state.asStateFlow()

    fun onAction(action: ReviewsAction) {
        // TODO: implementar cuando la feature "Reseñas" salga de Fase 1 (docs/TASKS.md)
    }
}
