package com.darjnest.kinecare.feature.payment.presentation.view

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
import com.darjnest.kinecare.feature.payment.presentation.viewmodel.PaymentState
import com.darjnest.kinecare.feature.payment.presentation.viewmodel.PaymentViewModel

@Composable
fun PaymentRoot(
    modifier: Modifier = Modifier,
    viewModel: PaymentViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    PaymentScreen(state = state, modifier = modifier)
}

@Composable
fun PaymentScreen(
    state: PaymentState,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Pago — próximamente")
    }
}

@Preview(showBackground = true)
@Composable
private fun PaymentScreenPreview() {
    KineCareTheme {
        PaymentScreen(state = PaymentState())
    }
}
