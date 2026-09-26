@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.darjnest.kinecare.core.network.firebase.repository

import com.darjnest.kinecare.core.common.data.error.ClienteError
import com.darjnest.kinecare.core.common.data.error.UsuarioError
import com.darjnest.kinecare.core.common.data.repository.UsuarioRepository
import com.darjnest.kinecare.core.common.domain.model.RolUsuario
import com.darjnest.kinecare.core.common.domain.model.Usuario
import com.darjnest.kinecare.core.common.result.Result
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

private const val COLECCION_CLIENTES = "clientes"

class ClienteRepositoryImplTest {

    private val firestore = mockk<FirebaseFirestore>()
    private val usuarioRepository = mockk<UsuarioRepository>()
    private val repository = ClienteRepositoryImpl(firestore, usuarioRepository)

    private val usuario = Usuario(
        id = "cliente-1",
        nombre = "Ana Soto",
        rut = "12345678-5",
        email = "ana@kinecare.cl",
        correoContacto = "ana@kinecare.cl",
        telefono = "+56911111111",
        rol = RolUsuario.CLIENTE,
        fotoUrl = null,
        fechaRegistro = Instant.fromEpochMilliseconds(1_700_000_000_000L),
    )

    @BeforeEach
    fun setUp() {
        mockkStatic("kotlinx.coroutines.tasks.TasksKt")
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic("kotlinx.coroutines.tasks.TasksKt")
    }

    @Test
    fun `retorna el cliente mapeado combinando el usuario y el documento cliente`() = runTest {
        coEvery { usuarioRepository.obtenerPorId("cliente-1") } returns Result.Success(usuario)
        val doc = mockDocumentSnapshot(
            data = mapOf(
                "direcciones" to listOf(
                    mapOf(
                        "calle" to "Av. Siempre Viva",
                        "numero" to "742",
                        "comuna" to "Providencia",
                        "ciudad" to "Santiago",
                        "lat" to -33.4,
                        "lng" to -70.6,
                        "indicaciones" to null,
                    ),
                ),
                "metodosPago" to emptyList<Map<String, Any?>>(),
                "favoritos" to listOf("prof-1", "prof-2"),
            ),
        )
        setUpGet(doc)

        val resultado = repository.obtenerPorId("cliente-1")

        assertTrue(resultado is Result.Success)
        val cliente = (resultado as Result.Success).data
        assertEquals("Ana Soto", cliente.usuario.nombre)
        assertEquals(1, cliente.direcciones.size)
        assertEquals("Providencia", cliente.direcciones.first().comuna)
        assertEquals(listOf("prof-1", "prof-2"), cliente.favoritos)
    }

    @Test
    fun `propaga el error del usuario cuando UsuarioRepository falla`() = runTest {
        coEvery { usuarioRepository.obtenerPorId("cliente-1") } returns Result.Error(UsuarioError.SIN_INTERNET)

        val resultado = repository.obtenerPorId("cliente-1")

        assertTrue(resultado is Result.Error)
        assertEquals(ClienteError.SIN_INTERNET, (resultado as Result.Error).error)
    }

    @Test
    fun `retorna NO_ENCONTRADO cuando clientes-uid no existe`() = runTest {
        coEvery { usuarioRepository.obtenerPorId("cliente-1") } returns Result.Success(usuario)
        setUpGet(mockDocumentSnapshot(exists = false, data = emptyMap()))

        val resultado = repository.obtenerPorId("cliente-1")

        assertTrue(resultado is Result.Error)
        assertEquals(ClienteError.NO_ENCONTRADO, (resultado as Result.Error).error)
    }

    @Test
    fun `agregarFavorito hace arrayUnion sobre el campo favoritos`() = runTest {
        mockkStatic(FieldValue::class)
        val arrayUnion = mockk<FieldValue>()
        every { FieldValue.arrayUnion("prof-1") } returns arrayUnion

        val clientesCollection = mockk<CollectionReference>()
        val docRef = mockk<DocumentReference>()
        every { firestore.collection(COLECCION_CLIENTES) } returns clientesCollection
        every { clientesCollection.document("cliente-1") } returns docRef
        val updateTask = mockk<Task<Void>>()
        coEvery { updateTask.await() } returns mockk()
        every { docRef.update("favoritos", arrayUnion) } returns updateTask

        val resultado = repository.agregarFavorito("cliente-1", "prof-1")

        assertTrue(resultado is Result.Success)
        verify { docRef.update("favoritos", arrayUnion) }
        unmockkStatic(FieldValue::class)
    }

    private fun setUpGet(doc: DocumentSnapshot) {
        val clientesCollection = mockk<CollectionReference>()
        val docRef = mockk<DocumentReference>()
        every { firestore.collection(COLECCION_CLIENTES) } returns clientesCollection
        every { clientesCollection.document(any()) } returns docRef
        val task = mockk<Task<DocumentSnapshot>>()
        coEvery { task.await() } returns doc
        every { docRef.get() } returns task
    }

    private fun mockDocumentSnapshot(exists: Boolean = true, data: Map<String, Any?>): DocumentSnapshot {
        val doc = mockk<DocumentSnapshot>()
        every { doc.exists() } returns exists
        every { doc.get(any<String>()) } answers { data[firstArg()] }
        return doc
    }
}
