package com.darjnest.kinecare.core.network.firebase.di

import com.darjnest.kinecare.core.common.data.repository.CertificacionRepository
import com.darjnest.kinecare.core.common.data.repository.ClienteRepository
import com.darjnest.kinecare.core.common.data.repository.ComisionRepository
import com.darjnest.kinecare.core.common.data.repository.DispositivoPushRepository
import com.darjnest.kinecare.core.common.data.repository.FotoPerfilRepository
import com.darjnest.kinecare.core.common.data.repository.ProfesionalRepository
import com.darjnest.kinecare.core.common.data.repository.ResenaRepository
import com.darjnest.kinecare.core.common.data.repository.ReservaRepository
import com.darjnest.kinecare.core.common.data.repository.ServicioRepository
import com.darjnest.kinecare.core.common.data.repository.UsuarioRepository
import com.darjnest.kinecare.core.network.firebase.repository.CertificacionRepositoryImpl
import com.darjnest.kinecare.core.network.firebase.repository.ClienteRepositoryImpl
import com.darjnest.kinecare.core.network.firebase.repository.ComisionRepositoryImpl
import com.darjnest.kinecare.core.network.firebase.repository.DispositivoPushRepositoryImpl
import com.darjnest.kinecare.core.network.firebase.repository.FotoPerfilRepositoryImpl
import com.darjnest.kinecare.core.network.firebase.repository.ProfesionalRepositoryImpl
import com.darjnest.kinecare.core.network.firebase.repository.ResenaRepositoryImpl
import com.darjnest.kinecare.core.network.firebase.repository.ReservaRepositoryImpl
import com.darjnest.kinecare.core.network.firebase.repository.ServicioRepositoryImpl
import com.darjnest.kinecare.core.network.firebase.repository.UsuarioRepositoryImpl
import com.darjnest.kinecare.core.network.firebase.storage.LectorDocumento
import com.darjnest.kinecare.core.network.firebase.storage.LectorDocumentoAndroid
import com.darjnest.kinecare.core.network.firebase.storage.LectorImagen
import com.darjnest.kinecare.core.network.firebase.storage.LectorImagenAndroid
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

    @Binds
    @Singleton
    abstract fun bindServicioRepository(impl: ServicioRepositoryImpl): ServicioRepository

    @Binds
    @Singleton
    abstract fun bindComisionRepository(impl: ComisionRepositoryImpl): ComisionRepository

    @Binds
    @Singleton
    abstract fun bindResenaRepository(impl: ResenaRepositoryImpl): ResenaRepository

    @Binds
    @Singleton
    abstract fun bindFotoPerfilRepository(impl: FotoPerfilRepositoryImpl): FotoPerfilRepository

    @Binds
    @Singleton
    abstract fun bindCertificacionRepository(impl: CertificacionRepositoryImpl): CertificacionRepository

    @Binds
    abstract fun bindLectorDocumento(impl: LectorDocumentoAndroid): LectorDocumento

    @Binds
    abstract fun bindLectorImagen(impl: LectorImagenAndroid): LectorImagen

    @Binds
    @Singleton
    abstract fun bindDispositivoPushRepository(impl: DispositivoPushRepositoryImpl): DispositivoPushRepository
}
