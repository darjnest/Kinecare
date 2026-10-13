@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.darjnest.kinecare.core.network.firebase.repository

import com.darjnest.kinecare.core.common.data.error.CuposOcupadosError
import com.darjnest.kinecare.core.common.domain.model.CupoOcupado
import com.darjnest.kinecare.core.common.result.Result
import com.darjnest.kinecare.core.network.functions.CloudFunctionsApi
import com.darjnest.kinecare.core.network.functions.dto.CallableRequest
import com.darjnest.kinecare.core.network.functions.dto.CallableResponse
import com.darjnest.kinecare.core.network.functions.dto.CuposOcupadosRequestDto
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
import kotlinx.serialization.json.JsonElement
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertTrue
import retrofit2.Response
import java.io.IOException

class ReservaRepositoryImplCuposOcupadosTest {

    private val firebaseAuth = mockk<FirebaseAuth>()
    private val api = mockk<CloudFunctionsApi>()
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }
    private val repository = ReservaRepositoryImpl(mockk<FirebaseFirestore>(), firebaseAuth, api, json)

    private val desde = Instant.parse("2026-10-12T00:00:00Z")
    private val hasta = Instant.parse("2026-10-26T00:00:00Z")

    private fun elemento(texto: String): JsonElement = json.parseToJsonElement(texto)

    @BeforeEach
    fun setUp() {
        mockkStatic("kotlinx.coroutines.tasks.TasksKt")
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic("kotlinx.coroutines.tasks.TasksKt")
    }

    private fun conSesion() {
        val usuario = mockk<FirebaseUser>()
        val tarea = mockk<Task<GetTokenResult>>()
        val resultado = mockk<GetTokenResult>()
        every { resultado.token } returns "token-123"
        every { usuario.getIdToken(false) } returns tarea
        coEvery { tarea.await() } returns resultado
        every { firebaseAuth.currentUser } returns usuario
    }

    @Test
    fun `envia profesional y ventana ISO con el token y mapea los tramos`() = runTest {
        conSesion()
        val autorizacion = slot<String>()
        val cuerpo = slot<CallableRequest<CuposOcupadosRequestDto>>()
        coEvery { api.obtenerCuposOcupados(capture(autorizacion), capture(cuerpo)) } returns
            Response.success(
                CallableResponse(elemento("""{"cupos":[{"inicio":"2026-10-14T13:00:00Z","fin":"2026-10-14T14:00:00Z"}]}""")),
            )

        val resultado = repository.obtenerCuposOcupados("prof-1", desde, hasta)

        assertEquals(
            Result.Success(
                listOf(CupoOcupado(Instant.parse("2026-10-14T13:00:00Z"), Instant.parse("2026-10-14T14:00:00Z"))),
            ),
            resultado,
        )
        assertEquals("Bearer token-123", autorizacion.captured)
        assertEquals(
            CuposOcupadosRequestDto("prof-1", "2026-10-12T00:00:00Z", "2026-10-26T00:00:00Z"),
            cuerpo.captured.data,
        )
    }

    @Test
    fun `acepta tambien una lista suelta sin el sobre cupos`() = runTest {
        conSesion()
        coEvery { api.obtenerCuposOcupados(any(), any()) } returns Response.success(
            CallableResponse(elemento("""[{"inicio":"2026-10-14T13:00:00Z","fin":"2026-10-14T14:00:00Z"}]""")),
        )

        val resultado = repository.obtenerCuposOcupados("prof-1", desde, hasta) as Result.Success

        assertEquals(1, resultado.data.size)
    }

    @Test
    fun `un cuerpo con otra forma es DESCONOCIDO`() = runTest {
        conSesion()
        coEvery { api.obtenerCuposOcupados(any(), any()) } returns
            Response.success(CallableResponse(elemento("""{"cupos":"nada"}""")))

        assertTrue(repository.obtenerCuposOcupados("prof-1", desde, hasta) is Result.Error)
    }

    @Test
    fun `omite los tramos con fechas ilegibles`() = runTest {
        conSesion()
        coEvery { api.obtenerCuposOcupados(any(), any()) } returns
            Response.success(
                CallableResponse(
                    elemento(
                        """[{"inicio":"basura","fin":"2026-10-14T14:00:00Z"},""" +
                            """{"inicio":"2026-10-15T13:00:00Z","fin":"2026-10-15T14:00:00Z"}]""",
                    ),
                ),
            )

        val resultado = repository.obtenerCuposOcupados("prof-1", desde, hasta) as Result.Success

        assertEquals(1, resultado.data.size)
    }

    @Test
    fun `una lista vacia es exito sin tramos`() = runTest {
        conSesion()
        coEvery { api.obtenerCuposOcupados(any(), any()) } returns Response.success(CallableResponse(elemento("""{"cupos":[]}""")))

        assertEquals(Result.Success(emptyList<CupoOcupado>()), repository.obtenerCuposOcupados("prof-1", desde, hasta))
    }

    @Test
    fun `sin usuario autenticado retorna SIN_SESION sin llamar a la funcion`() = runTest {
        every { firebaseAuth.currentUser } returns null

        assertEquals(Result.Error(CuposOcupadosError.SIN_SESION), repository.obtenerCuposOcupados("prof-1", desde, hasta))
        coVerify(exactly = 0) { api.obtenerCuposOcupados(any(), any()) }
    }

    @Test
    fun `un error de la funcion es DESCONOCIDO`() = runTest {
        conSesion()
        coEvery { api.obtenerCuposOcupados(any(), any()) } returns Response.error(
            400,
            """{"error":{"status":"INVALID_ARGUMENT","message":"x"}}""".toResponseBody("application/json".toMediaType()),
        )

        assertEquals(Result.Error(CuposOcupadosError.DESCONOCIDO), repository.obtenerCuposOcupados("prof-1", desde, hasta))
    }

    @Test
    fun `un error de red retorna SIN_INTERNET`() = runTest {
        conSesion()
        coEvery { api.obtenerCuposOcupados(any(), any()) } throws IOException("sin red")

        assertEquals(Result.Error(CuposOcupadosError.SIN_INTERNET), repository.obtenerCuposOcupados("prof-1", desde, hasta))
    }
}
