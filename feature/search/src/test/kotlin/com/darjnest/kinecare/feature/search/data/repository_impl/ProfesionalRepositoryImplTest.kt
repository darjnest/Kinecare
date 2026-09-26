package com.darjnest.kinecare.feature.search.data.repository_impl

import com.darjnest.kinecare.core.common.result.Result
import com.darjnest.kinecare.feature.search.domain.ProfesionalError
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.Timestamp
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.google.android.gms.tasks.Task
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

private const val COLECCION_PROFESIONALES = "profesionales"
private const val COLECCION_USUARIOS = "usuarios"
private const val SUBCOLECCION_SERVICIOS = "servicios"

/**
 * Primer repositorio de Firestore del proyecto con tests: el patron aqui
 * (mockear `FirebaseFirestore`/`CollectionReference`/`Query`/`DocumentSnapshot`
 * con MockK y stubear la extension `Task<T>.await()` de
 * kotlinx-coroutines-play-services via `mockkStatic` sobre su clase
 * contenedora `kotlinx.coroutines.tasks.TasksKt`) sienta el precedente para
 * el resto de los repositorios de Firestore que aun no tienen tests
 * (`AuthRepositoryImpl`, `UbicacionRepositoryImpl`).
 */
class ProfesionalRepositoryImplTest {

    private val firestore = mockk<FirebaseFirestore>()
    private val repository = ProfesionalRepositoryImpl(firestore)

    @BeforeEach
    fun setUp() {
        mockkStatic("kotlinx.coroutines.tasks.TasksKt")
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic("kotlinx.coroutines.tasks.TasksKt")
    }

    @Test
    fun `retorna profesionales mapeados cuando Firestore devuelve documentos validos`() = runTest {
        val usuarioDoc = mockDocumentSnapshot(
            id = "prof-1",
            data = mapOf(
                "nombre" to "Ana Soto",
                "rut" to "12345678-5",
                "email" to "ana@kinecare.cl",
                "correoContacto" to "ana.contacto@kinecare.cl",
                "telefono" to "+56911111111",
                "rol" to "PROFESIONAL",
                "fotoUrl" to "https://foto.com/ana.png",
                "fechaRegistro" to Timestamp(1_700_000_000L, 0),
            ),
        )
        val servicioDoc = mockDocumentSnapshot(
            id = "serv-1",
            data = mapOf(
                "nombre" to "Sesion de kinesiologia deportiva",
                "descripcion" to "Recuperacion de lesiones",
                "modalidad" to "CONSULTA",
                "duracionMinutos" to 60L,
                "precio" to 18000L,
            ),
        )
        val insigniaMap = mapOf(
            "tipo" to "IDENTIDAD",
            "estado" to "APROBADO",
            "detalle" to "Cedula verificada",
            "fechaActualizacion" to Timestamp(1_700_000_100L, 0),
        )
        val disponibilidadMap = mapOf(
            "diaSemana" to "MONDAY",
            "horaInicio" to "09:00",
            "horaFin" to "18:00",
            "activo" to true,
        )
        val profesionalDoc = mockDocumentSnapshot(
            id = "prof-1",
            data = mapOf(
                "especialidades" to listOf("KINESIOLOGIA", "kine-deportiva"),
                "insignias" to listOf(insigniaMap),
                "disponibilidad" to listOf(disponibilidadMap),
                "rnpi" to "RNPI-1234",
                "calificacionPromedio" to 4.8,
                "totalResenas" to 32L,
                "descripcion" to "Kinesiologo deportivo",
                "estadoVerificacionGeneral" to "APROBADO",
            ),
        )

        setUpFirestore(
            profesionalDocumentos = listOf(profesionalDoc),
            usuariosPorId = mapOf("prof-1" to usuarioDoc),
            serviciosPorProfesionalId = mapOf("prof-1" to listOf(servicioDoc)),
        )

        val resultado = repository.buscarPorEspecialidad("KINESIOLOGIA")

        assertTrue(resultado is Result.Success)
        val profesionales = (resultado as Result.Success).data
        assertEquals(1, profesionales.size)
        val profesional = profesionales.first()
        assertEquals("Ana Soto", profesional.usuario.nombre)
        assertEquals("RNPI-1234", profesional.rnpi)
        assertEquals(listOf("KINESIOLOGIA", "kine-deportiva"), profesional.especialidades)
        assertEquals(1, profesional.servicios.size)
        assertEquals("Sesion de kinesiologia deportiva", profesional.servicios.first().nombre)
        assertEquals(1, profesional.insignias.size)
        assertEquals(4.8, profesional.calificacionPromedio)
        assertEquals(32, profesional.totalResenas)
    }

    @Test
    fun `retorna lista vacia cuando Firestore no devuelve documentos`() = runTest {
        setUpFirestore(profesionalDocumentos = emptyList())

        val resultado = repository.buscarPorEspecialidad("KINESIOLOGIA")

        assertTrue(resultado is Result.Success)
        assertTrue((resultado as Result.Success).data.isEmpty())
    }

    @Test
    fun `retorna error SIN_INTERNET cuando la query lanza FirebaseNetworkException`() = runTest {
        setUpFirestore(
            profesionalDocumentos = emptyList(),
            queryException = FirebaseNetworkException("sin conexion"),
        )

        val resultado = repository.buscarPorEspecialidad("KINESIOLOGIA")

        assertTrue(resultado is Result.Error)
        assertEquals(ProfesionalError.SIN_INTERNET, (resultado as Result.Error).error)
    }

    @Test
    fun `retorna error DESCONOCIDO ante una excepcion generica`() = runTest {
        setUpFirestore(
            profesionalDocumentos = emptyList(),
            queryException = IllegalStateException("boom"),
        )

        val resultado = repository.buscarPorEspecialidad("KINESIOLOGIA")

        assertTrue(resultado is Result.Error)
        assertEquals(ProfesionalError.DESCONOCIDO, (resultado as Result.Error).error)
    }

    @Test
    fun `descarta insignia con tipo invalido sin romper el mapeo del resto del profesional`() = runTest {
        val usuarioDoc = mockDocumentSnapshot(
            id = "prof-2",
            data = mapOf(
                "nombre" to "Bruno Diaz",
                "rut" to "9876543-2",
                "email" to "bruno@kinecare.cl",
                "rol" to "PROFESIONAL",
                "fechaRegistro" to Timestamp(1_700_000_000L, 0),
            ),
        )
        val insigniaInvalida = mapOf(
            "tipo" to "NO_EXISTE",
            "estado" to "APROBADO",
            "detalle" to "dato corrupto",
            "fechaActualizacion" to Timestamp(1_700_000_100L, 0),
        )
        val profesionalDoc = mockDocumentSnapshot(
            id = "prof-2",
            data = mapOf(
                "especialidades" to listOf("KINESIOLOGIA"),
                "insignias" to listOf(insigniaInvalida),
                "rnpi" to "RNPI-9999",
                "calificacionPromedio" to 3.5,
                "totalResenas" to 4L,
                "descripcion" to "Kinesiologo general",
                "estadoVerificacionGeneral" to "NO_SOLICITADO",
            ),
        )

        setUpFirestore(
            profesionalDocumentos = listOf(profesionalDoc),
            usuariosPorId = mapOf("prof-2" to usuarioDoc),
            serviciosPorProfesionalId = mapOf("prof-2" to emptyList()),
        )

        val resultado = repository.buscarPorEspecialidad("KINESIOLOGIA")

        assertTrue(resultado is Result.Success)
        val profesional = (resultado as Result.Success).data.first()
        assertTrue(profesional.insignias.isEmpty())
        assertEquals("Bruno Diaz", profesional.usuario.nombre)
        assertEquals("RNPI-9999", profesional.rnpi)
    }

    /**
     * Encadena todos los mocks de Firestore que recorre
     * `ProfesionalRepositoryImpl.buscarPorEspecialidad`: la query sobre
     * `profesionales`, el `get()` de `usuarios/{uid}` y la subcoleccion
     * `profesionales/{id}/servicios`.
     */
    private fun setUpFirestore(
        profesionalDocumentos: List<DocumentSnapshot>,
        usuariosPorId: Map<String, DocumentSnapshot> = emptyMap(),
        serviciosPorProfesionalId: Map<String, List<DocumentSnapshot>> = emptyMap(),
        queryException: Exception? = null,
    ) {
        val profesionalesCollection = mockk<CollectionReference>()
        val query = mockk<Query>()
        every { firestore.collection(COLECCION_PROFESIONALES) } returns profesionalesCollection
        every { profesionalesCollection.whereArrayContains(any<String>(), any()) } returns query
        every { query.orderBy(any<String>(), any()) } returns query
        every { query.limit(any()) } returns query

        val queryTask = mockk<Task<QuerySnapshot>>()
        if (queryException != null) {
            coEvery { queryTask.await() } throws queryException
        } else {
            val querySnapshot = mockk<QuerySnapshot>()
            every { querySnapshot.documents } returns profesionalDocumentos
            coEvery { queryTask.await() } returns querySnapshot
        }
        every { query.get() } returns queryTask

        val usuariosCollection = mockk<CollectionReference>()
        every { firestore.collection(COLECCION_USUARIOS) } returns usuariosCollection
        usuariosPorId.forEach { (uid, doc) ->
            val usuarioDocRef = mockk<DocumentReference>()
            every { usuariosCollection.document(uid) } returns usuarioDocRef
            val usuarioTask = mockk<Task<DocumentSnapshot>>()
            coEvery { usuarioTask.await() } returns doc
            every { usuarioDocRef.get() } returns usuarioTask
        }

        serviciosPorProfesionalId.forEach { (profesionalId, servicioDocs) ->
            val profesionalDocRef = mockk<DocumentReference>()
            every { profesionalesCollection.document(profesionalId) } returns profesionalDocRef
            val serviciosCollection = mockk<CollectionReference>()
            every { profesionalDocRef.collection(SUBCOLECCION_SERVICIOS) } returns serviciosCollection
            val serviciosTask = mockk<Task<QuerySnapshot>>()
            val serviciosSnapshot = mockk<QuerySnapshot>()
            every { serviciosSnapshot.documents } returns servicioDocs
            coEvery { serviciosTask.await() } returns serviciosSnapshot
            every { serviciosCollection.get() } returns serviciosTask
        }
    }

    /** Mockea un `DocumentSnapshot` cuyos getters leen de un `Map` plano, en vez de repetir `every { }` por campo en cada test. */
    private fun mockDocumentSnapshot(
        id: String,
        exists: Boolean = true,
        data: Map<String, Any?>,
    ): DocumentSnapshot {
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
