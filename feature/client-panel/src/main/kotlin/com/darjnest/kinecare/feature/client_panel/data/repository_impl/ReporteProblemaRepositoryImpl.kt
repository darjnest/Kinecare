@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.darjnest.kinecare.feature.client_panel.data.repository_impl

import com.darjnest.kinecare.core.common.result.Result
import com.darjnest.kinecare.feature.client_panel.data.repository.ReporteProblemaRepository
import com.darjnest.kinecare.feature.client_panel.domain.EstadoReporte
import com.darjnest.kinecare.feature.client_panel.domain.MotivoReporte
import com.darjnest.kinecare.feature.client_panel.domain.ReporteProblema
import com.darjnest.kinecare.feature.client_panel.domain.ReporteProblemaError
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.tasks.await
import kotlinx.datetime.Instant
import javax.inject.Inject

private const val COLECCION_REPORTES = "reportesProblema"

class ReporteProblemaRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
) : ReporteProblemaRepository {

    override suspend fun obtenerPorCliente(clienteId: String): Result<List<ReporteProblema>, ReporteProblemaError> {
        return try {
            val documentos = firestore.collection(COLECCION_REPORTES)
                .whereEqualTo("clienteId", clienteId)
                .get()
                .await()
            Result.Success(documentos.documents.mapNotNull { it.aReporteONull() })
        } catch (e: Exception) {
            Result.Error(e.aReporteError())
        }
    }

    override suspend fun obtenerPorReserva(reservaId: String): Result<ReporteProblema?, ReporteProblemaError> {
        return try {
            val documento = firestore.collection(COLECCION_REPORTES).document(reservaId).get().await()
            Result.Success(documento.aReporteONull())
        } catch (e: Exception) {
            Result.Error(e.aReporteError())
        }
    }

    override suspend fun crear(reporte: ReporteProblema): Result<Unit, ReporteProblemaError> {
        return try {
            val datos = mapOf(
                "reservaId" to reporte.reservaId,
                "clienteId" to reporte.clienteId,
                "profesionalId" to reporte.profesionalId,
                "motivo" to reporte.motivo.name,
                "descripcion" to reporte.descripcion,
                "estado" to EstadoReporte.ABIERTO.name,
                "fecha" to FieldValue.serverTimestamp(),
            )
            firestore.collection(COLECCION_REPORTES).document(reporte.reservaId).set(datos).await()
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e.aReporteError())
        }
    }

    private fun Exception.aReporteError(): ReporteProblemaError = when {
        this is FirebaseNetworkException -> ReporteProblemaError.SIN_INTERNET
        this is FirebaseFirestoreException && code == FirebaseFirestoreException.Code.UNAVAILABLE ->
            ReporteProblemaError.SIN_INTERNET
        this is FirebaseFirestoreException && code == FirebaseFirestoreException.Code.PERMISSION_DENIED ->
            ReporteProblemaError.SIN_PERMISO
        else -> ReporteProblemaError.DESCONOCIDO
    }

    private fun DocumentSnapshot.aReporteONull(): ReporteProblema? {
        if (!exists()) return null
        val reservaId = getString("reservaId") ?: return null
        val clienteId = getString("clienteId") ?: return null
        val profesionalId = getString("profesionalId") ?: return null
        val motivo = getString("motivo")?.let { valor -> MotivoReporte.entries.firstOrNull { it.name == valor } }
            ?: return null
        val descripcion = getString("descripcion") ?: return null
        // Un estado que esta version no conoce (lo escribe soporte) se muestra como recibido.
        val estado = getString("estado")?.let { valor -> EstadoReporte.entries.firstOrNull { it.name == valor } }
            ?: EstadoReporte.ABIERTO
        val fecha = getTimestamp("fecha")?.let { Instant.fromEpochMilliseconds(it.toDate().time) } ?: return null
        return ReporteProblema(
            reservaId = reservaId,
            clienteId = clienteId,
            profesionalId = profesionalId,
            motivo = motivo,
            descripcion = descripcion,
            estado = estado,
            fecha = fecha,
        )
    }
}
