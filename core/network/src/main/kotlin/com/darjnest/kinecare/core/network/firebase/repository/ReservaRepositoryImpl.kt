@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.darjnest.kinecare.core.network.firebase.repository

import com.darjnest.kinecare.core.common.data.error.CrearReservaError
import com.darjnest.kinecare.core.common.data.error.ReservaError
import com.darjnest.kinecare.core.common.data.error.ResponderReservaError
import com.darjnest.kinecare.core.common.data.repository.ReservaRepository
import com.darjnest.kinecare.core.common.domain.model.Direccion
import com.darjnest.kinecare.core.common.domain.model.EstadoPago
import com.darjnest.kinecare.core.common.domain.model.EstadoReserva
import com.darjnest.kinecare.core.common.domain.model.MetodoPago
import com.darjnest.kinecare.core.common.domain.model.ModalidadServicio
import com.darjnest.kinecare.core.common.domain.model.Pago
import com.darjnest.kinecare.core.common.domain.model.Reserva
import com.darjnest.kinecare.core.common.domain.model.RespuestaReserva
import com.darjnest.kinecare.core.common.domain.model.SolicitudReserva
import com.darjnest.kinecare.core.common.domain.model.TipoMetodoPago
import com.darjnest.kinecare.core.common.result.Error
import com.darjnest.kinecare.core.common.result.Result
import com.darjnest.kinecare.core.network.functions.CloudFunctionsApi
import com.darjnest.kinecare.core.network.functions.dto.CallableErrorBody
import com.darjnest.kinecare.core.network.functions.dto.CallableErrorDto
import com.darjnest.kinecare.core.network.functions.dto.CallableRequest
import com.darjnest.kinecare.core.network.functions.dto.CallableResponse
import com.darjnest.kinecare.core.network.functions.dto.CrearReservaRequestDto
import com.darjnest.kinecare.core.network.functions.dto.DireccionDto
import com.darjnest.kinecare.core.network.functions.dto.ResponderReservaRequestDto
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.CancellationException
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import retrofit2.Response
import java.io.IOException
import javax.inject.Inject

private const val COLECCION_RESERVAS = "reservas"
private const val COLECCION_PAGOS = "pagos"

/**
 * Lectura de `reservas/{reservaId}` con el SDK de Firestore
 * (docs/DATA_MODEL.md) y escritura via las Cloud Functions `crearReserva` y
 * `responderReserva` (Retrofit, [CloudFunctionsApi]) — firestore.rules
 * deniega `write` desde el cliente sobre esta coleccion. Las funciones estan
 * desplegadas en QA (`kinecare-cl-qa`), no en produccion (plan Spark): ahi
 * terminan en `DESCONOCIDO` (404 sin cuerpo JSON).
 *
 * Requiere los indices compuestos `clienteId` (ASC) + `fechaHora` (DESC) y
 * `profesionalId` (ASC) + `fechaHora` (ASC) — documentados en
 * docs/DATA_MODEL.md y declarados en `firestore.indexes.json`.
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

    override suspend fun obtenerPorProfesional(profesionalId: String): Result<List<Reserva>, ReservaError> {
        return try {
            val documentos = firestore.collection(COLECCION_RESERVAS)
                .whereEqualTo("profesionalId", profesionalId)
                .orderBy("fechaHora", Query.Direction.ASCENDING)
                .get()
                .await()

            Result.Success(documentos.documents.mapNotNull { it.aReservaONull() })
        } catch (e: CancellationException) {
            throw e
        } catch (e: FirebaseNetworkException) {
            Result.Error(ReservaError.SIN_INTERNET)
        } catch (e: Exception) {
            Result.Error(ReservaError.DESCONOCIDO)
        }
    }

    override suspend fun crear(solicitud: SolicitudReserva): Result<String, CrearReservaError> {
        val resultado = llamarCallable(
            sinSesion = CrearReservaError.SIN_SESION,
            sinInternet = CrearReservaError.SIN_INTERNET,
            desconocido = CrearReservaError.DESCONOCIDO,
            aError = ::errorDeCrear,
            llamada = { autorizacion ->
                cloudFunctionsApi.crearReserva(autorizacion, CallableRequest(solicitud.aDto()))
            },
        )
        // `when` explicito y no `Result.map`: es inline y `:core:common` compila
        // con JVM target 17, que no se puede inlinear en este modulo (target 11).
        return when (resultado) {
            is Result.Success -> Result.Success(resultado.data.reservaId)
            is Result.Error -> resultado
        }
    }

    override suspend fun responder(
        reservaId: String,
        respuesta: RespuestaReserva,
    ): Result<EstadoReserva, ResponderReservaError> {
        val resultado = llamarCallable(
            sinSesion = ResponderReservaError.SIN_SESION,
            sinInternet = ResponderReservaError.SIN_INTERNET,
            desconocido = ResponderReservaError.DESCONOCIDO,
            aError = ::errorDeResponder,
            llamada = { autorizacion ->
                cloudFunctionsApi.responderReserva(
                    autorizacion,
                    CallableRequest(ResponderReservaRequestDto(reservaId, respuesta.name)),
                )
            },
        )
        return when (resultado) {
            is Result.Success -> EstadoReserva.entries.firstOrNull { it.name == resultado.data.estado }
                ?.let { Result.Success(it) }
                ?: Result.Error(ResponderReservaError.DESCONOCIDO)
            is Result.Error -> resultado
        }
    }

    /**
     * Llama a una funcion `onCall` con el ID token del usuario autenticado.
     * El cuerpo de error (si lo hay) se parsea a [CallableErrorDto] y
     * [aError] lo traduce al error propio de cada funcion; un cuerpo que no
     * es JSON de callable (p. ej. el 404 HTML de una funcion no desplegada)
     * llega como `null`.
     */
    private suspend fun <T, E : Error> llamarCallable(
        sinSesion: E,
        sinInternet: E,
        desconocido: E,
        aError: (CallableErrorDto?) -> E,
        llamada: suspend (autorizacion: String) -> Response<CallableResponse<T>>,
    ): Result<T, E> {
        val usuario = firebaseAuth.currentUser ?: return Result.Error(sinSesion)
        return try {
            val token = usuario.getIdToken(false).await().token
                ?: return Result.Error(sinSesion)
            val respuesta = llamada("Bearer $token")
            val cuerpo = respuesta.body()
            if (respuesta.isSuccessful && cuerpo != null) {
                Result.Success(cuerpo.result)
            } else {
                Result.Error(aError(errorCallable(respuesta.errorBody()?.string())))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: FirebaseNetworkException) {
            Result.Error(sinInternet)
        } catch (e: IOException) {
            Result.Error(sinInternet)
        } catch (e: Exception) {
            Result.Error(desconocido)
        }
    }

    private fun errorCallable(cuerpo: String?): CallableErrorDto? = cuerpo
        ?.let { runCatching { json.decodeFromString<CallableErrorBody>(it) }.getOrNull() }
        ?.error

    /** Prioriza `details.motivo` (1:1 con [CrearReservaError]); si no viene, cae al `status` canonico. */
    private fun errorDeCrear(error: CallableErrorDto?): CrearReservaError {
        if (error == null) return CrearReservaError.DESCONOCIDO
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

    /** Igual que [errorDeCrear], con los motivos de `responderReserva`. */
    private fun errorDeResponder(error: CallableErrorDto?): ResponderReservaError {
        if (error == null) return ResponderReservaError.DESCONOCIDO
        val porMotivo = error.details?.motivo?.let { motivo ->
            ResponderReservaError.entries.firstOrNull { it.name == motivo }
        }
        return porMotivo ?: when (error.status) {
            "UNAUTHENTICATED" -> ResponderReservaError.SIN_SESION
            "PERMISSION_DENIED" -> ResponderReservaError.ROL_INVALIDO
            "INVALID_ARGUMENT" -> ResponderReservaError.DATOS_INVALIDOS
            "NOT_FOUND" -> ResponderReservaError.RESERVA_NO_ENCONTRADA
            "FAILED_PRECONDITION" -> ResponderReservaError.RESERVA_YA_RESPONDIDA
            else -> ResponderReservaError.DESCONOCIDO
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
