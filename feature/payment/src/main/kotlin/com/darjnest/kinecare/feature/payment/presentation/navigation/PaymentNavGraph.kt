package com.darjnest.kinecare.feature.payment.presentation.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.darjnest.kinecare.feature.payment.presentation.view.PaymentRoot

fun NavGraphBuilder.paymentGraph() {
    composable<PaymentRoute> {
        PaymentRoot()
    }
}
