package com.darjnest.kinecare.core.network.firebase.repository

import com.darjnest.kinecare.core.common.data.error.ServicioError
import com.darjnest.kinecare.core.common.data.repository.ServicioRepository
import com.darjnest.kinecare.core.common.domain.model.ModalidadServicio
import com.darjnest.kinecare.core.common.domain.model.Servicio
import com.darjnest.kinecare.core.common.result.Result
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

private const val COLECCION_PROFESIONALES = "profesionales"
private const val SUBCOLECCION_SERVICIOS = "servicios"

/** Implementacion Firestore de `profesionales/{id}/servicios` (docs/DATA_MODEL.md). */
class ServicioRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
) : ServicioRepository {

    override suspend fun obtenerPorProfesional(profesionalId: String): Result<List<Servicio>, ServicioError> {
        return try {
            val documentos = servicios(profesionalId).get().await()
            Result.Success(documentos.documents.mapNotNull { it.aServicioONull() })
        } catch (e: FirebaseNetworkException) {
            Result.Error(ServicioError.SIN_INTERNET)
        } catch (e: Exception) {
            Result.Error(ServicioError.DESCONOCIDO)
        }
    }

    override suspend fun actualizarActivo(
        profesionalId: String,
        servicioId: String,
        activo: Boolean,
    ): Result<Unit, ServicioError> {
        return try {
            servicios(profesionalId).document(servicioId).update("activo", activo).await()
            Result.Success(Unit)
        } catch (e: FirebaseNetworkException) {
            Result.Error(ServicioError.SIN_INTERNET)
        } catch (e: Exception) {
            Result.Error(ServicioError.DESCONOCIDO)
        }
    }

    private fun servicios(profesionalId: String): CollectionReference =
        firestore.collection(COLECCION_PROFESIONALES)
            .document(profesionalId)
            .collection(SUBCOLECCION_SERVICIOS)

    private fun DocumentSnapshot.aServicioONull(): Servicio? {
        if (!exists()) return null
        return Servicio(
            id = id,
            nombre = getString("nombre") ?: "",
            descripcion = getString("descripcion") ?: "",
            modalidad = runCatching {
                ModalidadServicio.valueOf(getString("modalidad") ?: "")
            }.getOrDefault(ModalidadServicio.CONSULTA),
            duracionMinutos = getLong("duracionMinutos")?.toInt() ?: 0,
            precio = getLong("precio") ?: 0L,
            activo = getBoolean("activo") ?: true,
        )
    }
}
