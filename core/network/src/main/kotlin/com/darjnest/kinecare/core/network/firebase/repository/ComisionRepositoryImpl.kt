@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.darjnest.kinecare.core.network.firebase.repository

import com.darjnest.kinecare.core.common.data.error.ComisionError
import com.darjnest.kinecare.core.common.data.repository.ComisionRepository
import com.darjnest.kinecare.core.common.domain.model.ComisionPendiente
import com.darjnest.kinecare.core.common.domain.model.SaldoComision
import com.darjnest.kinecare.core.common.result.Result
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import kotlinx.datetime.Instant
import javax.inject.Inject

private const val COLECCION_COMISIONES = "comisionesPendientes"
private const val ESTADO_PENDIENTE = "PENDIENTE"

/**
 * Implementacion Firestore de `comisionesPendientes` (docs/DATA_MODEL.md).
 * Dos igualdades (`profesionalId` + `estado`): no necesita indice compuesto.
 * firestore.rules solo deja leer al profesional dueño.
 */
class ComisionRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
) : ComisionRepository {

    override suspend fun obtenerSaldo(profesionalId: String): Result<SaldoComision, ComisionError> {
        return try {
            val documentos = firestore.collection(COLECCION_COMISIONES)
                .whereEqualTo("profesionalId", profesionalId)
                .whereEqualTo("estado", ESTADO_PENDIENTE)
                .get()
                .await()
            val pendientes = documentos.documents
                .mapNotNull { it.aComisionONull() }
                .sortedByDescending { it.creadoEn }
            Result.Success(SaldoComision(pendientes))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.Error(e.aComisionError())
        }
    }

    private fun Exception.aComisionError(): ComisionError = when {
        this is FirebaseNetworkException -> ComisionError.SIN_INTERNET
        this is FirebaseFirestoreException && code == FirebaseFirestoreException.Code.UNAVAILABLE ->
            ComisionError.SIN_INTERNET
        this is FirebaseFirestoreException && code == FirebaseFirestoreException.Code.PERMISSION_DENIED ->
            ComisionError.SIN_PERMISO
        else -> ComisionError.DESCONOCIDO
    }

    private fun DocumentSnapshot.aComisionONull(): ComisionPendiente? {
        if (!exists()) return null
        val monto = getLong("monto") ?: return null
        return ComisionPendiente(
            reservaId = getString("reservaId") ?: id,
            monto = monto,
            creadoEn = getTimestamp("creadoEn")?.let { Instant.fromEpochMilliseconds(it.toDate().time) },
        )
    }
}
