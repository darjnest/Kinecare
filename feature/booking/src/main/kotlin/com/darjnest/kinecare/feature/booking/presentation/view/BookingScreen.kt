package com.darjnest.kinecare.feature.booking.presentation.view

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
import com.darjnest.kinecare.feature.booking.presentation.viewmodel.BookingState
import com.darjnest.kinecare.feature.booking.presentation.viewmodel.BookingViewModel

@Composable
fun BookingRoot(
    modifier: Modifier = Modifier,
    viewModel: BookingViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    BookingScreen(state = state, modifier = modifier)
}

@Composable
fun BookingScreen(
    state: BookingState,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Reserva — próximamente")
    }
}

@Preview(showBackground = true)
@Composable
private fun BookingScreenPreview() {
    KineCareTheme {
        BookingScreen(state = BookingState())
    }
}
