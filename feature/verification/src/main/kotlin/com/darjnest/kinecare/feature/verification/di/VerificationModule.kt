package com.darjnest.kinecare.feature.verification.di

import com.darjnest.kinecare.feature.verification.data.repository.VerificacionRepository
import com.darjnest.kinecare.feature.verification.data.repository_impl.VerificacionRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class VerificationModule {

    @Binds
    @Singleton
    abstract fun bindVerificacionRepository(impl: VerificacionRepositoryImpl): VerificacionRepository
}
