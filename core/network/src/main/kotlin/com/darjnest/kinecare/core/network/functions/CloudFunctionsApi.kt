package com.darjnest.kinecare.core.network.functions

import com.darjnest.kinecare.core.network.functions.dto.CallableRequest
import com.darjnest.kinecare.core.network.functions.dto.CallableResponse
import com.darjnest.kinecare.core.network.functions.dto.ConectarMercadoPagoRequestDto
import com.darjnest.kinecare.core.network.functions.dto.ConectarMercadoPagoResultadoDto
import com.darjnest.kinecare.core.network.functions.dto.CrearReservaRequestDto
import com.darjnest.kinecare.core.network.functions.dto.CrearReservaResultadoDto
import com.darjnest.kinecare.core.network.functions.dto.EstadoPagoRequestDto
import com.darjnest.kinecare.core.network.functions.dto.EstadoPagoResultadoDto
import com.darjnest.kinecare.core.network.functions.dto.IniciarPagoRequestDto
import com.darjnest.kinecare.core.network.functions.dto.IniciarPagoResultadoDto
import com.darjnest.kinecare.core.network.functions.dto.ResponderReservaRequestDto
import com.darjnest.kinecare.core.network.functions.dto.ResponderReservaResultadoDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

/**
 * Cloud Functions `onCall` (codigo en `functions/`) consumidas por Retrofit
 * en vez del SDK `firebase-functions`, para que pagos (Fase 5) pasen por el
 * mismo OkHttp con certificate pinning (docs/ARCHITECTURE.md#seguridad).
 * Retorna `Response` y no el cuerpo directo: el error de una callable trae
 * su `motivo` en el cuerpo de la respuesta no-2xx.
 */
interface CloudFunctionsApi {

    @POST("crearReserva")
    suspend fun crearReserva(
        /** `Bearer <ID token de Firebase Auth>`; la funcion lo verifica y expone `request.auth`. */
        @Header("Authorization") autorizacion: String,
        @Body cuerpo: CallableRequest<CrearReservaRequestDto>,
    ): Response<CallableResponse<CrearReservaResultadoDto>>

    @POST("responderReserva")
    suspend fun responderReserva(
        @Header("Authorization") autorizacion: String,
        @Body cuerpo: CallableRequest<ResponderReservaRequestDto>,
    ): Response<CallableResponse<ResponderReservaResultadoDto>>

    @POST("iniciarPago")
    suspend fun iniciarPago(
        @Header("Authorization") autorizacion: String,
        @Body cuerpo: CallableRequest<IniciarPagoRequestDto>,
    ): Response<CallableResponse<IniciarPagoResultadoDto>>

    @POST("estadoPago")
    suspend fun estadoPago(
        @Header("Authorization") autorizacion: String,
        @Body cuerpo: CallableRequest<EstadoPagoRequestDto>,
    ): Response<CallableResponse<EstadoPagoResultadoDto>>

    @POST("conectarMercadoPago")
    suspend fun conectarMercadoPago(
        @Header("Authorization") autorizacion: String,
        @Body cuerpo: CallableRequest<ConectarMercadoPagoRequestDto>,
    ): Response<CallableResponse<ConectarMercadoPagoResultadoDto>>
}
