package com.darjnest.kinecare.core.network.firebase.repository

import com.darjnest.kinecare.core.common.data.error.UsuarioError
import com.darjnest.kinecare.core.common.result.Result
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.Timestamp
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
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
    fun `actualizarDatosPersonales invoca update con los campos editables`() = runTest {
        val usuariosCollection = mockk<CollectionReference>()
        val docRef = mockk<DocumentReference>()
        every { firestore.collection(COLECCION_USUARIOS) } returns usuariosCollection
        every { usuariosCollection.document("cliente-1") } returns docRef
        val updateTask = mockk<Task<Void>>()
        coEvery { updateTask.await() } returns mockk()
        every { docRef.update(any<Map<String, Any?>>()) } returns updateTask

        val resultado = repository.actualizarDatosPersonales(
            id = "cliente-1",
            nombre = "Ana Soto",
            telefono = "+56911111111",
            correoContacto = "ana@kinecare.cl",
        )

        assertTrue(resultado is Result.Success)
        verify {
            docRef.update(
                mapOf(
                    "nombre" to "Ana Soto",
                    "telefono" to "+56911111111",
                    "correoContacto" to "ana@kinecare.cl",
                ),
            )
        }
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
