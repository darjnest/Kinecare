package com.darjnest.kinecare.feature.search.di

import android.content.Context
import com.darjnest.kinecare.feature.search.data.repository.UbicacionRepository
import com.darjnest.kinecare.feature.search.data.repository_impl.UbicacionRepositoryImpl
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * `ProfesionalRepository` ya no se bindea aca: vive en `:core:network`
 * (`FirestoreRepositoryModule`) desde que se movio a `:core:common` +
 * `:core:network` (ver docs/ARCHITECTURE.md) para que `:feature:client-panel`
 * tambien pueda resolver un profesional por id sin duplicar la feature.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class SearchModule {

    @Binds
    @Singleton
    abstract fun bindUbicacionRepository(impl: UbicacionRepositoryImpl): UbicacionRepository

    companion object {
        @Provides
        @Singleton
        fun provideFusedLocationProviderClient(@ApplicationContext context: Context): FusedLocationProviderClient =
            LocationServices.getFusedLocationProviderClient(context)
    }
}
