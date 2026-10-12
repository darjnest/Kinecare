package com.darjnest.kinecare.core.network.firebase.repository

import com.darjnest.kinecare.core.common.data.error.DispositivoPushError
import com.darjnest.kinecare.core.common.result.Result
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import io.mockk.verify
import io.mockk.verifyOrder
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class DispositivoPushRepositoryImplTest {

    private val firestore = mockk<FirebaseFirestore>()
    private val auth = mockk<FirebaseAuth>()
    private val messaging = mockk<FirebaseMessaging>()
    private val repository = DispositivoPushRepositoryImpl(firestore, auth, messaging)

    private val documento = mockk<DocumentReference>()

    @BeforeEach
    fun setUp() {
        mockkStatic("kotlinx.coroutines.tasks.TasksKt")
        val usuarios = mockk<CollectionReference>()
        val dispositivos = mockk<CollectionReference>()
        val usuario = mockk<DocumentReference>()
        every { firestore.collection("usuarios") } returns usuarios
        every { usuarios.document("uid-1") } returns usuario
        every { usuario.collection("dispositivos") } returns dispositivos
        every { dispositivos.document("tok-1") } returns documento
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic("kotlinx.coroutines.tasks.TasksKt")
    }

    private fun conSesion(uid: String? = "uid-1") {
        every { auth.currentUser } returns uid?.let { id -> mockk<FirebaseUser>().also { every { it.uid } returns id } }
    }

    private fun conToken(token: String? = "tok-1") {
        // FCM puede resolver la tarea con nulo aunque el tipo de Java no lo declare.
        val tarea = mockk<Task<String?>>()
        coEvery { tarea.await() } returns token
        @Suppress("UNCHECKED_CAST")
        every { messaging.token } returns (tarea as Task<String>)
    }

    private fun tareaVacia(error: Exception? = null): Task<Void> {
        val tarea = mockk<Task<Void>>()
        if (error != null) coEvery { tarea.await() } throws error else coEvery { tarea.await() } returns mockk()
        return tarea
    }

    @Test
    fun `registra el token actual en usuarios uid dispositivos token con plataforma ANDROID`() = runTest {
        conSesion()
        conToken()
        val datos = slot<Map<String, Any>>()
        every { documento.set(capture(datos)) } returns tareaVacia()

        val resultado = repository.registrarDispositivoActual()

        assertEquals(Result.Success(Unit), resultado)
        assertEquals("ANDROID", datos.captured["plataforma"])
        assertTrue(datos.captured.containsKey("actualizadoEn"))
        assertEquals(setOf("plataforma", "actualizadoEn"), datos.captured.keys)
    }

    @Test
    fun `sin sesion no registra nada`() = runTest {
        conSesion(null)

        assertEquals(Result.Error(DispositivoPushError.SIN_SESION), repository.registrarDispositivoActual())
        assertEquals(Result.Error(DispositivoPushError.SIN_SESION), repository.registrarToken("tok-1"))
        verify(exactly = 0) { firestore.collection(any()) }
    }

    @Test
    fun `si FCM no entrega token no escribe y avisa`() = runTest {
        conSesion()
        conToken(null)

        assertEquals(Result.Error(DispositivoPushError.SIN_TOKEN), repository.registrarDispositivoActual())
        verify(exactly = 0) { documento.set(any()) }
    }

    @Test
    fun `onNewToken registra el token recibido para el usuario con sesion`() = runTest {
        conSesion()
        every { documento.set(any()) } returns tareaVacia()

        val resultado = repository.registrarToken("tok-1")

        assertEquals(Result.Success(Unit), resultado)
        verify { documento.set(any()) }
    }

    @Test
    fun `un token vacio no se registra`() = runTest {
        conSesion()

        assertEquals(Result.Error(DispositivoPushError.SIN_TOKEN), repository.registrarToken(" "))
    }

    @Test
    fun `la falta de red se traduce a SIN_INTERNET`() = runTest {
        conSesion()
        every { documento.set(any()) } returns tareaVacia(FirebaseNetworkException("sin red"))

        assertEquals(Result.Error(DispositivoPushError.SIN_INTERNET), repository.registrarToken("tok-1"))
    }

    @Test
    fun `al cerrar sesion borra el documento del token y luego invalida el token de FCM`() = runTest {
        conSesion()
        conToken()
        every { documento.delete() } returns tareaVacia()
        every { messaging.deleteToken() } returns tareaVacia()

        val resultado = repository.eliminarDispositivoActual()

        assertEquals(Result.Success(Unit), resultado)
        verifyOrder {
            documento.delete()
            messaging.deleteToken()
        }
    }

    @Test
    fun `si el borrado falla igual invalida el token de FCM y reporta el error`() = runTest {
        conSesion()
        conToken()
        every { documento.delete() } returns tareaVacia(RuntimeException("denegado"))
        every { messaging.deleteToken() } returns tareaVacia()

        val resultado = repository.eliminarDispositivoActual()

        assertEquals(Result.Error(DispositivoPushError.DESCONOCIDO), resultado)
        verify { messaging.deleteToken() }
    }

    @Test
    fun `un fallo al invalidar el token de FCM no impide cerrar sesion`() = runTest {
        conSesion()
        conToken()
        every { documento.delete() } returns tareaVacia()
        every { messaging.deleteToken() } returns tareaVacia(RuntimeException("sin red"))

        assertEquals(Result.Success(Unit), repository.eliminarDispositivoActual())
    }

    @Test
    fun `eliminar sin sesion no hace nada`() = runTest {
        conSesion(null)

        assertEquals(Result.Error(DispositivoPushError.SIN_SESION), repository.eliminarDispositivoActual())
        coVerify(exactly = 0) { messaging.deleteToken() }
    }
}
