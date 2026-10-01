@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.darjnest.kinecare.feature.booking.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlin.time.Clock

/**
 * El reloj se inyecta (en vez de usar `Clock.System` directo en el
 * `ViewModel`) para que los tests fijen "ahora" y los horarios generados
 * sean deterministas. Si otra feature necesita lo mismo, este binding sube a
 * `:app` para no duplicarlo.
 */
@Module
@InstallIn(SingletonComponent::class)
object BookingModule {

    @Provides
    fun provideClock(): Clock = Clock.System
}
