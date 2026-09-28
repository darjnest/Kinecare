package com.darjnest.kinecare.core.network.firebase.repository

import com.darjnest.kinecare.core.common.data.error.ServicioError
import com.darjnest.kinecare.core.common.domain.model.ModalidadServicio
import com.darjnest.kinecare.core.common.result.Result
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QuerySnapshot
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
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ServicioRepositoryImplTest {

    private val firestore = mockk<FirebaseFirestore>()
    private val repository = ServicioRepositoryImpl(firestore)

    private val serviciosCollection = mockk<CollectionReference>()

    @BeforeEach
    fun setUp() {
        mockkStatic("kotlinx.coroutines.tasks.TasksKt")
        val profesionales = mockk<CollectionReference>()
        val profesionalDoc = mockk<DocumentReference>()
        every { firestore.collection("profesionales") } returns profesionales
        every { profesionales.document("prof-1") } returns profesionalDoc
        every { profesionalDoc.collection("servicios") } returns serviciosCollection
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic("kotlinx.coroutines.tasks.TasksKt")
    }

    @Test
    fun `obtenerPorProfesional mapea los servicios incluyendo los pausados`() = runTest {
        val snapshot = mockk<QuerySnapshot>()
        every { snapshot.documents } returns listOf(
            servicioDoc("s1", activo = true),
            servicioDoc("s2", activo = false),
            servicioDoc("s3", activo = null),
        )
        val task = mockk<Task<QuerySnapshot>>()
        coEvery { task.await() } returns snapshot
        every { serviciosCollection.get() } returns task

        val resultado = repository.obtenerPorProfesional("prof-1")

        assertTrue(resultado is Result.Success)
        val servicios = (resultado as Result.Success).data
        assertEquals(listOf("s1", "s2", "s3"), servicios.map { it.id })
        assertEquals(ModalidadServicio.DOMICILIO, servicios.first().modalidad)
        assertEquals(35_000L, servicios.first().precio)
        // Sin campo `activo` en el documento se considera activo (datos sembrados antes del campo).
        assertEquals(listOf(true, false, true), servicios.map { it.activo })
    }

    @Test
    fun `obtenerPorProfesional retorna SIN_INTERNET ante FirebaseNetworkException`() = runTest {
        val task = mockk<Task<QuerySnapshot>>()
        coEvery { task.await() } throws FirebaseNetworkException("sin red")
        every { serviciosCollection.get() } returns task

        val resultado = repository.obtenerPorProfesional("prof-1")

        assertEquals(ServicioError.SIN_INTERNET, (resultado as Result.Error).error)
    }

    @Test
    fun `actualizarActivo escribe solo el campo activo del servicio`() = runTest {
        val docRef = mockk<DocumentReference>()
        every { serviciosCollection.document("s1") } returns docRef
        val task = mockk<Task<Void>>()
        coEvery { task.await() } returns mockk()
        every { docRef.update("activo", false) } returns task

        val resultado = repository.actualizarActivo("prof-1", "s1", activo = false)

        assertTrue(resultado is Result.Success)
        verify { docRef.update("activo", false) }
    }

    @Test
    fun `actualizarActivo retorna DESCONOCIDO ante un error inesperado`() = runTest {
        val docRef = mockk<DocumentReference>()
        every { serviciosCollection.document("s1") } returns docRef
        val task = mockk<Task<Void>>()
        coEvery { task.await() } throws IllegalStateException("permiso denegado")
        every { docRef.update("activo", true) } returns task

        val resultado = repository.actualizarActivo("prof-1", "s1", activo = true)

        assertFalse(resultado is Result.Success)
        assertEquals(ServicioError.DESCONOCIDO, (resultado as Result.Error).error)
    }

    private fun servicioDoc(id: String, activo: Boolean?): DocumentSnapshot {
        val doc = mockk<DocumentSnapshot>()
        every { doc.exists() } returns true
        every { doc.id } returns id
        every { doc.getString("nombre") } returns "Sesion $id"
        every { doc.getString("descripcion") } returns "Descripcion"
        every { doc.getString("modalidad") } returns "DOMICILIO"
        every { doc.getLong("duracionMinutos") } returns 60L
        every { doc.getLong("precio") } returns 35_000L
        every { doc.getBoolean("activo") } returns activo
        return doc
    }
}
