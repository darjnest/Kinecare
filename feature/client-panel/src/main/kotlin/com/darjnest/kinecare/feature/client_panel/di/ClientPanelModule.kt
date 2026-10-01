package com.darjnest.kinecare.feature.client_panel.di

import com.darjnest.kinecare.feature.client_panel.data.repository.ReporteProblemaRepository
import com.darjnest.kinecare.feature.client_panel.data.repository_impl.ReporteProblemaRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ClientPanelModule {

    @Binds
    @Singleton
    abstract fun bindReporteProblemaRepository(impl: ReporteProblemaRepositoryImpl): ReporteProblemaRepository
}
