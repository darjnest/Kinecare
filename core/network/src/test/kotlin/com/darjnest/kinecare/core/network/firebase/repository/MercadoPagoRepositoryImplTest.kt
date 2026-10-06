@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.darjnest.kinecare.core.network.firebase.repository

import com.darjnest.kinecare.core.common.data.error.MercadoPagoError
import com.darjnest.kinecare.core.common.domain.model.EstadoMercadoPago
import com.darjnest.kinecare.core.common.result.Result
import com.darjnest.kinecare.core.network.functions.CloudFunctionsApi
import com.darjnest.kinecare.core.network.functions.dto.CallableRequest
import com.darjnest.kinecare.core.network.functions.dto.CallableResponse
import com.darjnest.kinecare.core.network.functions.dto.DesconectarMercadoPagoResultadoDto
import com.darjnest.kinecare.core.network.functions.dto.IniciarConexionMercadoPagoResultadoDto
import com.darjnest.kinecare.core.network.functions.dto.MercadoPagoSinDatosDto
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GetTokenResult
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import retrofit2.Response
import java.io.IOException

private const val COLECCION_MERCADO_PAGO_ESTADOS = "mercadoPagoEstados"

class MercadoPagoRepositoryImplTest {

    private val firestore = mockk<FirebaseFirestore>()
    private val firebaseAuth = mockk<FirebaseAuth>()
    private val api = mockk<CloudFunctionsApi>()
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }
    private val repository = MercadoPagoRepositoryImpl(firestore, firebaseAuth, api, json)

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

    private fun setUpGet(doc: DocumentSnapshot? = null, exception: Exception? = null) {
        val coleccion = mockk<CollectionReference>()
        val docRef = mockk<DocumentReference>()
        every { firestore.collection(COLECCION_MERCADO_PAGO_ESTADOS) } returns coleccion
        every { coleccion.document(any()) } returns docRef
        val task = mockk<Task<DocumentSnapshot>>()
        if (exception != null) {
            coEvery { task.await() } throws exception
        } else {
            coEvery { task.await() } returns doc!!
        }
        every { docRef.get() } returns task
    }

    private fun documento(exists: Boolean = true, conectado: Boolean? = true, conectadoEn: Timestamp? = null): DocumentSnapshot {
        val doc = mockk<DocumentSnapshot>()
        every { doc.exists() } returns exists
        every { doc.getBoolean("conectado") } returns conectado
        every { doc.getTimestamp("conectadoEn") } returns conectadoEn
        return doc
    }

    // region obtenerEstado

    @Test
    fun `documento existente y conectado retorna conectada con su fecha`() = runTest {
        setUpGet(documento(conectadoEn = Timestamp(1_700_000_000L, 0)))

        val resultado = repository.obtenerEstado("prof-1")

        assertEquals(
            Result.Success(EstadoMercadoPago(conectado = true, conectadoEn = Instant.fromEpochSeconds(1_700_000_000L))),
            resultado,
        )
    }

    @Test
    fun `documento conectado sin fecha retorna conectada con conectadoEn nulo`() = runTest {
        setUpGet(documento(conectadoEn = null))

        assertEquals(Result.Success(EstadoMercadoPago(conectado = true, conectadoEn = null)), repository.obtenerEstado("prof-1"))
    }

    @Test
    fun `documento ausente retorna no conectada`() = runTest {
        setUpGet(documento(exists = false, conectado = null))

        assertEquals(Result.Success(EstadoMercadoPago.NoConectada), repository.obtenerEstado("prof-1"))
    }

    @Test
    fun `documento con conectado distinto de true retorna no conectada`() = runTest {
        setUpGet(documento(conectado = false))

        assertEquals(Result.Success(EstadoMercadoPago.NoConectada), repository.obtenerEstado("prof-1"))
    }

    @Test
    fun `lee el documento del profesional en mercadoPagoEstados`() = runTest {
        val coleccion = mockk<CollectionReference>()
        val docRef = mockk<DocumentReference>()
        every { firestore.collection(COLECCION_MERCADO_PAGO_ESTADOS) } returns coleccion
        every { coleccion.document("prof-1") } returns docRef
        val task = mockk<Task<DocumentSnapshot>>()
        coEvery { task.await() } returns documento()
        every { docRef.get() } returns task

        repository.obtenerEstado("prof-1")

        io.mockk.verify { coleccion.document("prof-1") }
    }

    @Test
    fun `una falla de Firestore retorna DESCONOCIDO`() = runTest {
        setUpGet(exception = RuntimeException("boom"))

        assertEquals(Result.Error(MercadoPagoError.DESCONOCIDO), repository.obtenerEstado("prof-1"))
    }

    @Test
    fun `FirebaseNetworkException al leer retorna SIN_INTERNET`() = runTest {
        setUpGet(exception = FirebaseNetworkException("sin red"))

        assertEquals(Result.Error(MercadoPagoError.SIN_INTERNET), repository.obtenerEstado("prof-1"))
    }

    @Test
    fun `obtenerEstado relanza la cancelacion`() {
        setUpGet(exception = CancellationException("cancelado"))

        assertThrows(CancellationException::class.java) {
            kotlinx.coroutines.test.runTest { repository.obtenerEstado("prof-1") }
        }
    }

    // endregion

    // region iniciarConexion

    @Test
    fun `iniciarConexion envia el token y retorna la url de autorizacion`() = runTest {
        conSesion()
        val autorizacion = slot<String>()
        coEvery { api.iniciarConexionMercadoPago(capture(autorizacion), any()) } returns
            Response.success(CallableResponse(IniciarConexionMercadoPagoResultadoDto("https://auth.mercadopago.cl/x")))

        val resultado = repository.iniciarConexion()

        assertEquals(Result.Success("https://auth.mercadopago.cl/x"), resultado)
        assertEquals("Bearer token-123", autorizacion.captured)
    }

    @Test
    fun `el cuerpo de las callables sin parametros se serializa como data vacio`() {
        val cuerpo = json.encodeToString(CallableRequest(MercadoPagoSinDatosDto))

        assertEquals("""{"data":{}}""", cuerpo)
    }

    @Test
    fun `una url en blanco es DESCONOCIDO`() = runTest {
        conSesion()
        coEvery { api.iniciarConexionMercadoPago(any(), any()) } returns
            Response.success(CallableResponse(IniciarConexionMercadoPagoResultadoDto("  ")))

        assertEquals(Result.Error(MercadoPagoError.DESCONOCIDO), repository.iniciarConexion())
    }

    @ParameterizedTest
    @EnumSource(value = MercadoPagoError::class, names = ["SIN_INTERNET", "DESCONOCIDO"], mode = EnumSource.Mode.EXCLUDE)
    fun `iniciarConexion mapea cada motivo de la funcion a su error`(esperado: MercadoPagoError) = runTest {
        conSesion()
        coEvery { api.iniciarConexionMercadoPago(any(), any()) } returns respuestaError(
            403,
            """{"error":{"status":"PERMISSION_DENIED","message":"x","details":{"motivo":"${esperado.name}"}}}""",
        )

        assertEquals(Result.Error(esperado), repository.iniciarConexion())
    }

    @Test
    fun `sin motivo cae al status canonico`() = runTest {
        conSesion()
        coEvery { api.iniciarConexionMercadoPago(any(), any()) } returns
            respuestaError(401, """{"error":{"status":"UNAUTHENTICATED","message":"x"}}""")

        assertEquals(Result.Error(MercadoPagoError.SIN_SESION), repository.iniciarConexion())
    }

    @Test
    fun `un motivo desconocido es DESCONOCIDO`() = runTest {
        conSesion()
        coEvery { api.iniciarConexionMercadoPago(any(), any()) } returns respuestaError(
            500,
            """{"error":{"status":"INTERNAL","message":"x","details":{"motivo":"OTRO"}}}""",
        )

        assertEquals(Result.Error(MercadoPagoError.DESCONOCIDO), repository.iniciarConexion())
    }

    @Test
    fun `sin usuario autenticado retorna SIN_SESION sin llamar a la funcion`() = runTest {
        every { firebaseAuth.currentUser } returns null

        assertEquals(Result.Error(MercadoPagoError.SIN_SESION), repository.iniciarConexion())
        assertEquals(Result.Error(MercadoPagoError.SIN_SESION), repository.desconectar())
        coVerify(exactly = 0) { api.iniciarConexionMercadoPago(any(), any()) }
        coVerify(exactly = 0) { api.desconectarMercadoPago(any(), any()) }
    }

    @Test
    fun `un token nulo retorna SIN_SESION`() = runTest {
        conSesion(token = null)

        assertEquals(Result.Error(MercadoPagoError.SIN_SESION), repository.iniciarConexion())
        coVerify(exactly = 0) { api.iniciarConexionMercadoPago(any(), any()) }
    }

    @Test
    fun `un error de red retorna SIN_INTERNET`() = runTest {
        conSesion()
        coEvery { api.iniciarConexionMercadoPago(any(), any()) } throws IOException("sin red")

        assertEquals(Result.Error(MercadoPagoError.SIN_INTERNET), repository.iniciarConexion())
    }

    @Test
    fun `una excepcion inesperada retorna DESCONOCIDO`() = runTest {
        conSesion()
        coEvery { api.iniciarConexionMercadoPago(any(), any()) } throws IllegalStateException("boom")

        assertEquals(Result.Error(MercadoPagoError.DESCONOCIDO), repository.iniciarConexion())
    }

    @Test
    fun `un cuerpo que no es de callable (funcion no desplegada) es DESCONOCIDO`() = runTest {
        conSesion()
        coEvery { api.iniciarConexionMercadoPago(any(), any()) } returns respuestaError(404, "<html>Not Found</html>")

        assertEquals(Result.Error(MercadoPagoError.DESCONOCIDO), repository.iniciarConexion())
    }

    @Test
    fun `la cancelacion de la llamada se relanza y no se convierte en error`() {
        conSesion()
        coEvery { api.iniciarConexionMercadoPago(any(), any()) } throws CancellationException("cancelado")

        assertThrows(CancellationException::class.java) {
            kotlinx.coroutines.test.runTest { repository.iniciarConexion() }
        }
    }

    // endregion

    // region desconectar

    @Test
    fun `desconectar retorna Success cuando la funcion confirma`() = runTest {
        conSesion()
        val autorizacion = slot<String>()
        coEvery { api.desconectarMercadoPago(capture(autorizacion), any()) } returns
            Response.success(CallableResponse(DesconectarMercadoPagoResultadoDto(desconectado = true)))

        assertEquals(Result.Success(Unit), repository.desconectar())
        assertEquals("Bearer token-123", autorizacion.captured)
    }

    @Test
    fun `desconectar con desconectado false es DESCONOCIDO`() = runTest {
        conSesion()
        coEvery { api.desconectarMercadoPago(any(), any()) } returns
            Response.success(CallableResponse(DesconectarMercadoPagoResultadoDto(desconectado = false)))

        assertEquals(Result.Error(MercadoPagoError.DESCONOCIDO), repository.desconectar())
    }

    @Test
    fun `desconectar mapea el motivo ROL_INVALIDO`() = runTest {
        conSesion()
        coEvery { api.desconectarMercadoPago(any(), any()) } returns respuestaError(
            403,
            """{"error":{"status":"PERMISSION_DENIED","message":"x","details":{"motivo":"ROL_INVALIDO"}}}""",
        )

        assertEquals(Result.Error(MercadoPagoError.ROL_INVALIDO), repository.desconectar())
    }

    @Test
    fun `desconectar sin red retorna SIN_INTERNET`() = runTest {
        conSesion()
        coEvery { api.desconectarMercadoPago(any(), any()) } throws IOException("sin red")

        assertEquals(Result.Error(MercadoPagoError.SIN_INTERNET), repository.desconectar())
    }

    // endregion
}
