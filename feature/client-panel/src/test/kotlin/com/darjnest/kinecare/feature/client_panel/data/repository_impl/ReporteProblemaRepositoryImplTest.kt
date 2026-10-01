@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.darjnest.kinecare.feature.client_panel.data.repository_impl

import com.darjnest.kinecare.core.common.result.Result
import com.darjnest.kinecare.feature.client_panel.domain.EstadoReporte
import com.darjnest.kinecare.feature.client_panel.domain.MotivoReporte
import com.darjnest.kinecare.feature.client_panel.domain.ReporteProblema
import com.darjnest.kinecare.feature.client_panel.domain.ReporteProblemaError
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
import io.mockk.CapturingSlot
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
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ReporteProblemaRepositoryImplTest {

    private val firestore = mockk<FirebaseFirestore>()
    private val repository = ReporteProblemaRepositoryImpl(firestore)

    private val reportesCollection = mockk<CollectionReference>()

    @BeforeEach
    fun setUp() {
        mockkStatic("kotlinx.coroutines.tasks.TasksKt")
        every { firestore.collection("reportesProblema") } returns reportesCollection
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic("kotlinx.coroutines.tasks.TasksKt")
    }

    // --- obtenerPorCliente ---

    @Test
    fun `obtenerPorCliente consulta por clienteId y mapea, omitiendo documentos incompletos o con motivo desconocido`() = runTest {
        setUpQuery(
            documentos = listOf(
                reporteDoc(id = "reserva-1"),
                reporteDoc(id = "reserva-2", estado = "EN_REVISION"),
                reporteDoc(id = "reserva-3", motivo = "MOTIVO_NUEVO"),
                reporteDoc(id = "reserva-4", data = mapOf("reservaId" to "reserva-4")),
            ),
        )

        val resultado = repository.obtenerPorCliente("cliente-1")

        val reportes = (resultado as Result.Success).data
        assertEquals(listOf("reserva-1", "reserva-2"), reportes.map { it.reservaId })
        val primero = reportes.first()
        assertEquals("cliente-1", primero.clienteId)
        assertEquals("prof-1", primero.profesionalId)
        assertEquals(MotivoReporte.PROFESIONAL_NO_LLEGO, primero.motivo)
        assertEquals("Nunca llego a la cita", primero.descripcion)
        assertEquals(EstadoReporte.ABIERTO, primero.estado)
        assertEquals(Instant.fromEpochSeconds(1_700_000_000L), primero.fecha)
        assertEquals(EstadoReporte.EN_REVISION, reportes[1].estado)
        verify { reportesCollection.whereEqualTo("clienteId", "cliente-1") }
    }

    @Test
    fun `obtenerPorCliente trata un estado desconocido como ABIERTO`() = runTest {
        setUpQuery(documentos = listOf(reporteDoc(id = "reserva-1", estado = "ESCALADO")))

        val resultado = repository.obtenerPorCliente("cliente-1")

        assertEquals(EstadoReporte.ABIERTO, (resultado as Result.Success).data.single().estado)
    }

    @Test
    fun `obtenerPorCliente retorna SIN_INTERNET ante UNAVAILABLE`() = runTest {
        setUpQuery(documentos = emptyList(), excepcion = firestoreException(FirebaseFirestoreException.Code.UNAVAILABLE))

        val resultado = repository.obtenerPorCliente("cliente-1")

        assertEquals(ReporteProblemaError.SIN_INTERNET, (resultado as Result.Error).error)
    }

    @Test
    fun `obtenerPorCliente retorna SIN_PERMISO ante PERMISSION_DENIED`() = runTest {
        setUpQuery(
            documentos = emptyList(),
            excepcion = firestoreException(FirebaseFirestoreException.Code.PERMISSION_DENIED),
        )

        val resultado = repository.obtenerPorCliente("cliente-1")

        assertEquals(ReporteProblemaError.SIN_PERMISO, (resultado as Result.Error).error)
    }

    // --- obtenerPorReserva ---

    @Test
    fun `obtenerPorReserva lee el documento con id igual al reservaId`() = runTest {
        setUpDocumento("reserva-1", reporteDoc(id = "reserva-1"))

        val resultado = repository.obtenerPorReserva("reserva-1")

        assertEquals("reserva-1", (resultado as Result.Success).data?.reservaId)
    }

    @Test
    fun `obtenerPorReserva retorna null cuando el documento no existe`() = runTest {
        setUpDocumento("reserva-9", reporteDoc(id = "reserva-9", existe = false))

        val resultado = repository.obtenerPorReserva("reserva-9")

        assertNull((resultado as Result.Success).data)
    }

    @Test
    fun `obtenerPorReserva retorna SIN_INTERNET ante FirebaseNetworkException`() = runTest {
        setUpDocumento("reserva-1", documento = null, excepcion = FirebaseNetworkException("sin red"))

        val resultado = repository.obtenerPorReserva("reserva-1")

        assertEquals(ReporteProblemaError.SIN_INTERNET, (resultado as Result.Error).error)
    }

    // --- crear ---

    @Test
    fun `crear escribe en el id de la reserva con estado ABIERTO y fecha del servidor`() = runTest {
        val datos = slot<Map<String, Any>>()
        setUpEscritura("reserva-1", datos)

        val resultado = repository.crear(
            reporteParaCrear().copy(estado = EstadoReporte.RESUELTO, fecha = Instant.fromEpochSeconds(42)),
        )

        assertTrue(resultado is Result.Success)
        verify { reportesCollection.document("reserva-1") }
        assertEquals(
            mapOf(
                "reservaId" to "reserva-1",
                "clienteId" to "cliente-1",
                "profesionalId" to "prof-1",
                "motivo" to "COBRO_INCORRECTO",
                "descripcion" to "Me cobraron dos veces",
                "estado" to "ABIERTO",
                "fecha" to FieldValue.serverTimestamp(),
            ),
            datos.captured,
        )
    }

    @Test
    fun `crear retorna SIN_PERMISO ante PERMISSION_DENIED`() = runTest {
        setUpEscritura(
            "reserva-1",
            slot(),
            excepcion = firestoreException(FirebaseFirestoreException.Code.PERMISSION_DENIED),
        )

        val resultado = repository.crear(reporteParaCrear())

        assertEquals(ReporteProblemaError.SIN_PERMISO, (resultado as Result.Error).error)
    }

    @Test
    fun `crear retorna DESCONOCIDO ante otra excepcion`() = runTest {
        setUpEscritura("reserva-1", slot(), excepcion = IllegalStateException("boom"))

        val resultado = repository.crear(reporteParaCrear())

        assertEquals(ReporteProblemaError.DESCONOCIDO, (resultado as Result.Error).error)
    }

    // --- helpers ---

    private fun reporteParaCrear() = ReporteProblema(
        reservaId = "reserva-1",
        clienteId = "cliente-1",
        profesionalId = "prof-1",
        motivo = MotivoReporte.COBRO_INCORRECTO,
        descripcion = "Me cobraron dos veces",
        estado = EstadoReporte.ABIERTO,
        fecha = Instant.fromEpochSeconds(0),
    )

    private fun firestoreException(code: FirebaseFirestoreException.Code) =
        FirebaseFirestoreException("error", code)

    private fun setUpQuery(documentos: List<DocumentSnapshot>, excepcion: Exception? = null) {
        val query = mockk<Query>()
        every { reportesCollection.whereEqualTo(any<String>(), any()) } returns query
        val task = mockk<Task<QuerySnapshot>>()
        if (excepcion != null) {
            coEvery { task.await() } throws excepcion
        } else {
            val snapshot = mockk<QuerySnapshot>()
            every { snapshot.documents } returns documentos
            coEvery { task.await() } returns snapshot
        }
        every { query.get() } returns task
    }

    private fun setUpDocumento(reservaId: String, documento: DocumentSnapshot?, excepcion: Exception? = null) {
        val docRef = mockk<DocumentReference>()
        every { reportesCollection.document(reservaId) } returns docRef
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
        datosCapturados: CapturingSlot<Map<String, Any>>,
        excepcion: Exception? = null,
    ) {
        val docRef = mockk<DocumentReference>()
        every { reportesCollection.document(reservaId) } returns docRef
        val task = mockk<Task<Void>>()
        if (excepcion != null) {
            coEvery { task.await() } throws excepcion
        } else {
            coEvery { task.await() } returns mockk<Void>()
        }
        every { docRef.set(capture(datosCapturados)) } returns task
    }

    private fun reporteDoc(
        id: String,
        existe: Boolean = true,
        motivo: String = "PROFESIONAL_NO_LLEGO",
        estado: String = "ABIERTO",
        data: Map<String, Any?>? = null,
    ): DocumentSnapshot {
        val campos = data ?: mapOf(
            "reservaId" to id,
            "clienteId" to "cliente-1",
            "profesionalId" to "prof-1",
            "motivo" to motivo,
            "descripcion" to "Nunca llego a la cita",
            "estado" to estado,
            "fecha" to Timestamp(1_700_000_000L, 0),
        )
        val doc = mockk<DocumentSnapshot>()
        every { doc.id } returns id
        every { doc.exists() } returns existe
        every { doc.getString(any()) } answers { campos[firstArg()] as? String }
        every { doc.getTimestamp(any()) } answers { campos[firstArg()] as? Timestamp }
        return doc
    }
}
