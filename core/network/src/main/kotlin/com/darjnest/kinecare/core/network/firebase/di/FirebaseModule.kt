package com.darjnest.kinecare.core.network.firebase.di

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.storage.FirebaseStorage
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * SDK de Firebase usado directo (no via Retrofit) - ver
 * docs/ARCHITECTURE.md#backend-firebase. Cloud Functions siguen yendo por
 * NetworkModule/Retrofit.
 */
@Module
@InstallIn(SingletonComponent::class)
object FirebaseModule {

    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth = FirebaseAuth.getInstance()

    @Provides
    @Singleton
    fun provideFirebaseFirestore(): FirebaseFirestore = FirebaseFirestore.getInstance()

    /**
     * Sin `@Singleton` y consumido por `Provider` a proposito: `FirebaseStorage.getInstance()` falla
     * si el proyecto no tiene bucket configurado, y eso no debe tumbar el grafo de Hilt al abrir la
     * app (produccion no tiene bucket mientras siga en plan Spark): el repositorio lo pide al
     * momento de subir y degrada con un mensaje.
     */
    @Provides
    fun provideFirebaseStorage(): FirebaseStorage = FirebaseStorage.getInstance()

    @Provides
    @Singleton
    fun provideFirebaseMessaging(): FirebaseMessaging = FirebaseMessaging.getInstance()
}
