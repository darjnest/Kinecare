package com.darjnest.kinecare.core.network.firebase.di

import com.darjnest.kinecare.core.common.data.repository.ClienteRepository
import com.darjnest.kinecare.core.common.data.repository.ProfesionalRepository
import com.darjnest.kinecare.core.common.data.repository.ReservaRepository
import com.darjnest.kinecare.core.common.data.repository.UsuarioRepository
import com.darjnest.kinecare.core.network.firebase.repository.ClienteRepositoryImpl
import com.darjnest.kinecare.core.network.firebase.repository.ProfesionalRepositoryImpl
import com.darjnest.kinecare.core.network.firebase.repository.ReservaRepositoryImpl
import com.darjnest.kinecare.core.network.firebase.repository.UsuarioRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Repositorios de Firestore compartidos por 2+ features (`:feature:search`,
 * `:feature:client-panel`) — viven aca y no dentro de una feature porque
 * las features nunca se dependen entre si (docs/ARCHITECTURE.md). Hilt los
 * agrega igual al grafo final de `:app` aunque ninguna feature los
 * referencie directamente en su propio `@Module` (mismo mecanismo que ya
 * usan `FirebaseModule`/`NetworkModule` en este modulo).
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class FirestoreRepositoryModule {

    @Binds
    @Singleton
    abstract fun bindUsuarioRepository(impl: UsuarioRepositoryImpl): UsuarioRepository

    @Binds
    @Singleton
    abstract fun bindClienteRepository(impl: ClienteRepositoryImpl): ClienteRepository

    @Binds
    @Singleton
    abstract fun bindProfesionalRepository(impl: ProfesionalRepositoryImpl): ProfesionalRepository

    @Binds
    @Singleton
    abstract fun bindReservaRepository(impl: ReservaRepositoryImpl): ReservaRepository
}
