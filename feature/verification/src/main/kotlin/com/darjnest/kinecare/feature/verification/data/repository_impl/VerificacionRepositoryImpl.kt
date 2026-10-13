package com.darjnest.kinecare.feature.verification.data.repository_impl

import com.darjnest.kinecare.core.common.domain.model.EstadoVerificacion
import com.darjnest.kinecare.core.common.domain.model.TipoInsignia
import com.darjnest.kinecare.core.common.result.Result
import com.darjnest.kinecare.core.network.functions.CloudFunctionsApi
import com.darjnest.kinecare.core.network.functions.aErrorDe
import com.darjnest.kinecare.core.network.functions.dto.CallableRequest
import com.darjnest.kinecare.core.network.functions.dto.EstadoVerificacionRequestDto
import com.darjnest.kinecare.core.network.functions.dto.SolicitarVerificacionRequestDto
import com.darjnest.kinecare.core.network.functions.llamarCallable
import com.darjnest.kinecare.feature.verification.data.repository.VerificacionRepository
import com.darjnest.kinecare.feature.verification.domain.EstadoSolicitudVerificacion
import com.darjnest.kinecare.feature.verification.domain.EstadoVerificacionError
import com.darjnest.kinecare.feature.verification.domain.IntentoVerificacion
import com.darjnest.kinecare.feature.verification.domain.MotivoRechazoVerificacion
import com.darjnest.kinecare.feature.verification.domain.SolicitarVerificacionError
import com.google.firebase.auth.FirebaseAuth
import kotlinx.serialization.json.Json
import javax.inject.Inject

/**
 * Verificacion de identidad via las Cloud Functions `solicitarVerificacion` y
 * `estadoVerificacion` (Retrofit, [CloudFunctionsApi]). El cliente solo envia el
 * tipo (o el id de la solicitud): la sesion con el proveedor, la comparacion del
 * RUT y la resolucion de la insignia las hace el backend. Nada de la
 * verificacion (documento, selfie, resultado) se guarda en el dispositivo.
 *
 * La URL de verificacion viaja en el cuerpo de la respuesta; OkHttp se
 * configura con `Level.BASIC`, asi que no se loguea.
 */
class VerificacionRepositoryImpl @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
    private val cloudFunctionsApi: CloudFunctionsApi,
    private val json: Json,
) : VerificacionRepository {

    override suspend fun solicitar(): Result<IntentoVerificacion, SolicitarVerificacionError> {
        val resultado = llamarCallable(
            firebaseAuth = firebaseAuth,
            json = json,
            sinSesion = SolicitarVerificacionError.SIN_SESION,
            sinInternet = SolicitarVerificacionError.SIN_INTERNET,
            desconocido = SolicitarVerificacionError.DESCONOCIDO,
            aError = { error ->
                error.aErrorDe(
                    SolicitarVerificacionError.entries,
                    SolicitarVerificacionError.DESCONOCIDO,
                    ::estadoCanonicoSolicitar,
                )
            },
            llamada = { autorizacion ->
                cloudFunctionsApi.solicitarVerificacion(
                    autorizacion,
                    CallableRequest(SolicitarVerificacionRequestDto(TipoInsignia.IDENTIDAD.name)),
                )
            },
        )
        // `when` explicito y no `Result.map`: ver ReservaRepositoryImpl.crear.
        return when (resultado) {
            is Result.Success -> Result.Success(IntentoVerificacion(resultado.data.solicitudId, resultado.data.url))
            is Result.Error -> resultado
        }
    }

    override suspend fun consultarEstado(
        solicitudId: String?,
    ): Result<EstadoSolicitudVerificacion, EstadoVerificacionError> {
        val resultado = llamarCallable(
            firebaseAuth = firebaseAuth,
            json = json,
            sinSesion = EstadoVerificacionError.SIN_SESION,
            sinInternet = EstadoVerificacionError.SIN_INTERNET,
            desconocido = EstadoVerificacionError.DESCONOCIDO,
            aError = { error ->
                error.aErrorDe(
                    EstadoVerificacionError.entries,
                    EstadoVerificacionError.DESCONOCIDO,
                    ::estadoCanonicoEstado,
                )
            },
            llamada = { autorizacion ->
                cloudFunctionsApi.estadoVerificacion(
                    autorizacion,
                    CallableRequest(EstadoVerificacionRequestDto(solicitudId)),
                )
            },
        )
        return when (resultado) {
            is Result.Success -> {
                // Un estado que esta version no conoce es un error generico, nunca un crash ni un exito.
                val estado = EstadoVerificacion.entries.firstOrNull { it.name == resultado.data.estado }
                if (estado == null) {
                    Result.Error(EstadoVerificacionError.DESCONOCIDO)
                } else {
                    Result.Success(
                        EstadoSolicitudVerificacion(
                            estado = estado,
                            solicitudId = resultado.data.solicitudId,
                            // El motivo solo importa al rechazar; uno desconocido queda en null (mensaje generico).
                            motivo = if (estado == EstadoVerificacion.RECHAZADO) {
                                MotivoRechazoVerificacion.entries.firstOrNull { it.name == resultado.data.motivo }
                            } else {
                                null
                            },
                        ),
                    )
                }
            }
            is Result.Error -> resultado
        }
    }

    private fun estadoCanonicoSolicitar(status: String?): SolicitarVerificacionError? = when (status) {
        "UNAUTHENTICATED" -> SolicitarVerificacionError.SIN_SESION
        "PERMISSION_DENIED" -> SolicitarVerificacionError.NO_ES_PROFESIONAL
        "NOT_FOUND" -> SolicitarVerificacionError.PERFIL_NO_ENCONTRADO
        "UNAVAILABLE" -> SolicitarVerificacionError.PROVEEDOR_NO_DISPONIBLE
        else -> null
    }

    private fun estadoCanonicoEstado(status: String?): EstadoVerificacionError? = when (status) {
        "UNAUTHENTICATED" -> EstadoVerificacionError.SIN_SESION
        "NOT_FOUND" -> EstadoVerificacionError.SOLICITUD_NO_ENCONTRADA
        else -> null
    }
}
