@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.darjnest.kinecare.core.network.firebase.repository

import android.util.Log
import com.darjnest.kinecare.core.common.data.error.CertificacionError
import com.darjnest.kinecare.core.common.data.error.FotoPerfilError
import com.darjnest.kinecare.core.common.data.repository.CertificacionRepository
import com.darjnest.kinecare.core.common.domain.model.Certificacion
import com.darjnest.kinecare.core.common.domain.model.EstadoCertificacion
import com.darjnest.kinecare.core.common.result.Result
import com.darjnest.kinecare.core.network.firebase.storage.EXTENSIONES_DOCUMENTO
import com.darjnest.kinecare.core.network.firebase.storage.LectorDocumento
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import kotlinx.datetime.Instant
import javax.inject.Inject
import javax.inject.Provider

private const val COLECCION_PROFESIONALES = "profesionales"
private const val SUBCOLECCION_CERTIFICACIONES = "certificaciones"
private const val ETIQUETA = "Certificacion"
private const val LARGO_MAXIMO_NOMBRE = 120

/**
 * Sube la certificacion a Storage (`profesionales/{uid}/certificaciones/{id}.{ext}`) y crea su
 * registro `EN_REVISION` en Firestore. [storage] llega por `Provider` por la misma razon que en
 * `FotoPerfilRepositoryImpl`: sin bucket el SDK lanza al obtenerlo.
 */
class CertificacionRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val storage: Provider<FirebaseStorage>,
    private val lector: LectorDocumento,
) : CertificacionRepository {

    override suspend fun obtener(profesionalId: String): Result<List<Certificacion>, CertificacionError> {
        return try {
            val documentos = certificaciones(profesionalId).orderBy("creadoEn", Query.Direction.DESCENDING).get().await()
            Result.Success(documentos.documents.mapNotNull { it.aCertificacionONull() })
        } catch (e: CancellationException) {
            throw e
        } catch (e: FirebaseNetworkException) {
            Result.Error(CertificacionError.SIN_INTERNET)
        } catch (e: Exception) {
            Log.w(ETIQUETA, "No se pudo leer las certificaciones", e)
            Result.Error(CertificacionError.DESCONOCIDO)
        }
    }

    override suspend fun subir(
        profesionalId: String,
        archivoUri: String,
        nombre: String?,
    ): Result<Unit, CertificacionError> {
        val documento = when (val lectura = lector.leer(archivoUri)) {
            is Result.Success -> lectura.data
            is Result.Error -> return Result.Error(lectura.error)
        }
        return try {
            val referenciaRegistro = certificaciones(profesionalId).document()
            val extension = EXTENSIONES_DOCUMENTO.getValue(documento.tipoContenido)
            val ruta = "$COLECCION_PROFESIONALES/$profesionalId/$SUBCOLECCION_CERTIFICACIONES/${referenciaRegistro.id}.$extension"
            val metadatos = StorageMetadata.Builder().setContentType(documento.tipoContenido).build()
            storage.get().reference.child(ruta).putBytes(documento.bytes, metadatos).await()
            referenciaRegistro.set(
                mapOf(
                    "nombre" to (nombre?.trim()?.takeIf { it.isNotEmpty() } ?: documento.nombreSugerido).take(LARGO_MAXIMO_NOMBRE),
                    "archivoPath" to ruta,
                    "tipoContenido" to documento.tipoContenido,
                    "tamanoBytes" to documento.bytes.size.toLong(),
                    "estado" to EstadoCertificacion.EN_REVISION.name,
                    "creadoEn" to FieldValue.serverTimestamp(),
                ),
            ).await()
            Result.Success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val error = e.aCertificacionError()
            Log.w(ETIQUETA, "Fallo al subir la certificacion ($error)", e)
            Result.Error(error)
        }
    }

    private fun certificaciones(profesionalId: String) =
        firestore.collection(COLECCION_PROFESIONALES).document(profesionalId).collection(SUBCOLECCION_CERTIFICACIONES)

    private fun DocumentSnapshot.aCertificacionONull(): Certificacion? {
        if (!exists()) return null
        return Certificacion(
            id = id,
            nombre = getString("nombre") ?: return null,
            estado = runCatching { EstadoCertificacion.valueOf(getString("estado") ?: "") }
                .getOrDefault(EstadoCertificacion.EN_REVISION),
            tipoContenido = getString("tipoContenido").orEmpty(),
            tamanoBytes = getLong("tamanoBytes") ?: 0L,
            creadoEn = getTimestamp("creadoEn")?.let { Instant.fromEpochMilliseconds(it.toDate().time) },
            motivoRechazo = getString("motivoRechazo"),
        )
    }
}

/**
 * Traduce un fallo de Storage/Firestore a un error de dominio; los de Storage se interpretan igual
 * que en la foto de perfil ([aFotoPerfilError]).
 */
internal fun Throwable.aCertificacionError(): CertificacionError = when (aFotoPerfilError()) {
    FotoPerfilError.SIN_INTERNET -> CertificacionError.SIN_INTERNET
    FotoPerfilError.SIN_PERMISO -> CertificacionError.SIN_PERMISO
    FotoPerfilError.ALMACENAMIENTO_NO_DISPONIBLE -> CertificacionError.ALMACENAMIENTO_NO_DISPONIBLE
    else -> if (this is com.google.firebase.firestore.FirebaseFirestoreException &&
        code == com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED
    ) {
        CertificacionError.SIN_PERMISO
    } else {
        CertificacionError.DESCONOCIDO
    }
}
