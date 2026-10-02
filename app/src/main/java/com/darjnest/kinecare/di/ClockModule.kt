@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.darjnest.kinecare.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlin.time.Clock

/**
 * El reloj se inyecta (en vez de usar `Clock.System` directo en los
 * `ViewModel`) para que los tests fijen "ahora". Vive en `:app` porque lo
 * usan varias features (`:feature:booking`, `:feature:professional-panel`)
 * y un mismo binding declarado en dos modulos de feature chocaria en el
 * grafo de Hilt.
 */
@Module
@InstallIn(SingletonComponent::class)
object ClockModule {

    @Provides
    fun provideClock(): Clock = Clock.System
}
