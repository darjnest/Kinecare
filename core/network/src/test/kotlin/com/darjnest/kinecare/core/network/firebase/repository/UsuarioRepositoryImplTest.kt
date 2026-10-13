package com.darjnest.kinecare.core.network.firebase.repository

import com.darjnest.kinecare.core.common.data.error.UsuarioError
import com.darjnest.kinecare.core.common.domain.model.PerfilPublico
import com.darjnest.kinecare.core.common.result.Result
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.Timestamp
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.WriteBatch
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

private const val COLECCION_USUARIOS = "usuarios"

class UsuarioRepositoryImplTest {

    private val firestore = mockk<FirebaseFirestore>()
    private val repository = UsuarioRepositoryImpl(firestore)

    @BeforeEach
    fun setUp() {
        mockkStatic("kotlinx.coroutines.tasks.TasksKt")
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic("kotlinx.coroutines.tasks.TasksKt")
    }

    @Test
    fun `retorna el usuario mapeado cuando el documento existe`() = runTest {
        val doc = mockDocumentSnapshot(
            data = mapOf(
                "nombre" to "Ana Soto",
                "rut" to "12345678-5",
                "email" to "ana@kinecare.cl",
                "correoContacto" to "ana.contacto@kinecare.cl",
                "telefono" to "+56911111111",
                "rol" to "CLIENTE",
                "fotoUrl" to "https://foto.com/ana.png",
                "fechaRegistro" to Timestamp(1_700_000_000L, 0),
            ),
        )
        setUpGet(doc)

        val resultado = repository.obtenerPorId("cliente-1")

        assertTrue(resultado is Result.Success)
        val usuario = (resultado as Result.Success).data
        assertEquals("Ana Soto", usuario.nombre)
        assertEquals("cliente-1", usuario.id)
    }

    @Test
    fun `retorna NO_ENCONTRADO cuando el documento no existe`() = runTest {
        setUpGet(mockDocumentSnapshot(exists = false, data = emptyMap()))

        val resultado = repository.obtenerPorId("cliente-1")

        assertTrue(resultado is Result.Error)
        assertEquals(UsuarioError.NO_ENCONTRADO, (resultado as Result.Error).error)
    }

    @Test
    fun `retorna SIN_INTERNET ante FirebaseNetworkException`() = runTest {
        setUpGet(exception = FirebaseNetworkException("sin conexion"))

        val resultado = repository.obtenerPorId("cliente-1")

        assertTrue(resultado is Result.Error)
        assertEquals(UsuarioError.SIN_INTERNET, (resultado as Result.Error).error)
    }

    @Test
    fun `actualizarDatosPersonales escribe usuarios y el nombre del perfil publico en un batch`() = runTest {
        val (batch, usuarioRef, perfilRef) = setUpBatch()

        val resultado = repository.actualizarDatosPersonales(
            id = "cliente-1",
            nombre = "Ana Soto",
            telefono = "+56911111111",
            correoContacto = "ana@kinecare.cl",
        )

        assertTrue(resultado is Result.Success)
        verify {
            batch.update(
                usuarioRef,
                mapOf(
                    "nombre" to "Ana Soto",
                    "telefono" to "+56911111111",
                    "correoContacto" to "ana@kinecare.cl",
                ),
            )
        }
        // El perfil publico solo lleva el nombre: telefono y correo no salen de `usuarios`.
        verify { batch.update(perfilRef, mapOf("nombre" to "Ana Soto")) }
        verify(exactly = 1) { batch.commit() }
    }

    @Test
    fun `actualizarFotoUrl escribe fotoUrl en usuarios y en el perfil publico`() = runTest {
        val (batch, usuarioRef, perfilRef) = setUpBatch()

        val resultado = repository.actualizarFotoUrl("cliente-1", "https://foto.com/nueva.jpg")

        assertTrue(resultado is Result.Success)
        verify { batch.update(usuarioRef, mapOf("fotoUrl" to "https://foto.com/nueva.jpg")) }
        verify { batch.update(perfilRef, mapOf("fotoUrl" to "https://foto.com/nueva.jpg")) }
        verify(exactly = 1) { batch.commit() }
    }

    @Test
    fun `actualizarFotoUrl retorna SIN_INTERNET ante FirebaseNetworkException`() = runTest {
        val (batch, _, _) = setUpBatch()
        val commitFallido = mockk<Task<Void>>()
        coEvery { commitFallido.await() } throws FirebaseNetworkException("sin conexion")
        every { batch.commit() } returns commitFallido

        val resultado = repository.actualizarFotoUrl("cliente-1", "https://foto.com/nueva.jpg")

        assertEquals(UsuarioError.SIN_INTERNET, (resultado as Result.Error).error)
    }

    @Test
    fun `obtenerPerfilPublico lee perfilesPublicos y mapea solo nombre y foto`() = runTest {
        val perfiles = mockk<CollectionReference>()
        val docRef = mockk<DocumentReference>()
        every { firestore.collection("perfilesPublicos") } returns perfiles
        every { perfiles.document("pro-1") } returns docRef
        val task = mockk<Task<DocumentSnapshot>>()
        coEvery { task.await() } returns mockDocumentSnapshot(
            data = mapOf("nombre" to "Ana Soto", "fotoUrl" to "https://foto.com/ana.png"),
        )
        every { docRef.get() } returns task

        val resultado = repository.obtenerPerfilPublico("pro-1")

        assertEquals(PerfilPublico(id = "pro-1", nombre = "Ana Soto", fotoUrl = "https://foto.com/ana.png"), (resultado as Result.Success).data)
        // Nunca toca `usuarios` (privado de su dueno).
        verify(exactly = 0) { firestore.collection(COLECCION_USUARIOS) }
    }

    @Test
    fun `obtenerPerfilPublico retorna NO_ENCONTRADO si no existe y SIN_INTERNET ante FirebaseNetworkException`() = runTest {
        val perfiles = mockk<CollectionReference>()
        val docRef = mockk<DocumentReference>()
        every { firestore.collection("perfilesPublicos") } returns perfiles
        every { perfiles.document(any()) } returns docRef
        val task = mockk<Task<DocumentSnapshot>>()
        coEvery { task.await() } returns mockDocumentSnapshot(exists = false, data = emptyMap())
        every { docRef.get() } returns task
        assertEquals(UsuarioError.NO_ENCONTRADO, (repository.obtenerPerfilPublico("pro-1") as Result.Error).error)

        val caida = mockk<Task<DocumentSnapshot>>()
        coEvery { caida.await() } throws FirebaseNetworkException("sin conexion")
        every { docRef.get() } returns caida
        assertEquals(UsuarioError.SIN_INTERNET, (repository.obtenerPerfilPublico("pro-1") as Result.Error).error)
    }

    /** Mockea `firestore.batch()` con `usuarios/cliente-1` y `perfilesPublicos/cliente-1`. */
    private fun setUpBatch(): Triple<WriteBatch, DocumentReference, DocumentReference> {
        val usuarios = mockk<CollectionReference>()
        val perfiles = mockk<CollectionReference>()
        val usuarioRef = mockk<DocumentReference>()
        val perfilRef = mockk<DocumentReference>()
        every { firestore.collection(COLECCION_USUARIOS) } returns usuarios
        every { firestore.collection("perfilesPublicos") } returns perfiles
        every { usuarios.document("cliente-1") } returns usuarioRef
        every { perfiles.document("cliente-1") } returns perfilRef
        val batch = mockk<WriteBatch>()
        every { firestore.batch() } returns batch
        every { batch.update(any<DocumentReference>(), any<Map<String, Any?>>()) } returns batch
        val commitTask = mockk<Task<Void>>()
        coEvery { commitTask.await() } returns mockk()
        every { batch.commit() } returns commitTask
        return Triple(batch, usuarioRef, perfilRef)
    }

    private fun setUpGet(doc: DocumentSnapshot? = null, exception: Exception? = null) {
        val usuariosCollection = mockk<CollectionReference>()
        val docRef = mockk<DocumentReference>()
        every { firestore.collection(COLECCION_USUARIOS) } returns usuariosCollection
        every { usuariosCollection.document(any()) } returns docRef
        val task = mockk<Task<DocumentSnapshot>>()
        if (exception != null) {
            coEvery { task.await() } throws exception
        } else {
            coEvery { task.await() } returns doc!!
        }
        every { docRef.get() } returns task
    }

    private fun mockDocumentSnapshot(exists: Boolean = true, data: Map<String, Any?>): DocumentSnapshot {
        val doc = mockk<DocumentSnapshot>()
        every { doc.exists() } returns exists
        every { doc.getString(any()) } answers { data[firstArg()] as? String }
        every { doc.getTimestamp(any()) } answers { data[firstArg()] as? Timestamp }
        return doc
    }
}
