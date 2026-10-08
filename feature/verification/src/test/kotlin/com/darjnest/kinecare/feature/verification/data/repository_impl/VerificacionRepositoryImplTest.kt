package com.darjnest.kinecare.feature.verification.data.repository_impl

import com.darjnest.kinecare.core.common.domain.model.EstadoVerificacion
import com.darjnest.kinecare.core.common.result.Result
import com.darjnest.kinecare.core.network.functions.CloudFunctionsApi
import com.darjnest.kinecare.core.network.functions.dto.CallableRequest
import com.darjnest.kinecare.core.network.functions.dto.CallableResponse
import com.darjnest.kinecare.core.network.functions.dto.EstadoVerificacionRequestDto
import com.darjnest.kinecare.core.network.functions.dto.EstadoVerificacionResultadoDto
import com.darjnest.kinecare.core.network.functions.dto.SolicitarVerificacionRequestDto
import com.darjnest.kinecare.core.network.functions.dto.SolicitarVerificacionResultadoDto
import com.darjnest.kinecare.feature.verification.domain.EstadoSolicitudVerificacion
import com.darjnest.kinecare.feature.verification.domain.EstadoVerificacionError
import com.darjnest.kinecare.feature.verification.domain.IntentoVerificacion
import com.darjnest.kinecare.feature.verification.domain.MotivoRechazoVerificacion
import com.darjnest.kinecare.feature.verification.domain.SolicitarVerificacionError
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

class VerificacionRepositoryImplTest {

    private val firebaseAuth = mockk<FirebaseAuth>()
    private val api = mockk<CloudFunctionsApi>()
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }
    private val repository = VerificacionRepositoryImpl(firebaseAuth, api, json)

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

    private fun respondeEstado(estado: String, solicitudId: String? = "sol-1", motivo: String? = null) {
        coEvery { api.estadoVerificacion(any(), any()) } returns
            Response.success(CallableResponse(EstadoVerificacionResultadoDto(estado, solicitudId, motivo)))
    }

    // ── solicitar ────────────────────────────────────────────────────────────────────────

    @Test
    fun `solicitar pide la verificacion de IDENTIDAD con el token y retorna la URL`() = runTest {
        conSesion()
        val autorizacion = slot<String>()
        val cuerpo = slot<CallableRequest<SolicitarVerificacionRequestDto>>()
        coEvery { api.solicitarVerificacion(capture(autorizacion), capture(cuerpo)) } returns
            Response.success(
                CallableResponse(
                    SolicitarVerificacionResultadoDto("sol-1", "https://verify.didit.me/session/abc", "PENDIENTE"),
                ),
            )

        val resultado = repository.solicitar()

        assertEquals(
            Result.Success(IntentoVerificacion("sol-1", "https://verify.didit.me/session/abc")),
            resultado,
        )
        assertEquals("Bearer token-123", autorizacion.captured)
        assertEquals(SolicitarVerificacionRequestDto("IDENTIDAD"), cuerpo.captured.data)
    }

    @Test
    fun `solicitar sin usuario autenticado retorna SIN_SESION sin llamar a la funcion`() = runTest {
        every { firebaseAuth.currentUser } returns null

        assertEquals(Result.Error(SolicitarVerificacionError.SIN_SESION), repository.solicitar())
        coVerify(exactly = 0) { api.solicitarVerificacion(any(), any()) }
    }

    @Test
    fun `solicitar sin token de ID retorna SIN_SESION`() = runTest {
        conSesion(token = null)

        assertEquals(Result.Error(SolicitarVerificacionError.SIN_SESION), repository.solicitar())
    }

    @ParameterizedTest
    @EnumSource(
        value = SolicitarVerificacionError::class,
        names = ["SIN_INTERNET", "DESCONOCIDO"],
        mode = EnumSource.Mode.EXCLUDE,
    )
    fun `solicitar mapea cada motivo de la funcion a su error`(esperado: SolicitarVerificacionError) = runTest {
        conSesion()
        coEvery { api.solicitarVerificacion(any(), any()) } returns
            respuestaError(400, cuerpoDeError("FAILED_PRECONDITION", esperado.name))

        assertEquals(Result.Error(esperado), repository.solicitar())
    }

    @Test
    fun `solicitar sin motivo cae al status canonico`() = runTest {
        conSesion()
        coEvery { api.solicitarVerificacion(any(), any()) } returns
            respuestaError(503, """{"error":{"status":"UNAVAILABLE","message":"x"}}""")
        assertEquals(Result.Error(SolicitarVerificacionError.PROVEEDOR_NO_DISPONIBLE), repository.solicitar())

        coEvery { api.solicitarVerificacion(any(), any()) } returns
            respuestaError(403, """{"error":{"status":"PERMISSION_DENIED","message":"x"}}""")
        assertEquals(Result.Error(SolicitarVerificacionError.NO_ES_PROFESIONAL), repository.solicitar())

        coEvery { api.solicitarVerificacion(any(), any()) } returns
            respuestaError(401, """{"error":{"status":"UNAUTHENTICATED","message":"x"}}""")
        assertEquals(Result.Error(SolicitarVerificacionError.SIN_SESION), repository.solicitar())
    }

    @Test
    fun `solicitar con un motivo desconocido o un cuerpo que no es de callable es DESCONOCIDO`() = runTest {
        conSesion()
        coEvery { api.solicitarVerificacion(any(), any()) } returns
            respuestaError(400, cuerpoDeError("FAILED_PRECONDITION", "MOTIVO_NUEVO"))
        assertEquals(Result.Error(SolicitarVerificacionError.DESCONOCIDO), repository.solicitar())

        coEvery { api.solicitarVerificacion(any(), any()) } returns respuestaError(404, "<html>Not Found</html>")
        assertEquals(Result.Error(SolicitarVerificacionError.DESCONOCIDO), repository.solicitar())
    }

    @Test
    fun `solicitar con error de red retorna SIN_INTERNET y con otra excepcion DESCONOCIDO`() = runTest {
        conSesion()
        coEvery { api.solicitarVerificacion(any(), any()) } throws IOException("sin red")
        assertEquals(Result.Error(SolicitarVerificacionError.SIN_INTERNET), repository.solicitar())

        coEvery { api.solicitarVerificacion(any(), any()) } throws IllegalStateException("boom")
        assertEquals(Result.Error(SolicitarVerificacionError.DESCONOCIDO), repository.solicitar())
    }

    // ── consultarEstado ──────────────────────────────────────────────────────────────────

    @ParameterizedTest
    @EnumSource(EstadoVerificacion::class)
    fun `consultarEstado traduce cada estado del backend`(estado: EstadoVerificacion) = runTest {
        conSesion()
        respondeEstado(estado.name)

        assertEquals(
            Result.Success(EstadoSolicitudVerificacion(estado, "sol-1", motivo = null)),
            repository.consultarEstado("sol-1"),
        )
    }

    @Test
    fun `consultarEstado envia el solicitudId y omite el campo cuando es null`() = runTest {
        conSesion()
        val cuerpo = slot<CallableRequest<EstadoVerificacionRequestDto>>()
        coEvery { api.estadoVerificacion(any(), capture(cuerpo)) } returns
            Response.success(CallableResponse(EstadoVerificacionResultadoDto("NO_SOLICITADO")))

        repository.consultarEstado("sol-9")
        assertEquals(EstadoVerificacionRequestDto("sol-9"), cuerpo.captured.data)

        val resultado = repository.consultarEstado(null)
        assertEquals(EstadoVerificacionRequestDto(null), cuerpo.captured.data)
        assertEquals(Result.Success(EstadoSolicitudVerificacion(EstadoVerificacion.NO_SOLICITADO)), resultado)
        assertEquals("""{"data":{}}""", json.encodeToString(CallableRequest.serializer(EstadoVerificacionRequestDto.serializer()), cuerpo.captured))
    }

    @ParameterizedTest
    @EnumSource(MotivoRechazoVerificacion::class)
    fun `consultarEstado RECHAZADO traduce cada motivo`(motivo: MotivoRechazoVerificacion) = runTest {
        conSesion()
        respondeEstado("RECHAZADO", motivo = motivo.name)

        assertEquals(
            Result.Success(EstadoSolicitudVerificacion(EstadoVerificacion.RECHAZADO, "sol-1", motivo)),
            repository.consultarEstado("sol-1"),
        )
    }

    @Test
    fun `consultarEstado RECHAZADO con un motivo desconocido o ausente sigue siendo RECHAZADO sin motivo`() = runTest {
        conSesion()
        respondeEstado("RECHAZADO", motivo = "MOTIVO_NUEVO")
        assertEquals(
            Result.Success(EstadoSolicitudVerificacion(EstadoVerificacion.RECHAZADO, "sol-1", null)),
            repository.consultarEstado("sol-1"),
        )

        respondeEstado("RECHAZADO", motivo = null)
        assertEquals(
            Result.Success(EstadoSolicitudVerificacion(EstadoVerificacion.RECHAZADO, "sol-1", null)),
            repository.consultarEstado("sol-1"),
        )
    }

    @Test
    fun `consultarEstado ignora el motivo si el estado no es RECHAZADO`() = runTest {
        conSesion()
        respondeEstado("APROBADO", motivo = "DECLINED")

        assertEquals(
            Result.Success(EstadoSolicitudVerificacion(EstadoVerificacion.APROBADO, "sol-1", null)),
            repository.consultarEstado("sol-1"),
        )
    }

    @Test
    fun `consultarEstado con un estado desconocido es DESCONOCIDO y no lanza`() = runTest {
        conSesion()
        respondeEstado("EN_VUELO")
        assertEquals(Result.Error(EstadoVerificacionError.DESCONOCIDO), repository.consultarEstado("sol-1"))

        respondeEstado("")
        assertEquals(Result.Error(EstadoVerificacionError.DESCONOCIDO), repository.consultarEstado("sol-1"))
    }

    @ParameterizedTest
    @EnumSource(
        value = EstadoVerificacionError::class,
        names = ["SIN_INTERNET", "DESCONOCIDO"],
        mode = EnumSource.Mode.EXCLUDE,
    )
    fun `consultarEstado mapea cada motivo de la funcion a su error`(esperado: EstadoVerificacionError) = runTest {
        conSesion()
        coEvery { api.estadoVerificacion(any(), any()) } returns respuestaError(404, cuerpoDeError("NOT_FOUND", esperado.name))

        assertEquals(Result.Error(esperado), repository.consultarEstado("sol-1"))
    }

    @Test
    fun `consultarEstado sin motivo cae al status canonico`() = runTest {
        conSesion()
        coEvery { api.estadoVerificacion(any(), any()) } returns
            respuestaError(404, """{"error":{"status":"NOT_FOUND","message":"x"}}""")

        assertEquals(Result.Error(EstadoVerificacionError.SOLICITUD_NO_ENCONTRADA), repository.consultarEstado("sol-1"))
    }

    @Test
    fun `consultarEstado sin usuario retorna SIN_SESION sin llamar a la funcion`() = runTest {
        every { firebaseAuth.currentUser } returns null

        assertEquals(Result.Error(EstadoVerificacionError.SIN_SESION), repository.consultarEstado(null))
        coVerify(exactly = 0) { api.estadoVerificacion(any(), any()) }
    }

    @Test
    fun `consultarEstado con error de red retorna SIN_INTERNET y con funcion no desplegada DESCONOCIDO`() = runTest {
        conSesion()
        coEvery { api.estadoVerificacion(any(), any()) } throws IOException("sin red")
        assertEquals(Result.Error(EstadoVerificacionError.SIN_INTERNET), repository.consultarEstado(null))

        coEvery { api.estadoVerificacion(any(), any()) } returns respuestaError(404, "<html>Not Found</html>")
        assertEquals(Result.Error(EstadoVerificacionError.DESCONOCIDO), repository.consultarEstado(null))
    }
}
