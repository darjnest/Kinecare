package com.darjnest.kinecare.core.network.firebase.repository

import com.darjnest.kinecare.core.common.data.error.CompletarReservaError
import com.darjnest.kinecare.core.common.domain.model.EstadoReserva
import com.darjnest.kinecare.core.common.result.Result
import com.darjnest.kinecare.core.network.functions.CloudFunctionsApi
import com.darjnest.kinecare.core.network.functions.dto.CallableRequest
import com.darjnest.kinecare.core.network.functions.dto.CallableResponse
import com.darjnest.kinecare.core.network.functions.dto.CompletarReservaRequestDto
import com.darjnest.kinecare.core.network.functions.dto.CompletarReservaResultadoDto
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

class ReservaRepositoryImplCompletarTest {

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

    private fun respuestaError(codigoHttp: Int, cuerpo: String): Response<CallableResponse<CompletarReservaResultadoDto>> =
        Response.error(codigoHttp, cuerpo.toResponseBody("application/json".toMediaType()))

    @Test
    fun `completar envia el id con el token y retorna COMPLETADA`() = runTest {
        conSesion()
        val autorizacion = slot<String>()
        val cuerpo = slot<CallableRequest<CompletarReservaRequestDto>>()
        coEvery { api.completarReserva(capture(autorizacion), capture(cuerpo)) } returns
            Response.success(CallableResponse(CompletarReservaResultadoDto("COMPLETADA")))

        val resultado = repository.completar("res-1")

        assertEquals(Result.Success(EstadoReserva.COMPLETADA), resultado)
        assertEquals("Bearer token-123", autorizacion.captured)
        assertEquals(CompletarReservaRequestDto("res-1"), cuerpo.captured.data)
    }

    @Test
    fun `un estado de respuesta desconocido es DESCONOCIDO`() = runTest {
        conSesion()
        coEvery { api.completarReserva(any(), any()) } returns
            Response.success(CallableResponse(CompletarReservaResultadoDto("OTRO")))

        assertEquals(Result.Error(CompletarReservaError.DESCONOCIDO), repository.completar("res-1"))
    }

    @Test
    fun `sin usuario autenticado retorna SIN_SESION sin llamar a la funcion`() = runTest {
        every { firebaseAuth.currentUser } returns null

        assertEquals(Result.Error(CompletarReservaError.SIN_SESION), repository.completar("res-1"))
        coVerify(exactly = 0) { api.completarReserva(any(), any()) }
    }

    @ParameterizedTest
    @EnumSource(
        value = CompletarReservaError::class,
        names = ["SIN_INTERNET", "DESCONOCIDO"],
        mode = EnumSource.Mode.EXCLUDE,
    )
    fun `mapea cada motivo de la funcion a su error`(esperado: CompletarReservaError) = runTest {
        conSesion()
        coEvery { api.completarReserva(any(), any()) } returns respuestaError(
            400,
            """{"error":{"status":"FAILED_PRECONDITION","message":"x","details":{"motivo":"${esperado.name}"}}}""",
        )

        assertEquals(Result.Error(esperado), repository.completar("res-1"))
    }

    @Test
    fun `sin motivo cae al status canonico`() = runTest {
        conSesion()
        coEvery { api.completarReserva(any(), any()) } returns
            respuestaError(400, """{"error":{"status":"FAILED_PRECONDITION","message":"x"}}""")

        assertEquals(Result.Error(CompletarReservaError.RESERVA_NO_COMPLETABLE), repository.completar("res-1"))
    }

    @Test
    fun `un cuerpo que no es de callable (funcion no desplegada) es DESCONOCIDO`() = runTest {
        conSesion()
        coEvery { api.completarReserva(any(), any()) } returns respuestaError(404, "<html>Not Found</html>")

        assertEquals(Result.Error(CompletarReservaError.DESCONOCIDO), repository.completar("res-1"))
    }

    @Test
    fun `un error de red retorna SIN_INTERNET`() = runTest {
        conSesion()
        coEvery { api.completarReserva(any(), any()) } throws IOException("sin red")

        assertEquals(Result.Error(CompletarReservaError.SIN_INTERNET), repository.completar("res-1"))
    }
}
