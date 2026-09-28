@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.darjnest.kinecare.core.network.firebase.repository

import com.darjnest.kinecare.core.common.data.error.ResenaError
import com.darjnest.kinecare.core.common.domain.model.Resena
import com.darjnest.kinecare.core.common.result.Result
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.Timestamp
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ResenaRepositoryImplTest {

    private val firestore = mockk<FirebaseFirestore>()
    private val repository = ResenaRepositoryImpl(firestore)

    private val resenasCollection = mockk<CollectionReference>()

    @BeforeEach
    fun setUp() {
        mockkStatic("kotlinx.coroutines.tasks.TasksKt")
        every { firestore.collection("resenas") } returns resenasCollection
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic("kotlinx.coroutines.tasks.TasksKt")
    }

    // --- obtenerPorProfesional ---

    @Test
    fun `obtenerPorProfesional mapea las resenas y omite las con campos obligatorios faltantes`() = runTest {
        setUpQuery(
            documentos = listOf(
                resenaDoc(id = "reserva-1"),
                resenaDoc(id = "reserva-2", comentario = null, respuesta = "Gracias"),
                resenaDoc(id = "reserva-3", data = mapOf("reservaId" to "reserva-3")), // incompleta
            ),
        )

        val resultado = repository.obtenerPorProfesional("prof-1")

        assertTrue(resultado is Result.Success)
        val resenas = (resultado as Result.Success).data
        assertEquals(listOf("reserva-1", "reserva-2"), resenas.map { it.id })
        val primera = resenas.first()
        assertEquals("cliente-1", primera.clienteId)
        assertEquals("prof-1", primera.profesionalId)
        assertEquals(5, primera.calificacion)
        assertEquals("Excelente", primera.comentario)
        assertEquals(Instant.fromEpochSeconds(1_700_000_000L), primera.fecha)
        assertNull(primera.respuestaProfesional)
        assertNull(resenas[1].comentario)
        assertEquals("Gracias", resenas[1].respuestaProfesional)
    }

    @Test
    fun `obtenerPorProfesional consulta por profesionalId, fecha descendente y limite 100`() = runTest {
        val query = setUpQuery(documentos = emptyList())

        val resultado = repository.obtenerPorProfesional("prof-1")

        assertTrue(resultado is Result.Success)
        assertTrue((resultado as Result.Success).data.isEmpty())
        verify { resenasCollection.whereEqualTo("profesionalId", "prof-1") }
        verify { query.orderBy("fecha", Query.Direction.DESCENDING) }
        verify { query.limit(100L) }
    }

    @Test
    fun `obtenerPorProfesional retorna SIN_INTERNET ante FirebaseNetworkException`() = runTest {
        setUpQuery(documentos = emptyList(), excepcion = FirebaseNetworkException("sin red"))

        val resultado = repository.obtenerPorProfesional("prof-1")

        assertEquals(ResenaError.SIN_INTERNET, (resultado as Result.Error).error)
    }

    @Test
    fun `obtenerPorProfesional retorna SIN_INTERNET ante UNAVAILABLE`() = runTest {
        setUpQuery(documentos = emptyList(), excepcion = firestoreException(FirebaseFirestoreException.Code.UNAVAILABLE))

        val resultado = repository.obtenerPorProfesional("prof-1")

        assertEquals(ResenaError.SIN_INTERNET, (resultado as Result.Error).error)
    }

    @Test
    fun `obtenerPorProfesional retorna SIN_PERMISO ante PERMISSION_DENIED`() = runTest {
        setUpQuery(
            documentos = emptyList(),
            excepcion = firestoreException(FirebaseFirestoreException.Code.PERMISSION_DENIED),
        )

        val resultado = repository.obtenerPorProfesional("prof-1")

        assertEquals(ResenaError.SIN_PERMISO, (resultado as Result.Error).error)
    }

    @Test
    fun `obtenerPorProfesional retorna DESCONOCIDO ante otra excepcion`() = runTest {
        setUpQuery(documentos = emptyList(), excepcion = IllegalStateException("boom"))

        val resultado = repository.obtenerPorProfesional("prof-1")

        assertEquals(ResenaError.DESCONOCIDO, (resultado as Result.Error).error)
    }

    // --- obtenerPorReserva ---

    @Test
    fun `obtenerPorReserva retorna la resena del documento con id igual al reservaId`() = runTest {
        setUpDocumento("reserva-1", resenaDoc(id = "reserva-1"))

        val resultado = repository.obtenerPorReserva("reserva-1")

        assertTrue(resultado is Result.Success)
        val resena = (resultado as Result.Success).data
        assertEquals("reserva-1", resena?.id)
        assertEquals("reserva-1", resena?.reservaId)
    }

    @Test
    fun `obtenerPorReserva retorna null cuando el documento no existe`() = runTest {
        setUpDocumento("reserva-9", resenaDoc(id = "reserva-9", existe = false))

        val resultado = repository.obtenerPorReserva("reserva-9")

        assertTrue(resultado is Result.Success)
        assertNull((resultado as Result.Success).data)
    }

    @Test
    fun `obtenerPorReserva retorna SIN_INTERNET ante FirebaseNetworkException`() = runTest {
        setUpDocumento("reserva-1", documento = null, excepcion = FirebaseNetworkException("sin red"))

        val resultado = repository.obtenerPorReserva("reserva-1")

        assertEquals(ResenaError.SIN_INTERNET, (resultado as Result.Error).error)
    }

    // --- crear ---

    @Test
    fun `crear escribe el documento con id igual al reservaId y los campos esperados`() = runTest {
        val slot = slot<Map<String, Any>>()
        val docRef = setUpEscritura("reserva-1", slot)

        val resultado = repository.crear(
            Resena(
                id = "ignorado",
                reservaId = "reserva-1",
                clienteId = "cliente-1",
                profesionalId = "prof-1",
                calificacion = 4,
                comentario = "Muy buena atencion",
                fecha = Instant.fromEpochSeconds(0),
                respuestaProfesional = "no debe escribirse",
            ),
        )

        assertTrue(resultado is Result.Success)
        verify { resenasCollection.document("reserva-1") }
        verify { docRef.set(any<Map<String, Any>>()) }
        val datos = slot.captured
        assertEquals(
            setOf("reservaId", "clienteId", "profesionalId", "calificacion", "fecha", "comentario"),
            datos.keys,
        )
        assertEquals("reserva-1", datos["reservaId"])
        assertEquals("cliente-1", datos["clienteId"])
        assertEquals("prof-1", datos["profesionalId"])
        assertEquals(4, datos["calificacion"])
        assertEquals("Muy buena atencion", datos["comentario"])
        assertEquals(FieldValue.serverTimestamp(), datos["fecha"])
        assertFalse(datos.containsKey("respuestaProfesional"))
    }

    @Test
    fun `crear omite comentario cuando es null`() = runTest {
        val slot = slot<Map<String, Any>>()
        setUpEscritura("reserva-1", slot)

        val resultado = repository.crear(resenaParaCrear(comentario = null))

        assertTrue(resultado is Result.Success)
        assertFalse(slot.captured.containsKey("comentario"))
    }

    @Test
    fun `crear retorna SIN_PERMISO ante PERMISSION_DENIED`() = runTest {
        setUpEscritura(
            "reserva-1",
            slot(),
            excepcion = firestoreException(FirebaseFirestoreException.Code.PERMISSION_DENIED),
        )

        val resultado = repository.crear(resenaParaCrear())

        assertEquals(ResenaError.SIN_PERMISO, (resultado as Result.Error).error)
    }

    @Test
    fun `crear retorna SIN_INTERNET ante FirebaseNetworkException`() = runTest {
        setUpEscritura("reserva-1", slot(), excepcion = FirebaseNetworkException("sin red"))

        val resultado = repository.crear(resenaParaCrear())

        assertEquals(ResenaError.SIN_INTERNET, (resultado as Result.Error).error)
    }

    @Test
    fun `crear retorna SIN_INTERNET ante UNAVAILABLE`() = runTest {
        setUpEscritura(
            "reserva-1",
            slot(),
            excepcion = firestoreException(FirebaseFirestoreException.Code.UNAVAILABLE),
        )

        val resultado = repository.crear(resenaParaCrear())

        assertEquals(ResenaError.SIN_INTERNET, (resultado as Result.Error).error)
    }

    @Test
    fun `crear retorna DESCONOCIDO ante otra excepcion`() = runTest {
        setUpEscritura("reserva-1", slot(), excepcion = IllegalStateException("boom"))

        val resultado = repository.crear(resenaParaCrear())

        assertEquals(ResenaError.DESCONOCIDO, (resultado as Result.Error).error)
    }

    // --- helpers ---

    private fun resenaParaCrear(comentario: String? = "Bien") = Resena(
        id = "reserva-1",
        reservaId = "reserva-1",
        clienteId = "cliente-1",
        profesionalId = "prof-1",
        calificacion = 5,
        comentario = comentario,
        fecha = Instant.fromEpochSeconds(0),
        respuestaProfesional = null,
    )

    private fun firestoreException(code: FirebaseFirestoreException.Code) =
        FirebaseFirestoreException("error", code)

    private fun setUpQuery(documentos: List<DocumentSnapshot>, excepcion: Exception? = null): Query {
        val query = mockk<Query>()
        every { resenasCollection.whereEqualTo(any<String>(), any()) } returns query
        every { query.orderBy(any<String>(), any()) } returns query
        every { query.limit(any()) } returns query

        val task = mockk<Task<QuerySnapshot>>()
        if (excepcion != null) {
            coEvery { task.await() } throws excepcion
        } else {
            val snapshot = mockk<QuerySnapshot>()
            every { snapshot.documents } returns documentos
            coEvery { task.await() } returns snapshot
        }
        every { query.get() } returns task
        return query
    }

    private fun setUpDocumento(reservaId: String, documento: DocumentSnapshot?, excepcion: Exception? = null) {
        val docRef = mockk<DocumentReference>()
        every { resenasCollection.document(reservaId) } returns docRef
        val task = mockk<Task<DocumentSnapshot>>()
        if (excepcion != null) {
            coEvery { task.await() } throws excepcion
        } else {
            coEvery { task.await() } returns documento!!
        }
        every { docRef.get() } returns task
    }

    private fun setUpEscritura(
        reservaId: String,
        datosCapturados: io.mockk.CapturingSlot<Map<String, Any>>,
        excepcion: Exception? = null,
    ): DocumentReference {
        val docRef = mockk<DocumentReference>()
        every { resenasCollection.document(reservaId) } returns docRef
        val task = mockk<Task<Void>>()
        if (excepcion != null) {
            coEvery { task.await() } throws excepcion
        } else {
            coEvery { task.await() } returns mockk<Void>()
        }
        every { docRef.set(capture(datosCapturados)) } returns task
        return docRef
    }

    private fun resenaDoc(
        id: String,
        existe: Boolean = true,
        comentario: String? = "Excelente",
        respuesta: String? = null,
        data: Map<String, Any?>? = null,
    ): DocumentSnapshot {
        val campos = data ?: mapOf(
            "reservaId" to id,
            "clienteId" to "cliente-1",
            "profesionalId" to "prof-1",
            "calificacion" to 5L,
            "comentario" to comentario,
            "fecha" to Timestamp(1_700_000_000L, 0),
            "respuestaProfesional" to respuesta,
        )
        val doc = mockk<DocumentSnapshot>()
        every { doc.id } returns id
        every { doc.exists() } returns existe
        every { doc.getString(any()) } answers { campos[firstArg()] as? String }
        every { doc.getLong(any()) } answers { campos[firstArg()] as? Long }
        every { doc.getTimestamp(any()) } answers { campos[firstArg()] as? Timestamp }
        return doc
    }
}
