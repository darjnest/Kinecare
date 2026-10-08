package com.darjnest.kinecare.feature.payment.data.repository_impl

import com.darjnest.kinecare.feature.payment.domain.ConectarMercadoPagoError
import com.darjnest.kinecare.feature.payment.domain.EstadoPagoError
import com.darjnest.kinecare.feature.payment.domain.IniciarPagoError
import com.darjnest.kinecare.core.common.domain.model.EstadoPago
import com.darjnest.kinecare.feature.payment.domain.IntentoPago
import com.darjnest.kinecare.core.common.result.Result
import com.darjnest.kinecare.core.network.functions.CloudFunctionsApi
import com.darjnest.kinecare.core.network.functions.dto.CallableRequest
import com.darjnest.kinecare.core.network.functions.dto.CallableResponse
import com.darjnest.kinecare.core.network.functions.dto.ConectarMercadoPagoResultadoDto
import com.darjnest.kinecare.core.network.functions.dto.EstadoPagoRequestDto
import com.darjnest.kinecare.core.network.functions.dto.EstadoPagoResultadoDto
import com.darjnest.kinecare.core.network.functions.dto.IniciarPagoRequestDto
import com.darjnest.kinecare.core.network.functions.dto.IniciarPagoResultadoDto
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GetTokenResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import retrofit2.Response
import java.io.IOException

class PagoRepositoryImplTest {

    private val firebaseAuth = mockk<FirebaseAuth>()
    private val api = mockk<CloudFunctionsApi>()
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }
    private val repository = PagoRepositoryImpl(firebaseAuth, api, json)

    @BeforeEach
    fun setUp() {
        mockkStatic("kotlinx.coroutines.tasks.TasksKt")
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic("kotlinx.coroutines.tasks.TasksKt")
    }

    private fun conSesion(token: String? = "token-123") {
        val usuario = mockk<FirebaseUser>()
        val tarea = mockk<Task<GetTokenResult>>()
        val resultado = mockk<GetTokenResult>()
        every { resultado.token } returns token
        every { usuario.getIdToken(false) } returns tarea
        coEvery { tarea.await() } returns resultado
        every { firebaseAuth.currentUser } returns usuario
    }

    private fun <T> respuestaError(codigoHttp: Int, cuerpo: String): Response<CallableResponse<T>> =
        Response.error(codigoHttp, cuerpo.toResponseBody("application/json".toMediaType()))

    private fun cuerpoDeError(status: String, motivo: String) =
        """{"error":{"status":"$status","message":"x","details":{"motivo":"$motivo"}}}"""

    // ── iniciar ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `iniciar envia solo la reserva con el token y retorna el intento de pago`() = runTest {
        conSesion()
        val autorizacion = slot<String>()
        val cuerpo = slot<CallableRequest<IniciarPagoRequestDto>>()
        coEvery { api.iniciarPago(capture(autorizacion), capture(cuerpo)) } returns
            Response.success(CallableResponse(IniciarPagoResultadoDto("pago-1", "https://www.mercadopago.cl/checkout/v1/redirect?pref_id=1")))

        val resultado = repository.iniciar("res-1")

        assertEquals(Result.Success(IntentoPago("pago-1", "https://www.mercadopago.cl/checkout/v1/redirect?pref_id=1")), resultado)
        assertEquals("Bearer token-123", autorizacion.captured)
        assertEquals(IniciarPagoRequestDto("res-1"), cuerpo.captured.data)
    }

    @Test
    fun `iniciar sin usuario autenticado retorna SIN_SESION sin llamar a la funcion`() = runTest {
        every { firebaseAuth.currentUser } returns null

        assertEquals(Result.Error(IniciarPagoError.SIN_SESION), repository.iniciar("res-1"))
        coVerify(exactly = 0) { api.iniciarPago(any(), any()) }
    }

    @ParameterizedTest
    @EnumSource(value = IniciarPagoError::class, names = ["SIN_INTERNET", "DESCONOCIDO"], mode = EnumSource.Mode.EXCLUDE)
    fun `iniciar mapea cada motivo de la funcion a su error`(esperado: IniciarPagoError) = runTest {
        conSesion()
        coEvery { api.iniciarPago(any(), any()) } returns respuestaError(400, cuerpoDeError("FAILED_PRECONDITION", esperado.name))

        assertEquals(Result.Error(esperado), repository.iniciar("res-1"))
    }

    @Test
    fun `iniciar sin motivo cae al status canonico`() = runTest {
        conSesion()
        coEvery { api.iniciarPago(any(), any()) } returns
            respuestaError(503, """{"error":{"status":"UNAVAILABLE","message":"x"}}""")

        assertEquals(Result.Error(IniciarPagoError.PASARELA_NO_DISPONIBLE), repository.iniciar("res-1"))
    }

    @Test
    fun `iniciar con un cuerpo que no es de callable (funcion no desplegada) es DESCONOCIDO`() = runTest {
        conSesion()
        coEvery { api.iniciarPago(any(), any()) } returns respuestaError(404, "<html>Not Found</html>")

        assertEquals(Result.Error(IniciarPagoError.DESCONOCIDO), repository.iniciar("res-1"))
    }

    @Test
    fun `iniciar con error de red retorna SIN_INTERNET`() = runTest {
        conSesion()
        coEvery { api.iniciarPago(any(), any()) } throws IOException("sin red")

        assertEquals(Result.Error(IniciarPagoError.SIN_INTERNET), repository.iniciar("res-1"))
    }

    @Test
    fun `iniciar sin token de ID retorna SIN_SESION`() = runTest {
        conSesion(token = null)

        assertEquals(Result.Error(IniciarPagoError.SIN_SESION), repository.iniciar("res-1"))
    }

    // ── consultarEstado ──────────────────────────────────────────────────────────────────

    @ParameterizedTest
    @EnumSource(EstadoPago::class)
    fun `consultarEstado traduce cada estado del backend`(estado: EstadoPago) = runTest {
        conSesion()
        val cuerpo = slot<CallableRequest<EstadoPagoRequestDto>>()
        coEvery { api.estadoPago(any(), capture(cuerpo)) } returns
            Response.success(CallableResponse(EstadoPagoResultadoDto(estado.name)))

        assertEquals(Result.Success(estado), repository.consultarEstado("pago-1"))
        assertEquals(EstadoPagoRequestDto("pago-1"), cuerpo.captured.data)
    }

    @Test
    fun `consultarEstado con un estado desconocido es DESCONOCIDO`() = runTest {
        conSesion()
        coEvery { api.estadoPago(any(), any()) } returns Response.success(CallableResponse(EstadoPagoResultadoDto("OTRO")))

        assertEquals(Result.Error(EstadoPagoError.DESCONOCIDO), repository.consultarEstado("pago-1"))
    }

    @ParameterizedTest
    @EnumSource(value = EstadoPagoError::class, names = ["SIN_INTERNET", "DESCONOCIDO"], mode = EnumSource.Mode.EXCLUDE)
    fun `consultarEstado mapea cada motivo de la funcion a su error`(esperado: EstadoPagoError) = runTest {
        conSesion()
        coEvery { api.estadoPago(any(), any()) } returns respuestaError(404, cuerpoDeError("NOT_FOUND", esperado.name))

        assertEquals(Result.Error(esperado), repository.consultarEstado("pago-1"))
    }

    @Test
    fun `consultarEstado sin usuario retorna SIN_SESION`() = runTest {
        every { firebaseAuth.currentUser } returns null

        assertEquals(Result.Error(EstadoPagoError.SIN_SESION), repository.consultarEstado("pago-1"))
        coVerify(exactly = 0) { api.estadoPago(any(), any()) }
    }

    // ── obtenerUrlConexion ───────────────────────────────────────────────────────────────

    @Test
    fun `obtenerUrlConexion retorna la URL de autorizacion`() = runTest {
        conSesion()
        coEvery { api.conectarMercadoPago(any(), any()) } returns
            Response.success(CallableResponse(ConectarMercadoPagoResultadoDto("https://auth.mercadopago.com/authorization?state=x")))

        assertEquals(Result.Success("https://auth.mercadopago.com/authorization?state=x"), repository.obtenerUrlConexion())
    }

    @Test
    fun `obtenerUrlConexion con un cliente retorna ROL_INVALIDO`() = runTest {
        conSesion()
        coEvery { api.conectarMercadoPago(any(), any()) } returns
            respuestaError(403, cuerpoDeError("PERMISSION_DENIED", "ROL_INVALIDO"))

        assertEquals(Result.Error(ConectarMercadoPagoError.ROL_INVALIDO), repository.obtenerUrlConexion())
    }

    @Test
    fun `obtenerUrlConexion sin usuario retorna SIN_SESION y con red caida SIN_INTERNET`() = runTest {
        every { firebaseAuth.currentUser } returns null
        assertEquals(Result.Error(ConectarMercadoPagoError.SIN_SESION), repository.obtenerUrlConexion())

        conSesion()
        coEvery { api.conectarMercadoPago(any(), any()) } throws IOException("sin red")
        assertEquals(Result.Error(ConectarMercadoPagoError.SIN_INTERNET), repository.obtenerUrlConexion())
    }
}
