package com.darjnest.kinecare.core.network.di

import com.darjnest.kinecare.core.network.functions.CloudFunctionsApi
import com.darjnest.kinecare.core.network.security.PinningCallFactory
import com.darjnest.kinecare.core.network.security.PinningOverrideRemoto
import com.darjnest.kinecare.core.network.security.PinningPolicy
import com.darjnest.kinecare.core.network.security.crearCertificatePinnerCloudFunctions
import com.darjnest.kinecare.core.network.urlBaseCloudFunctions
import com.google.firebase.FirebaseApp
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import javax.inject.Singleton

/**
 * Cliente HTTP para llamar a las Cloud Functions (pagos, verificacion, etc.).
 * El resto de los datos se leen via SDK de Firebase directo, no por aqui
 * (ver docs/ARCHITECTURE.md).
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        return OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            // Pinning de las llamadas a las Cloud Functions (pagos, verificacion, reservas): ver
            // CertificatePins.kt y docs/ARCHITECTURE.md#seguridad.
            .certificatePinner(crearCertificatePinnerCloudFunctions())
            .build()
    }

    /** Pinning activo salvo un override firmado y vigente de Remote Config (ver `PinningOverride.kt`). */
    @Provides
    @Singleton
    fun providePinningPolicy(overrideRemoto: PinningOverrideRemoto): PinningPolicy =
        PinningPolicy(valorOverride = overrideRemoto::valor)

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient, pinningPolicy: PinningPolicy, json: Json): Retrofit {
        val contentType = "application/json".toMediaType()
        val projectId = requireNotNull(FirebaseApp.getInstance().options.projectId) {
            "google-services.json no define project_id"
        }
        return Retrofit.Builder()
            .baseUrl(urlBaseCloudFunctions(projectId))
            // Elige por llamada entre el cliente con pinning y uno sin el, segun la politica.
            .callFactory(PinningCallFactory(okHttpClient, pinningPolicy))
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
    }

    @Provides
    @Singleton
    fun provideCloudFunctionsApi(retrofit: Retrofit): CloudFunctionsApi =
        retrofit.create(CloudFunctionsApi::class.java)
}
