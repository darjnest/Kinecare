package com.darjnest.kinecare.feature.booking.presentation.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.darjnest.kinecare.feature.booking.presentation.view.BookingRoot

fun NavGraphBuilder.bookingGraph() {
    composable<BookingRoute> {
        BookingRoot()
    }
}
