@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.darjnest.kinecare.core.network.firebase.repository

import com.darjnest.kinecare.core.common.data.error.CrearReservaError
import com.darjnest.kinecare.core.common.data.error.ReservaError
import com.darjnest.kinecare.core.common.data.repository.ReservaRepository
import com.darjnest.kinecare.core.common.domain.model.Direccion
import com.darjnest.kinecare.core.common.domain.model.EstadoPago
import com.darjnest.kinecare.core.common.domain.model.EstadoReserva
import com.darjnest.kinecare.core.common.domain.model.MetodoPago
import com.darjnest.kinecare.core.common.domain.model.ModalidadServicio
import com.darjnest.kinecare.core.common.domain.model.Pago
import com.darjnest.kinecare.core.common.domain.model.Reserva
import com.darjnest.kinecare.core.common.domain.model.SolicitudReserva
import com.darjnest.kinecare.core.common.domain.model.TipoMetodoPago
import com.darjnest.kinecare.core.common.result.Result
import com.darjnest.kinecare.core.network.functions.CloudFunctionsApi
import com.darjnest.kinecare.core.network.functions.dto.CallableErrorBody
import com.darjnest.kinecare.core.network.functions.dto.CallableRequest
import com.darjnest.kinecare.core.network.functions.dto.CrearReservaRequestDto
import com.darjnest.kinecare.core.network.functions.dto.DireccionDto
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.CancellationException
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import java.io.IOException
import javax.inject.Inject

private const val COLECCION_RESERVAS = "reservas"
private const val COLECCION_PAGOS = "pagos"

/**
 * Lectura de `reservas/{reservaId}` con el SDK de Firestore
 * (docs/DATA_MODEL.md) y creacion via la Cloud Function `crearReserva`
 * (Retrofit, [CloudFunctionsApi]) — firestore.rules deniega `write` desde el
 * cliente sobre esta coleccion. `crearReserva` esta desplegada en QA
 * (`kinecare-cl-qa`), no en produccion (plan Spark): ahi [crear] termina en
 * [CrearReservaError.DESCONOCIDO] (404 sin cuerpo JSON).
 *
 * Requiere el indice compuesto `clienteId` (ASC) + `fechaHora` (DESC) —
 * documentado en docs/DATA_MODEL.md y agregado a `firestore.indexes.json`.
 */
class ReservaRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val firebaseAuth: FirebaseAuth,
    private val cloudFunctionsApi: CloudFunctionsApi,
    private val json: Json,
) : ReservaRepository {

    override suspend fun obtenerPorCliente(clienteId: String): Result<List<Reserva>, ReservaError> {
        return try {
            val documentos = firestore.collection(COLECCION_RESERVAS)
                .whereEqualTo("clienteId", clienteId)
                .orderBy("fechaHora", Query.Direction.DESCENDING)
                .get()
                .await()

            val reservas = documentos.documents.mapNotNull { it.aReservaONull() }
            Result.Success(reservas)
        } catch (e: FirebaseNetworkException) {
            Result.Error(ReservaError.SIN_INTERNET)
        } catch (e: Exception) {
            Result.Error(ReservaError.DESCONOCIDO)
        }
    }

    override suspend fun crear(solicitud: SolicitudReserva): Result<String, CrearReservaError> {
        val usuario = firebaseAuth.currentUser ?: return Result.Error(CrearReservaError.SIN_SESION)
        return try {
            val token = usuario.getIdToken(false).await().token
                ?: return Result.Error(CrearReservaError.SIN_SESION)
            val respuesta = cloudFunctionsApi.crearReserva(
                autorizacion = "Bearer $token",
                cuerpo = CallableRequest(solicitud.aDto()),
            )
            val cuerpo = respuesta.body()
            if (respuesta.isSuccessful && cuerpo != null) {
                Result.Success(cuerpo.result.reservaId)
            } else {
                Result.Error(errorDeCallable(respuesta.errorBody()?.string()))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: FirebaseNetworkException) {
            Result.Error(CrearReservaError.SIN_INTERNET)
        } catch (e: IOException) {
            Result.Error(CrearReservaError.SIN_INTERNET)
        } catch (e: Exception) {
            Result.Error(CrearReservaError.DESCONOCIDO)
        }
    }

    /**
     * Prioriza `details.motivo` (1:1 con [CrearReservaError]); si no viene,
     * cae al `status` canonico. Un cuerpo que no es JSON de callable (p. ej.
     * el 404 HTML de una funcion no desplegada) es [CrearReservaError.DESCONOCIDO].
     */
    private fun errorDeCallable(cuerpo: String?): CrearReservaError {
        val error = cuerpo
            ?.let { runCatching { json.decodeFromString<CallableErrorBody>(it) }.getOrNull() }
            ?.error
            ?: return CrearReservaError.DESCONOCIDO
        val porMotivo = error.details?.motivo?.let { motivo ->
            CrearReservaError.entries.firstOrNull { it.name == motivo }
        }
        return porMotivo ?: when (error.status) {
            "UNAUTHENTICATED" -> CrearReservaError.SIN_SESION
            "PERMISSION_DENIED" -> CrearReservaError.ROL_INVALIDO
            "INVALID_ARGUMENT" -> CrearReservaError.DATOS_INVALIDOS
            "ALREADY_EXISTS" -> CrearReservaError.HORARIO_OCUPADO
            "NOT_FOUND" -> CrearReservaError.SERVICIO_NO_DISPONIBLE
            else -> CrearReservaError.DESCONOCIDO
        }
    }

    private suspend fun DocumentSnapshot.aReservaONull(): Reserva? {
        if (!exists()) return null
        val clienteId = getString("clienteId") ?: return null
        val profesionalId = getString("profesionalId") ?: return null
        val servicioId = getString("servicioId") ?: return null
        val fechaHora = getTimestamp("fechaHora")?.let { Instant.fromEpochMilliseconds(it.toDate().time) } ?: return null

        @Suppress("UNCHECKED_CAST")
        val direccionMap = get("direccion") as? Map<String, Any?>
        val direccion = direccionMap?.aDireccion()

        @Suppress("UNCHECKED_CAST")
        val pagoRef = get("pago") as? Map<String, Any?> ?: emptyMap()
        val pagoId = pagoRef["id"] as? String
        val pago = pagoId?.let { obtenerPago(it) } ?: pagoRef.aPagoIncompleto(id)

        return Reserva(
            id = id,
            clienteId = clienteId,
            profesionalId = profesionalId,
            servicioId = servicioId,
            modalidad = runCatching {
                ModalidadServicio.valueOf(getString("modalidad") ?: "")
            }.getOrDefault(ModalidadServicio.CONSULTA),
            fechaHora = fechaHora,
            direccion = direccion,
            estado = runCatching {
                EstadoReserva.valueOf(getString("estado") ?: "")
            }.getOrDefault(EstadoReserva.SOLICITADA),
            pago = pago,
            comisionPorcentaje = getDouble("comisionPorcentaje") ?: 0.0,
        )
    }

    /**
     * `reservas/{id}.pago` solo trae `{ id, monto, estado }` (docs/DATA_MODEL.md);
     * el detalle completo (metodo, id de transaccion) vive en `pagos/{pagoId}`,
     * escrito solo por Cloud Functions.
     */
    private suspend fun obtenerPago(pagoId: String): Pago? {
        val doc = firestore.collection(COLECCION_PAGOS).document(pagoId).get().await()
        if (!doc.exists()) return null

        @Suppress("UNCHECKED_CAST")
        val metodoMap = doc.get("metodo") as? Map<String, Any?>
        val metodo = metodoMap?.aMetodoPago() ?: MetodoPago(TipoMetodoPago.TARJETA, null, "")

        return Pago(
            id = doc.id,
            reservaId = doc.getString("reservaId") ?: "",
            monto = doc.getLong("monto") ?: 0L,
            metodo = metodo,
            estado = runCatching { EstadoPago.valueOf(doc.getString("estado") ?: "") }.getOrDefault(EstadoPago.PENDIENTE),
            idTransaccionPasarela = doc.getString("idTransaccionPasarela"),
        )
    }

    /** Sin `pagos/{pagoId}` que resolver (borrador o dato incompleto): arma un `Pago` minimo con lo que trae la `PagoRef` embebida. */
    private fun Map<String, Any?>.aPagoIncompleto(reservaId: String): Pago = Pago(
        id = this["id"] as? String ?: "",
        reservaId = reservaId,
        monto = (this["monto"] as? Long) ?: 0L,
        metodo = MetodoPago(TipoMetodoPago.TARJETA, null, ""),
        estado = runCatching { EstadoPago.valueOf(this["estado"] as? String ?: "") }.getOrDefault(EstadoPago.PENDIENTE),
        idTransaccionPasarela = null,
    )

    private fun Map<String, Any?>.aDireccion(): Direccion = Direccion(
        calle = this["calle"] as? String ?: "",
        numero = this["numero"] as? String ?: "",
        comuna = this["comuna"] as? String ?: "",
        ciudad = this["ciudad"] as? String ?: "",
        lat = this["lat"] as? Double,
        lng = this["lng"] as? Double,
        indicaciones = this["indicaciones"] as? String,
    )

    private fun Map<String, Any?>.aMetodoPago(): MetodoPago {
        val tipo = runCatching { TipoMetodoPago.valueOf(this["tipo"] as? String ?: "") }.getOrDefault(TipoMetodoPago.TARJETA)
        return MetodoPago(
            tipo = tipo,
            ultimosDigitos = this["ultimosDigitos"] as? String,
            tokenPasarela = this["tokenPasarela"] as? String ?: "",
        )
    }
}

private fun SolicitudReserva.aDto() = CrearReservaRequestDto(
    profesionalId = profesionalId,
    servicioId = servicioId,
    fechaHora = fechaHora.toString(),
    direccion = direccion?.let {
        DireccionDto(
            calle = it.calle,
            numero = it.numero,
            comuna = it.comuna,
            ciudad = it.ciudad,
            lat = it.lat,
            lng = it.lng,
            indicaciones = it.indicaciones,
        )
    },
)
