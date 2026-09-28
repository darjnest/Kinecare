@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.darjnest.kinecare.core.network.firebase.repository

import com.darjnest.kinecare.core.common.data.error.ResenaError
import com.darjnest.kinecare.core.common.data.repository.ResenaRepository
import com.darjnest.kinecare.core.common.domain.model.Resena
import com.darjnest.kinecare.core.common.result.Result
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await
import kotlinx.datetime.Instant
import javax.inject.Inject

private const val COLECCION_RESENAS = "resenas"
private const val LIMITE_RESENAS = 100L

/**
 * Implementacion Firestore de `resenas/{resenaId}` (docs/DATA_MODEL.md).
 *
 * Requiere el indice compuesto `profesionalId` (ASC) + `fecha` (DESC), ya
 * declarado en `firestore.indexes.json`.
 */
class ResenaRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
) : ResenaRepository {

    override suspend fun obtenerPorProfesional(profesionalId: String): Result<List<Resena>, ResenaError> {
        return try {
            val documentos = firestore.collection(COLECCION_RESENAS)
                .whereEqualTo("profesionalId", profesionalId)
                .orderBy("fecha", Query.Direction.DESCENDING)
                .limit(LIMITE_RESENAS)
                .get()
                .await()
            Result.Success(documentos.documents.mapNotNull { it.aResenaONull() })
        } catch (e: Exception) {
            Result.Error(e.aResenaError())
        }
    }

    override suspend fun obtenerPorReserva(reservaId: String): Result<Resena?, ResenaError> {
        return try {
            val documento = firestore.collection(COLECCION_RESENAS).document(reservaId).get().await()
            Result.Success(documento.aResenaONull())
        } catch (e: Exception) {
            Result.Error(e.aResenaError())
        }
    }

    override suspend fun crear(resena: Resena): Result<Unit, ResenaError> {
        return try {
            val datos = mutableMapOf<String, Any>(
                "reservaId" to resena.reservaId,
                "clienteId" to resena.clienteId,
                "profesionalId" to resena.profesionalId,
                "calificacion" to resena.calificacion,
                "fecha" to FieldValue.serverTimestamp(),
            )
            resena.comentario?.let { datos["comentario"] = it }

            firestore.collection(COLECCION_RESENAS).document(resena.reservaId).set(datos).await()
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e.aResenaError())
        }
    }

    private fun Exception.aResenaError(): ResenaError = when {
        this is FirebaseNetworkException -> ResenaError.SIN_INTERNET
        this is FirebaseFirestoreException && code == FirebaseFirestoreException.Code.UNAVAILABLE ->
            ResenaError.SIN_INTERNET
        this is FirebaseFirestoreException && code == FirebaseFirestoreException.Code.PERMISSION_DENIED ->
            ResenaError.SIN_PERMISO
        else -> ResenaError.DESCONOCIDO
    }

    private fun DocumentSnapshot.aResenaONull(): Resena? {
        if (!exists()) return null
        val reservaId = getString("reservaId") ?: return null
        val clienteId = getString("clienteId") ?: return null
        val profesionalId = getString("profesionalId") ?: return null
        val calificacion = getLong("calificacion")?.toInt() ?: return null
        val fecha = getTimestamp("fecha")?.let { Instant.fromEpochMilliseconds(it.toDate().time) } ?: return null
        return Resena(
            id = id,
            reservaId = reservaId,
            clienteId = clienteId,
            profesionalId = profesionalId,
            calificacion = calificacion,
            comentario = getString("comentario"),
            fecha = fecha,
            respuestaProfesional = getString("respuestaProfesional"),
        )
    }
}
