package com.darjnest.kinecare.core.network.firebase.repository

import android.util.Log
import com.darjnest.kinecare.core.common.data.error.FotoPerfilError
import com.darjnest.kinecare.core.common.data.repository.FotoPerfilRepository
import com.darjnest.kinecare.core.common.domain.model.RolUsuario
import com.darjnest.kinecare.core.common.result.Result
import com.darjnest.kinecare.core.network.firebase.storage.LectorImagen
import com.darjnest.kinecare.core.network.firebase.storage.conVersion
import com.darjnest.kinecare.core.network.firebase.storage.rutaFotoPerfil
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageException
import com.google.firebase.storage.StorageMetadata
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import java.io.IOException
import javax.inject.Inject
import javax.inject.Provider

private const val TIPO_CONTENIDO_JPEG = "image/jpeg"
private const val ETIQUETA = "FotoPerfil"

/**
 * Sube la foto de perfil a Firebase Storage con el tipo `image/jpeg` que exige `storage.rules`.
 * [storage] llega por `Provider` porque el SDK lanza al obtener el bucket si el proyecto no lo
 * tiene configurado: eso se traduce a `ALMACENAMIENTO_NO_DISPONIBLE` en vez de romper la app.
 */
class FotoPerfilRepositoryImpl @Inject constructor(
    private val storage: Provider<FirebaseStorage>,
    private val lector: LectorImagen,
) : FotoPerfilRepository {

    /** Reloj de la version de la URL; sobreescribible en pruebas. */
    internal var ahoraMillis: () -> Long = System::currentTimeMillis

    override suspend fun subir(
        usuarioId: String,
        rol: RolUsuario,
        imagenUri: String,
    ): Result<String, FotoPerfilError> {
        val jpeg = when (val lectura = lector.leerComoJpeg(imagenUri)) {
            is Result.Success -> lectura.data
            is Result.Error -> return Result.Error(lectura.error)
        }
        return try {
            val referencia = storage.get().reference.child(rutaFotoPerfil(usuarioId, rol))
            val metadatos = StorageMetadata.Builder().setContentType(TIPO_CONTENIDO_JPEG).build()
            referencia.putBytes(jpeg, metadatos).await()
            val url = referencia.downloadUrl.await().toString()
            Result.Success(conVersion(url, ahoraMillis()))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val error = e.aFotoPerfilError()
            Log.w(ETIQUETA, "Fallo al subir la foto de perfil ($error)", e)
            Result.Error(error)
        }
    }
}

/**
 * Traduce un fallo de Storage a un error de dominio. Subir a un bucket que no existe (produccion
 * en Spark) responde 404/"objeto no encontrado": en una subida eso solo puede significar que el
 * almacenamiento no esta disponible. `RETRY_LIMIT_EXCEEDED` es lo que reporta el SDK al agotar los
 * reintentos sin red.
 */
internal fun Throwable.aFotoPerfilError(): FotoPerfilError = when (this) {
    is StorageException -> when (errorCode) {
        StorageException.ERROR_OBJECT_NOT_FOUND,
        StorageException.ERROR_BUCKET_NOT_FOUND,
        StorageException.ERROR_PROJECT_NOT_FOUND,
        StorageException.ERROR_QUOTA_EXCEEDED,
        -> FotoPerfilError.ALMACENAMIENTO_NO_DISPONIBLE
        StorageException.ERROR_NOT_AUTHENTICATED,
        StorageException.ERROR_NOT_AUTHORIZED,
        -> FotoPerfilError.SIN_PERMISO
        StorageException.ERROR_RETRY_LIMIT_EXCEEDED -> FotoPerfilError.SIN_INTERNET
        else -> if (cause is IOException) FotoPerfilError.SIN_INTERNET else FotoPerfilError.DESCONOCIDO
    }
    is FirebaseNetworkException, is IOException -> FotoPerfilError.SIN_INTERNET
    // `FirebaseStorage.getInstance()` falla asi cuando el proyecto no tiene bucket configurado.
    is IllegalStateException, is IllegalArgumentException -> FotoPerfilError.ALMACENAMIENTO_NO_DISPONIBLE
    else -> FotoPerfilError.DESCONOCIDO
}
