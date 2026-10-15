@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.darjnest.kinecare.core.network.firebase.repository

import com.darjnest.kinecare.core.common.data.error.ComisionError
import com.darjnest.kinecare.core.common.result.Result
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.Timestamp
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
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
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ComisionRepositoryImplTest {

    private val firestore = mockk<FirebaseFirestore>()
    private val repository = ComisionRepositoryImpl(firestore)
    private val coleccion = mockk<CollectionReference>()

    @BeforeEach
    fun setUp() {
        mockkStatic("kotlinx.coroutines.tasks.TasksKt")
        every { firestore.collection("comisionesPendientes") } returns coleccion
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic("kotlinx.coroutines.tasks.TasksKt")
    }

    @Test
    fun `obtenerSaldo suma lo pendiente, de la mas reciente a la mas antigua`() = runTest {
        setUpQuery(
            listOf(
                comisionDoc("r1", monto = 2_500L, segundos = 1_700_000_000L),
                comisionDoc("r2", monto = 4_000L, segundos = 1_700_100_000L),
            ),
        )

        val resultado = repository.obtenerSaldo("prof-1")

        assertTrue(resultado is Result.Success)
        val saldo = (resultado as Result.Success).data
        assertEquals(6_500L, saldo.total)
        assertTrue(saldo.hayDeuda)
        assertEquals(listOf("r2", "r1"), saldo.pendientes.map { it.reservaId })
        assertEquals(Instant.fromEpochSeconds(1_700_100_000L), saldo.pendientes.first().creadoEn)
    }

    @Test
    fun `obtenerSaldo filtra por profesional y por estado PENDIENTE`() = runTest {
        setUpQuery(emptyList())

        repository.obtenerSaldo("prof-1")

        verify { coleccion.whereEqualTo("profesionalId", "prof-1") }
        verify { filtradaPorProfesional.whereEqualTo("estado", "PENDIENTE") }
    }

    @Test
    fun `obtenerSaldo sin comisiones no hay deuda`() = runTest {
        setUpQuery(emptyList())

        val saldo = (repository.obtenerSaldo("prof-1") as Result.Success).data

        assertEquals(0L, saldo.total)
        assertFalse(saldo.hayDeuda)
    }

    @Test
    fun `obtenerSaldo omite documentos sin monto y tolera un creadoEn pendiente`() = runTest {
        setUpQuery(
            listOf(
                comisionDoc("sin-monto", monto = null),
                comisionDoc("r1", monto = 2_500L, segundos = null),
            ),
        )

        val saldo = (repository.obtenerSaldo("prof-1") as Result.Success).data

        assertEquals(listOf("r1"), saldo.pendientes.map { it.reservaId })
        assertNull(saldo.pendientes.single().creadoEn)
    }

    @Test
    fun `obtenerSaldo retorna SIN_INTERNET ante FirebaseNetworkException`() = runTest {
        setUpQuery(emptyList(), excepcion = FirebaseNetworkException("sin red"))

        assertEquals(ComisionError.SIN_INTERNET, (repository.obtenerSaldo("prof-1") as Result.Error).error)
    }

    @Test
    fun `obtenerSaldo retorna SIN_PERMISO ante PERMISSION_DENIED y DESCONOCIDO ante otro fallo`() = runTest {
        setUpQuery(emptyList(), excepcion = firestoreException(FirebaseFirestoreException.Code.PERMISSION_DENIED))
        assertEquals(ComisionError.SIN_PERMISO, (repository.obtenerSaldo("prof-1") as Result.Error).error)

        setUpQuery(emptyList(), excepcion = IllegalStateException("boom"))
        assertEquals(ComisionError.DESCONOCIDO, (repository.obtenerSaldo("prof-1") as Result.Error).error)
    }

    private lateinit var filtradaPorProfesional: Query
    private lateinit var consulta: Query

    private fun setUpQuery(documentos: List<DocumentSnapshot>, excepcion: Exception? = null) {
        filtradaPorProfesional = mockk<Query>()
        consulta = mockk<Query>()
        every { coleccion.whereEqualTo(any<String>(), any()) } returns filtradaPorProfesional
        every { filtradaPorProfesional.whereEqualTo(any<String>(), any()) } returns consulta
        val task = mockk<Task<QuerySnapshot>>()
        if (excepcion != null) {
            coEvery { task.await() } throws excepcion
        } else {
            val snapshot = mockk<QuerySnapshot>()
            every { snapshot.documents } returns documentos
            coEvery { task.await() } returns snapshot
        }
        every { consulta.get() } returns task
    }

    private fun comisionDoc(id: String, monto: Long?, segundos: Long? = 1_700_000_000L): DocumentSnapshot {
        val campos = mapOf<String, Any?>(
            "reservaId" to id,
            "monto" to monto,
            "creadoEn" to segundos?.let { Timestamp(it, 0) },
        )
        val doc = mockk<DocumentSnapshot>()
        every { doc.id } returns id
        every { doc.exists() } returns true
        every { doc.getString(any()) } answers { campos[firstArg()] as? String }
        every { doc.getLong(any()) } answers { campos[firstArg()] as? Long }
        every { doc.getTimestamp(any()) } answers { campos[firstArg()] as? Timestamp }
        return doc
    }

    private fun firestoreException(code: FirebaseFirestoreException.Code) =
        FirebaseFirestoreException("error", code)
}
