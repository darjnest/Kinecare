package com.darjnest.kinecare.core.network.firebase.repository

import com.darjnest.kinecare.core.common.data.error.ConectarMercadoPagoError
import com.darjnest.kinecare.core.common.data.error.EstadoPagoError
import com.darjnest.kinecare.core.common.data.error.IniciarPagoError
import com.darjnest.kinecare.core.common.data.repository.PagoRepository
import com.darjnest.kinecare.core.common.domain.model.EstadoPago
import com.darjnest.kinecare.core.common.domain.model.IntentoPago
import com.darjnest.kinecare.core.common.result.Error
import com.darjnest.kinecare.core.common.result.Result
import com.darjnest.kinecare.core.network.functions.CloudFunctionsApi
import com.darjnest.kinecare.core.network.functions.dto.CallableErrorBody
import com.darjnest.kinecare.core.network.functions.dto.CallableErrorDto
import com.darjnest.kinecare.core.network.functions.dto.CallableRequest
import com.darjnest.kinecare.core.network.functions.dto.CallableResponse
import com.darjnest.kinecare.core.network.functions.dto.ConectarMercadoPagoRequestDto
import com.darjnest.kinecare.core.network.functions.dto.EstadoPagoRequestDto
import com.darjnest.kinecare.core.network.functions.dto.IniciarPagoRequestDto
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.json.Json
import retrofit2.Response
import java.io.IOException
import javax.inject.Inject

/**
 * Pagos con Mercado Pago via las Cloud Functions `iniciarPago`, `estadoPago` y
 * `conectarMercadoPago` (Retrofit, [CloudFunctionsApi]). El cliente solo envia
 * ids: el monto, la comision y el vendedor los resuelve el backend
 * (docs/DATA_MODEL.md, "Pagos con Mercado Pago"). Las funciones aun no estan
 * desplegadas: hasta entonces terminan en `DESCONOCIDO` (404 sin cuerpo JSON).
 *
 * La URL de pago (`initPoint`) y el `state` de OAuth viajan en el cuerpo de la
 * respuesta; OkHttp se configura con `Level.BASIC`, asi que no se loguean.
 */
class PagoRepositoryImpl @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
    private val cloudFunctionsApi: CloudFunctionsApi,
    private val json: Json,
) : PagoRepository {

    override suspend fun iniciar(reservaId: String): Result<IntentoPago, IniciarPagoError> {
        val resultado = llamarCallable(
            sinSesion = IniciarPagoError.SIN_SESION,
            sinInternet = IniciarPagoError.SIN_INTERNET,
            desconocido = IniciarPagoError.DESCONOCIDO,
            aError = { error -> error.aErrorDe(IniciarPagoError.entries, IniciarPagoError.DESCONOCIDO, ::estadoCanonicoIniciar) },
            llamada = { autorizacion ->
                cloudFunctionsApi.iniciarPago(autorizacion, CallableRequest(IniciarPagoRequestDto(reservaId)))
            },
        )
        // `when` explicito y no `Result.map`: ver ReservaRepositoryImpl.crear.
        return when (resultado) {
            is Result.Success -> Result.Success(IntentoPago(resultado.data.pagoId, resultado.data.initPoint))
            is Result.Error -> resultado
        }
    }

    override suspend fun consultarEstado(pagoId: String): Result<EstadoPago, EstadoPagoError> {
        val resultado = llamarCallable(
            sinSesion = EstadoPagoError.SIN_SESION,
            sinInternet = EstadoPagoError.SIN_INTERNET,
            desconocido = EstadoPagoError.DESCONOCIDO,
            aError = { error -> error.aErrorDe(EstadoPagoError.entries, EstadoPagoError.DESCONOCIDO, ::estadoCanonicoEstado) },
            llamada = { autorizacion ->
                cloudFunctionsApi.estadoPago(autorizacion, CallableRequest(EstadoPagoRequestDto(pagoId)))
            },
        )
        return when (resultado) {
            is Result.Success -> EstadoPago.entries.firstOrNull { it.name == resultado.data.estado }
                ?.let { Result.Success(it) }
                ?: Result.Error(EstadoPagoError.DESCONOCIDO)
            is Result.Error -> resultado
        }
    }

    override suspend fun obtenerUrlConexion(): Result<String, ConectarMercadoPagoError> {
        val resultado = llamarCallable(
            sinSesion = ConectarMercadoPagoError.SIN_SESION,
            sinInternet = ConectarMercadoPagoError.SIN_INTERNET,
            desconocido = ConectarMercadoPagoError.DESCONOCIDO,
            aError = { error ->
                error.aErrorDe(ConectarMercadoPagoError.entries, ConectarMercadoPagoError.DESCONOCIDO, ::estadoCanonicoConectar)
            },
            llamada = { autorizacion ->
                cloudFunctionsApi.conectarMercadoPago(autorizacion, CallableRequest(ConectarMercadoPagoRequestDto()))
            },
        )
        return when (resultado) {
            is Result.Success -> Result.Success(resultado.data.authorizationUrl)
            is Result.Error -> resultado
        }
    }

    /**
     * Llama a una funcion `onCall` con el ID token del usuario autenticado. El
     * cuerpo de error se parsea a [CallableErrorDto] y [aError] lo traduce al
     * error propio de cada funcion; un cuerpo que no es JSON de callable (p. ej.
     * el 404 HTML de una funcion no desplegada) llega como `null`.
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
            val token = usuario.getIdToken(false).await().token ?: return Result.Error(sinSesion)
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

    /** Prioriza `details.motivo` (1:1 con el enum); si no viene, cae al `status` canonico de [aCanonico]. */
    private fun <E : Enum<E>> CallableErrorDto?.aErrorDe(
        valores: List<E>,
        desconocido: E,
        aCanonico: (String?) -> E?,
    ): E {
        if (this == null) return desconocido
        val porMotivo = details?.motivo?.let { motivo -> valores.firstOrNull { it.name == motivo } }
        return porMotivo ?: aCanonico(status) ?: desconocido
    }

    private fun estadoCanonicoIniciar(status: String?): IniciarPagoError? = when (status) {
        "UNAUTHENTICATED" -> IniciarPagoError.SIN_SESION
        "PERMISSION_DENIED" -> IniciarPagoError.ROL_INVALIDO
        "INVALID_ARGUMENT" -> IniciarPagoError.DATOS_INVALIDOS
        "NOT_FOUND" -> IniciarPagoError.RESERVA_NO_ENCONTRADA
        "UNAVAILABLE" -> IniciarPagoError.PASARELA_NO_DISPONIBLE
        else -> null
    }

    private fun estadoCanonicoEstado(status: String?): EstadoPagoError? = when (status) {
        "UNAUTHENTICATED" -> EstadoPagoError.SIN_SESION
        "INVALID_ARGUMENT" -> EstadoPagoError.DATOS_INVALIDOS
        "NOT_FOUND" -> EstadoPagoError.PAGO_NO_ENCONTRADO
        "UNAVAILABLE" -> EstadoPagoError.PASARELA_NO_DISPONIBLE
        else -> null
    }

    private fun estadoCanonicoConectar(status: String?): ConectarMercadoPagoError? = when (status) {
        "UNAUTHENTICATED" -> ConectarMercadoPagoError.SIN_SESION
        "PERMISSION_DENIED" -> ConectarMercadoPagoError.ROL_INVALIDO
        else -> null
    }
}
