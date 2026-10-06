@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.darjnest.kinecare.core.network.firebase.repository

import com.darjnest.kinecare.core.common.data.error.MercadoPagoError
import com.darjnest.kinecare.core.common.data.repository.MercadoPagoRepository
import com.darjnest.kinecare.core.common.domain.model.EstadoMercadoPago
import com.darjnest.kinecare.core.common.result.Result
import com.darjnest.kinecare.core.network.functions.CallableInvoker
import com.darjnest.kinecare.core.network.functions.CloudFunctionsApi
import com.darjnest.kinecare.core.network.functions.dto.CallableErrorDto
import com.darjnest.kinecare.core.network.functions.dto.CallableRequest
import com.darjnest.kinecare.core.network.functions.dto.CallableResponse
import com.darjnest.kinecare.core.network.functions.dto.MercadoPagoSinDatosDto
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import retrofit2.Response
import javax.inject.Inject

private const val COLECCION_MERCADO_PAGO_ESTADOS = "mercadoPagoEstados"

/**
 * Estado de la conexion Mercado Pago: lectura de
 * `mercadoPagoEstados/{uid}` con el SDK de Firestore (solo lectura para el
 * dueno; nadie lo escribe desde la app) y las Cloud Functions
 * `iniciarConexionMercadoPago` / `desconectarMercadoPago` via Retrofit
 * ([CloudFunctionsApi]). La app nunca ve el access token de Mercado Pago.
 */
class MercadoPagoRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
    firebaseAuth: FirebaseAuth,
    private val cloudFunctionsApi: CloudFunctionsApi,
    json: Json,
) : MercadoPagoRepository {

    private val invocador = CallableInvoker(firebaseAuth, json)

    override suspend fun obtenerEstado(profesionalId: String): Result<EstadoMercadoPago, MercadoPagoError> {
        return try {
            val doc = firestore.collection(COLECCION_MERCADO_PAGO_ESTADOS)
                .document(profesionalId)
                .get()
                .await()
            if (!doc.exists() || doc.getBoolean("conectado") != true) {
                Result.Success(EstadoMercadoPago.NoConectada)
            } else {
                Result.Success(
                    EstadoMercadoPago(
                        conectado = true,
                        conectadoEn = doc.getTimestamp("conectadoEn")
                            ?.let { Instant.fromEpochMilliseconds(it.toDate().time) },
                    ),
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: FirebaseNetworkException) {
            Result.Error(MercadoPagoError.SIN_INTERNET)
        } catch (e: Exception) {
            Result.Error(MercadoPagoError.DESCONOCIDO)
        }
    }

    override suspend fun iniciarConexion(): Result<String, MercadoPagoError> {
        val resultado = invocar { autorizacion ->
            cloudFunctionsApi.iniciarConexionMercadoPago(autorizacion, CallableRequest(MercadoPagoSinDatosDto))
        }
        // `when` explicito y no `Result.map`: ver comentario en ReservaRepositoryImpl.crear.
        return when (resultado) {
            is Result.Success -> resultado.data.urlAutorizacion.takeIf { it.isNotBlank() }
                ?.let { Result.Success(it) }
                ?: Result.Error(MercadoPagoError.DESCONOCIDO)
            is Result.Error -> resultado
        }
    }

    override suspend fun desconectar(): Result<Unit, MercadoPagoError> {
        val resultado = invocar { autorizacion ->
            cloudFunctionsApi.desconectarMercadoPago(autorizacion, CallableRequest(MercadoPagoSinDatosDto))
        }
        return when (resultado) {
            is Result.Success -> if (resultado.data.desconectado) {
                Result.Success(Unit)
            } else {
                Result.Error(MercadoPagoError.DESCONOCIDO)
            }
            is Result.Error -> resultado
        }
    }

    private suspend fun <T> invocar(
        llamada: suspend (autorizacion: String) -> Response<CallableResponse<T>>,
    ): Result<T, MercadoPagoError> = invocador.invocar(
        sinSesion = MercadoPagoError.SIN_SESION,
        sinInternet = MercadoPagoError.SIN_INTERNET,
        desconocido = MercadoPagoError.DESCONOCIDO,
        aError = ::errorDeMercadoPago,
        llamada = llamada,
    )

    /** Prioriza `details.motivo` (1:1 con [MercadoPagoError]); si no viene, cae al `status` canonico. */
    private fun errorDeMercadoPago(error: CallableErrorDto?): MercadoPagoError {
        if (error == null) return MercadoPagoError.DESCONOCIDO
        val porMotivo = error.details?.motivo?.let { motivo ->
            MercadoPagoError.entries.firstOrNull { it.name == motivo }
        }
        return porMotivo ?: when (error.status) {
            "UNAUTHENTICATED" -> MercadoPagoError.SIN_SESION
            "PERMISSION_DENIED" -> MercadoPagoError.ROL_INVALIDO
            else -> MercadoPagoError.DESCONOCIDO
        }
    }
}
