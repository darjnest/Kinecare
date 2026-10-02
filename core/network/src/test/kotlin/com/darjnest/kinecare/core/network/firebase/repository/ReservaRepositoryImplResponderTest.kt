package com.darjnest.kinecare.core.network.firebase.repository

import com.darjnest.kinecare.core.common.data.error.ResponderReservaError
import com.darjnest.kinecare.core.common.domain.model.EstadoReserva
import com.darjnest.kinecare.core.common.domain.model.RespuestaReserva
import com.darjnest.kinecare.core.common.result.Result
import com.darjnest.kinecare.core.network.functions.CloudFunctionsApi
import com.darjnest.kinecare.core.network.functions.dto.CallableRequest
import com.darjnest.kinecare.core.network.functions.dto.CallableResponse
import com.darjnest.kinecare.core.network.functions.dto.ResponderReservaRequestDto
import com.darjnest.kinecare.core.network.functions.dto.ResponderReservaResultadoDto
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

class ReservaRepositoryImplResponderTest {

    private val firebaseAuth = mockk<FirebaseAuth>()
    private val api = mockk<CloudFunctionsApi>()
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }
    private val repository = ReservaRepositoryImpl(mockk<FirebaseFirestore>(), firebaseAuth, api, json)

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

    private fun respuestaError(codigoHttp: Int, cuerpo: String): Response<CallableResponse<ResponderReservaResultadoDto>> =
        Response.error(codigoHttp, cuerpo.toResponseBody("application/json".toMediaType()))

    @Test
    fun `aceptar envia el id y la respuesta con el token y retorna CONFIRMADA`() = runTest {
        conSesion()
        val autorizacion = slot<String>()
        val cuerpo = slot<CallableRequest<ResponderReservaRequestDto>>()
        coEvery { api.responderReserva(capture(autorizacion), capture(cuerpo)) } returns
            Response.success(CallableResponse(ResponderReservaResultadoDto("CONFIRMADA")))

        val resultado = repository.responder("res-1", RespuestaReserva.ACEPTAR)

        assertEquals(Result.Success(EstadoReserva.CONFIRMADA), resultado)
        assertEquals("Bearer token-123", autorizacion.captured)
        assertEquals(ResponderReservaRequestDto("res-1", "ACEPTAR"), cuerpo.captured.data)
    }

    @Test
    fun `rechazar retorna RECHAZADA`() = runTest {
        conSesion()
        val cuerpo = slot<CallableRequest<ResponderReservaRequestDto>>()
        coEvery { api.responderReserva(any(), capture(cuerpo)) } returns
            Response.success(CallableResponse(ResponderReservaResultadoDto("RECHAZADA")))

        assertEquals(Result.Success(EstadoReserva.RECHAZADA), repository.responder("res-1", RespuestaReserva.RECHAZAR))
        assertEquals("RECHAZAR", cuerpo.captured.data.respuesta)
    }

    @Test
    fun `un estado de respuesta desconocido es DESCONOCIDO`() = runTest {
        conSesion()
        coEvery { api.responderReserva(any(), any()) } returns
            Response.success(CallableResponse(ResponderReservaResultadoDto("OTRO")))

        assertEquals(
            Result.Error(ResponderReservaError.DESCONOCIDO),
            repository.responder("res-1", RespuestaReserva.ACEPTAR),
        )
    }

    @Test
    fun `sin usuario autenticado retorna SIN_SESION sin llamar a la funcion`() = runTest {
        every { firebaseAuth.currentUser } returns null

        assertEquals(
            Result.Error(ResponderReservaError.SIN_SESION),
            repository.responder("res-1", RespuestaReserva.ACEPTAR),
        )
        coVerify(exactly = 0) { api.responderReserva(any(), any()) }
    }

    @ParameterizedTest
    @EnumSource(
        value = ResponderReservaError::class,
        names = ["SIN_INTERNET", "DESCONOCIDO"],
        mode = EnumSource.Mode.EXCLUDE,
    )
    fun `mapea cada motivo de la funcion a su error`(esperado: ResponderReservaError) = runTest {
        conSesion()
        coEvery { api.responderReserva(any(), any()) } returns respuestaError(
            400,
            """{"error":{"status":"FAILED_PRECONDITION","message":"x","details":{"motivo":"${esperado.name}"}}}""",
        )

        assertEquals(Result.Error(esperado), repository.responder("res-1", RespuestaReserva.ACEPTAR))
    }

    @Test
    fun `sin motivo cae al status canonico`() = runTest {
        conSesion()
        coEvery { api.responderReserva(any(), any()) } returns
            respuestaError(404, """{"error":{"status":"NOT_FOUND","message":"x"}}""")

        assertEquals(
            Result.Error(ResponderReservaError.RESERVA_NO_ENCONTRADA),
            repository.responder("res-1", RespuestaReserva.ACEPTAR),
        )
    }

    @Test
    fun `un cuerpo que no es de callable (funcion no desplegada) es DESCONOCIDO`() = runTest {
        conSesion()
        coEvery { api.responderReserva(any(), any()) } returns respuestaError(404, "<html>Not Found</html>")

        assertEquals(
            Result.Error(ResponderReservaError.DESCONOCIDO),
            repository.responder("res-1", RespuestaReserva.ACEPTAR),
        )
    }

    @Test
    fun `un error de red retorna SIN_INTERNET`() = runTest {
        conSesion()
        coEvery { api.responderReserva(any(), any()) } throws IOException("sin red")

        assertEquals(
            Result.Error(ResponderReservaError.SIN_INTERNET),
            repository.responder("res-1", RespuestaReserva.ACEPTAR),
        )
    }
}
