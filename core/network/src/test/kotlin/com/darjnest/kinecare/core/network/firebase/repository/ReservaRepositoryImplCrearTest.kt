@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.darjnest.kinecare.core.network.firebase.repository

import com.darjnest.kinecare.core.common.data.error.CrearReservaError
import com.darjnest.kinecare.core.common.domain.model.Direccion
import com.darjnest.kinecare.core.common.domain.model.SolicitudReserva
import com.darjnest.kinecare.core.common.result.Result
import com.darjnest.kinecare.core.network.functions.CloudFunctionsApi
import com.darjnest.kinecare.core.network.functions.dto.CallableRequest
import com.darjnest.kinecare.core.network.functions.dto.CallableResponse
import com.darjnest.kinecare.core.network.functions.dto.CrearReservaRequestDto
import com.darjnest.kinecare.core.network.functions.dto.CrearReservaResultadoDto
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GetTokenResult
import com.google.firebase.firestore.FirebaseFirestore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import retrofit2.Response
import java.io.IOException

class ReservaRepositoryImplCrearTest {

    private val firebaseAuth = mockk<FirebaseAuth>()
    private val api = mockk<CloudFunctionsApi>()
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }
    private val repository = ReservaRepositoryImpl(mockk<FirebaseFirestore>(), firebaseAuth, api, json)

    private val solicitud = SolicitudReserva(
        profesionalId = "prof-1",
        servicioId = "serv-1",
        fechaHora = Instant.parse("2026-10-05T13:00:00Z"),
        direccion = null,
    )

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

    private fun respuestaError(codigoHttp: Int, cuerpo: String): Response<CallableResponse<CrearReservaResultadoDto>> =
        Response.error(codigoHttp, cuerpo.toResponseBody("application/json".toMediaType()))

    @Test
    fun `envia la solicitud con el token y retorna el id de la reserva creada`() = runTest {
        conSesion()
        val autorizacion = slot<String>()
        val cuerpo = slot<CallableRequest<CrearReservaRequestDto>>()
        coEvery { api.crearReserva(capture(autorizacion), capture(cuerpo)) } returns
            Response.success(CallableResponse(CrearReservaResultadoDto("res-9")))

        val resultado = repository.crear(solicitud)

        assertEquals(Result.Success("res-9"), resultado)
        assertEquals("Bearer token-123", autorizacion.captured)
        assertEquals("prof-1", cuerpo.captured.data.profesionalId)
        assertEquals("serv-1", cuerpo.captured.data.servicioId)
        assertEquals("2026-10-05T13:00:00Z", cuerpo.captured.data.fechaHora)
        assertNull(cuerpo.captured.data.direccion)
    }

    @Test
    fun `mapea la direccion de un servicio a domicilio`() = runTest {
        conSesion()
        val cuerpo = slot<CallableRequest<CrearReservaRequestDto>>()
        coEvery { api.crearReserva(any(), capture(cuerpo)) } returns
            Response.success(CallableResponse(CrearReservaResultadoDto("res-9")))
        val direccion = Direccion("Av. Siempre Viva", "742", "Providencia", "Santiago", null, null, "Depto 3")

        repository.crear(solicitud.copy(direccion = direccion))

        val dto = cuerpo.captured.data.direccion!!
        assertEquals("Av. Siempre Viva", dto.calle)
        assertEquals("742", dto.numero)
        assertEquals("Providencia", dto.comuna)
        assertEquals("Santiago", dto.ciudad)
        assertEquals("Depto 3", dto.indicaciones)
    }

    @Test
    fun `sin usuario autenticado retorna SIN_SESION sin llamar a la funcion`() = runTest {
        every { firebaseAuth.currentUser } returns null

        assertEquals(Result.Error(CrearReservaError.SIN_SESION), repository.crear(solicitud))
        coVerify(exactly = 0) { api.crearReserva(any(), any()) }
    }

    @Test
    fun `sin token retorna SIN_SESION`() = runTest {
        conSesion(token = null)

        assertEquals(Result.Error(CrearReservaError.SIN_SESION), repository.crear(solicitud))
    }

    @ParameterizedTest
    @EnumSource(
        value = CrearReservaError::class,
        names = ["SIN_INTERNET", "DESCONOCIDO"],
        mode = EnumSource.Mode.EXCLUDE,
    )
    fun `mapea cada motivo de la funcion a su error`(esperado: CrearReservaError) = runTest {
        conSesion()
        coEvery { api.crearReserva(any(), any()) } returns respuestaError(
            400,
            """{"error":{"status":"FAILED_PRECONDITION","message":"x","details":{"motivo":"${esperado.name}"}}}""",
        )

        assertEquals(Result.Error(esperado), repository.crear(solicitud))
    }

    @Test
    fun `sin motivo cae al status canonico`() = runTest {
        conSesion()
        coEvery { api.crearReserva(any(), any()) } returns
            respuestaError(409, """{"error":{"status":"ALREADY_EXISTS","message":"x"}}""")

        assertEquals(Result.Error(CrearReservaError.HORARIO_OCUPADO), repository.crear(solicitud))
    }

    @Test
    fun `un cuerpo que no es de callable (funcion no desplegada) es DESCONOCIDO`() = runTest {
        conSesion()
        coEvery { api.crearReserva(any(), any()) } returns respuestaError(404, "<html>Not Found</html>")

        assertEquals(Result.Error(CrearReservaError.DESCONOCIDO), repository.crear(solicitud))
    }

    @Test
    fun `un motivo desconocido con status desconocido es DESCONOCIDO`() = runTest {
        conSesion()
        coEvery { api.crearReserva(any(), any()) } returns respuestaError(
            500,
            """{"error":{"status":"INTERNAL","message":"x","details":{"motivo":"OTRO"}}}""",
        )

        assertEquals(Result.Error(CrearReservaError.DESCONOCIDO), repository.crear(solicitud))
    }

    @Test
    fun `un error de red retorna SIN_INTERNET`() = runTest {
        conSesion()
        coEvery { api.crearReserva(any(), any()) } throws IOException("sin red")

        assertEquals(Result.Error(CrearReservaError.SIN_INTERNET), repository.crear(solicitud))
    }
}
