@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.darjnest.kinecare.feature.search.data.repository_impl

import com.darjnest.kinecare.core.common.domain.model.Disponibilidad
import com.darjnest.kinecare.core.common.domain.model.EstadoVerificacion
import com.darjnest.kinecare.core.common.domain.model.Insignia
import com.darjnest.kinecare.core.common.domain.model.ModalidadServicio
import com.darjnest.kinecare.core.common.domain.model.Profesional
import com.darjnest.kinecare.core.common.domain.model.RolUsuario
import com.darjnest.kinecare.core.common.domain.model.Servicio
import com.darjnest.kinecare.core.common.domain.model.TipoInsignia
import com.darjnest.kinecare.core.common.domain.model.Usuario
import com.darjnest.kinecare.core.common.result.Result
import com.darjnest.kinecare.feature.search.data.repository.ProfesionalRepository
import com.darjnest.kinecare.feature.search.domain.ProfesionalError
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalTime
import javax.inject.Inject
import kotlin.time.Clock

private const val COLECCION_PROFESIONALES = "profesionales"
private const val COLECCION_USUARIOS = "usuarios"
private const val SUBCOLECCION_SERVICIOS = "servicios"

class ProfesionalRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
) : ProfesionalRepository {

    override suspend fun buscarPorEspecialidad(especialidad: String): Result<List<Profesional>, ProfesionalError> {
        return try {
            val documentos = firestore.collection(COLECCION_PROFESIONALES)
                .whereArrayContains("especialidades", especialidad)
                .orderBy("calificacionPromedio", Query.Direction.DESCENDING)
                .limit(20)
                .get()
                .await()

            val profesionales = documentos.documents.mapNotNull { it.aProfesionalONull() }
            Result.Success(profesionales)
        } catch (e: FirebaseNetworkException) {
            Result.Error(ProfesionalError.SIN_INTERNET)
        } catch (e: Exception) {
            Result.Error(ProfesionalError.DESCONOCIDO)
        }
    }

    private suspend fun DocumentSnapshot.aProfesionalONull(): Profesional? {
        if (!exists()) return null
        val usuario = obtenerUsuario(id) ?: return null

        @Suppress("UNCHECKED_CAST")
        val especialidades = (get("especialidades") as? List<Any?>)
            ?.filterIsInstance<String>()
            ?: emptyList()

        @Suppress("UNCHECKED_CAST")
        val insignias = (get("insignias") as? List<Map<String, Any?>>)
            ?.mapNotNull { it.aInsigniaONull() }
            ?: emptyList()

        @Suppress("UNCHECKED_CAST")
        val disponibilidad = (get("disponibilidad") as? List<Map<String, Any?>>)
            ?.mapNotNull { it.aDisponibilidadONull() }
            ?: emptyList()

        return Profesional(
            usuario = usuario,
            especialidades = especialidades,
            rnpi = getString("rnpi") ?: "",
            servicios = obtenerServicios(id),
            insignias = insignias,
            disponibilidad = disponibilidad,
            calificacionPromedio = getDouble("calificacionPromedio") ?: 0.0,
            totalResenas = getLong("totalResenas")?.toInt() ?: 0,
            descripcion = getString("descripcion") ?: "",
            estadoVerificacionGeneral = runCatching {
                EstadoVerificacion.valueOf(getString("estadoVerificacionGeneral") ?: "")
            }.getOrDefault(EstadoVerificacion.NO_SOLICITADO),
        )
    }

    private suspend fun obtenerUsuario(uid: String): Usuario? {
        val doc = firestore.collection(COLECCION_USUARIOS).document(uid).get().await()
        if (!doc.exists()) return null
        return Usuario(
            id = uid,
            nombre = doc.getString("nombre") ?: "",
            rut = doc.getString("rut") ?: "",
            email = doc.getString("email") ?: "",
            correoContacto = doc.getString("correoContacto"),
            telefono = doc.getString("telefono"),
            rol = runCatching { RolUsuario.valueOf(doc.getString("rol") ?: "") }.getOrDefault(RolUsuario.PROFESIONAL),
            fotoUrl = doc.getString("fotoUrl"),
            fechaRegistro = doc.getTimestamp("fechaRegistro")?.let { Instant.fromEpochMilliseconds(it.toDate().time) }
                ?: Clock.System.now(),
        )
    }

    private suspend fun obtenerServicios(profesionalId: String): List<Servicio> {
        val documentos = firestore.collection(COLECCION_PROFESIONALES)
            .document(profesionalId)
            .collection(SUBCOLECCION_SERVICIOS)
            .get()
            .await()
        return documentos.documents.mapNotNull { it.aServicioONull() }
    }

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
        )
    }

    private fun Map<String, Any?>.aInsigniaONull(): Insignia? {
        val tipo = runCatching { TipoInsignia.valueOf(this["tipo"] as? String ?: "") }.getOrNull() ?: return null
        val estado = runCatching {
            EstadoVerificacion.valueOf(this["estado"] as? String ?: "")
        }.getOrDefault(EstadoVerificacion.NO_SOLICITADO)
        val fechaActualizacion = (this["fechaActualizacion"] as? com.google.firebase.Timestamp)
            ?.let { Instant.fromEpochMilliseconds(it.toDate().time) }
            ?: Clock.System.now()
        return Insignia(
            tipo = tipo,
            estado = estado,
            detalle = this["detalle"] as? String,
            fechaActualizacion = fechaActualizacion,
        )
    }

    private fun Map<String, Any?>.aDisponibilidadONull(): Disponibilidad? {
        val diaSemana = runCatching { DayOfWeek.valueOf(this["diaSemana"] as? String ?: "") }.getOrNull() ?: return null
        val horaInicio = runCatching { LocalTime.parse(this["horaInicio"] as? String ?: "") }.getOrNull() ?: return null
        val horaFin = runCatching { LocalTime.parse(this["horaFin"] as? String ?: "") }.getOrNull() ?: return null
        return Disponibilidad(
            diaSemana = diaSemana,
            horaInicio = horaInicio,
            horaFin = horaFin,
            activo = this["activo"] as? Boolean ?: false,
        )
    }
}
