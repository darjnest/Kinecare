package com.darjnest.kinecare.feature.payment.di

import com.darjnest.kinecare.feature.payment.data.repository.PagoRepository
import com.darjnest.kinecare.feature.payment.data.repository_impl.PagoRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class PaymentModule {

    @Binds
    @Singleton
    abstract fun bindPagoRepository(impl: PagoRepositoryImpl): PagoRepository
}
