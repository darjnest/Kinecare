@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.darjnest.kinecare.core.network.firebase.repository

import com.darjnest.kinecare.core.common.data.error.ReservaError
import com.darjnest.kinecare.core.common.domain.model.EstadoPago
import com.darjnest.kinecare.core.common.domain.model.EstadoReserva
import com.darjnest.kinecare.core.common.result.Result
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.Timestamp
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

private const val COLECCION_RESERVAS = "reservas"
private const val COLECCION_PAGOS = "pagos"

class ReservaRepositoryImplTest {

    private val firestore = mockk<FirebaseFirestore>()
    private val repository = ReservaRepositoryImpl(firestore)

    @BeforeEach
    fun setUp() {
        mockkStatic("kotlinx.coroutines.tasks.TasksKt")
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic("kotlinx.coroutines.tasks.TasksKt")
    }

    @Test
    fun `retorna las reservas del cliente mapeadas junto a su pago`() = runTest {
        val reservaDoc = mockDocumentSnapshot(
            id = "reserva-1",
            data = mapOf(
                "clienteId" to "cliente-1",
                "profesionalId" to "prof-1",
                "servicioId" to "serv-1",
                "modalidad" to "CONSULTA",
                "fechaHora" to Timestamp(1_700_000_000L, 0),
                "estado" to "CONFIRMADA",
                "pago" to mapOf("id" to "pago-1", "monto" to 18000L, "estado" to "AUTORIZADO"),
                "comisionPorcentaje" to 0.15,
            ),
        )
        val pagoDoc = mockDocumentSnapshot(
            id = "pago-1",
            data = mapOf(
                "reservaId" to "reserva-1",
                "monto" to 18000L,
                "metodo" to mapOf("tipo" to "TARJETA", "ultimosDigitos" to "1234", "tokenPasarela" to "tok_abc"),
                "estado" to "AUTORIZADO",
                "idTransaccionPasarela" to "tx-1",
            ),
        )
        setUpQuery(reservaDocumentos = listOf(reservaDoc))
        setUpPago("pago-1", pagoDoc)

        val resultado = repository.obtenerPorCliente("cliente-1")

        assertTrue(resultado is Result.Success)
        val reservas = (resultado as Result.Success).data
        assertEquals(1, reservas.size)
        val reserva = reservas.first()
        assertEquals("cliente-1", reserva.clienteId)
        assertEquals(EstadoReserva.CONFIRMADA, reserva.estado)
        assertEquals(EstadoPago.AUTORIZADO, reserva.pago.estado)
        assertEquals("tx-1", reserva.pago.idTransaccionPasarela)
        assertEquals(18000L, reserva.pago.monto)
    }

    @Test
    fun `retorna lista vacia cuando el cliente no tiene reservas`() = runTest {
        setUpQuery(reservaDocumentos = emptyList())

        val resultado = repository.obtenerPorCliente("cliente-1")

        assertTrue(resultado is Result.Success)
        assertTrue((resultado as Result.Success).data.isEmpty())
    }

    @Test
    fun `retorna SIN_INTERNET ante FirebaseNetworkException`() = runTest {
        setUpQuery(reservaDocumentos = emptyList(), queryException = FirebaseNetworkException("sin conexion"))

        val resultado = repository.obtenerPorCliente("cliente-1")

        assertTrue(resultado is Result.Error)
        assertEquals(ReservaError.SIN_INTERNET, (resultado as Result.Error).error)
    }

    private fun setUpQuery(reservaDocumentos: List<DocumentSnapshot>, queryException: Exception? = null) {
        val reservasCollection = mockk<CollectionReference>()
        val query = mockk<Query>()
        every { firestore.collection(COLECCION_RESERVAS) } returns reservasCollection
        every { reservasCollection.whereEqualTo(any<String>(), any()) } returns query
        every { query.orderBy(any<String>(), any()) } returns query

        val queryTask = mockk<Task<QuerySnapshot>>()
        if (queryException != null) {
            coEvery { queryTask.await() } throws queryException
        } else {
            val querySnapshot = mockk<QuerySnapshot>()
            every { querySnapshot.documents } returns reservaDocumentos
            coEvery { queryTask.await() } returns querySnapshot
        }
        every { query.get() } returns queryTask
    }

    private fun setUpPago(pagoId: String, doc: DocumentSnapshot) {
        val pagosCollection = mockk<CollectionReference>()
        val docRef = mockk<DocumentReference>()
        every { firestore.collection(COLECCION_PAGOS) } returns pagosCollection
        every { pagosCollection.document(pagoId) } returns docRef
        val task = mockk<Task<DocumentSnapshot>>()
        coEvery { task.await() } returns doc
        every { docRef.get() } returns task
    }

    private fun mockDocumentSnapshot(id: String, exists: Boolean = true, data: Map<String, Any?>): DocumentSnapshot {
        val doc = mockk<DocumentSnapshot>()
        every { doc.id } returns id
        every { doc.exists() } returns exists
        every { doc.get(any<String>()) } answers { data[firstArg()] }
        every { doc.getString(any()) } answers { data[firstArg()] as? String }
        every { doc.getDouble(any()) } answers { data[firstArg()] as? Double }
        every { doc.getLong(any()) } answers { data[firstArg()] as? Long }
        every { doc.getTimestamp(any()) } answers { data[firstArg()] as? Timestamp }
        return doc
    }
}
