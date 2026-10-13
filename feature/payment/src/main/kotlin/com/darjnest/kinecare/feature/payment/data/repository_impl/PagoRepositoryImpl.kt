package com.darjnest.kinecare.feature.payment.data.repository_impl

import com.darjnest.kinecare.feature.payment.domain.ConectarMercadoPagoError
import com.darjnest.kinecare.feature.payment.domain.EstadoPagoError
import com.darjnest.kinecare.feature.payment.domain.IniciarPagoError
import com.darjnest.kinecare.feature.payment.data.repository.PagoRepository
import com.darjnest.kinecare.core.common.domain.model.EstadoPago
import com.darjnest.kinecare.feature.payment.domain.IntentoPago
import com.darjnest.kinecare.core.common.result.Result
import com.darjnest.kinecare.core.network.functions.CloudFunctionsApi
import com.darjnest.kinecare.core.network.functions.aErrorDe
import com.darjnest.kinecare.core.network.functions.llamarCallable
import com.darjnest.kinecare.core.network.functions.dto.CallableRequest
import com.darjnest.kinecare.core.network.functions.dto.ConectarMercadoPagoRequestDto
import com.darjnest.kinecare.core.network.functions.dto.EstadoPagoRequestDto
import com.darjnest.kinecare.core.network.functions.dto.IniciarPagoRequestDto
import com.google.firebase.auth.FirebaseAuth
import kotlinx.serialization.json.Json
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
            firebaseAuth = firebaseAuth,
            json = json,
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
            firebaseAuth = firebaseAuth,
            json = json,
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
            firebaseAuth = firebaseAuth,
            json = json,
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
