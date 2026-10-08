@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.darjnest.kinecare.core.network.firebase.repository

import com.darjnest.kinecare.core.common.data.error.ProfesionalError
import com.darjnest.kinecare.core.common.data.error.UsuarioError
import com.darjnest.kinecare.core.common.data.repository.UsuarioRepository
import com.darjnest.kinecare.core.common.domain.model.Disponibilidad
import com.darjnest.kinecare.core.common.domain.model.RolUsuario
import com.darjnest.kinecare.core.common.domain.model.TipoAtencion
import com.darjnest.kinecare.core.common.domain.model.Usuario
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
import io.mockk.verify
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalTime
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

private const val COLECCION_PROFESIONALES = "profesionales"
private const val SUBCOLECCION_SERVICIOS = "servicios"

/**
 * Movido desde `:feature:search` a `:core:network` junto con
 * `ProfesionalRepositoryImpl` (ver docs/ARCHITECTURE.md). El patron de
 * mockeo de `FirebaseFirestore`/`CollectionReference`/`Query`/`DocumentSnapshot`
 * con MockK + `mockkStatic` sobre `kotlinx.coroutines.tasks.TasksKt` sigue
 * siendo el precedente para el resto de los repositorios de Firestore de
 * este modulo.
 */
class ProfesionalRepositoryImplTest {

    private val firestore = mockk<FirebaseFirestore>()
    private val usuarioRepository = mockk<UsuarioRepository>()
    private val repository = ProfesionalRepositoryImpl(firestore, usuarioRepository)

    private val usuario = Usuario(
        id = "prof-1",
        nombre = "Ana Soto",
        rut = "12345678-5",
        email = "ana@kinecare.cl",
        correoContacto = "ana.contacto@kinecare.cl",
        telefono = "+56911111111",
        rol = RolUsuario.PROFESIONAL,
        fotoUrl = "https://foto.com/ana.png",
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
    fun `retorna profesionales mapeados cuando Firestore devuelve documentos validos`() = runTest {
        coEvery { usuarioRepository.obtenerPorId("prof-1") } returns Result.Success(usuario)
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
                "tiposAtencion" to listOf("KINESIOLOGIA", "MASOTERAPIA"),
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
            serviciosPorProfesionalId = mapOf("prof-1" to listOf(servicioDoc)),
        )

        val resultado = repository.buscarPorTipoAtencion(TipoAtencion.KINESIOLOGIA)

        assertTrue(resultado is Result.Success)
        val profesionales = (resultado as Result.Success).data
        assertEquals(1, profesionales.size)
        val profesional = profesionales.first()
        assertEquals("Ana Soto", profesional.usuario.nombre)
        assertEquals("RNPI-1234", profesional.rnpi)
        assertEquals(listOf("KINESIOLOGIA", "kine-deportiva"), profesional.especialidades)
        assertEquals(listOf(TipoAtencion.KINESIOLOGIA, TipoAtencion.MASOTERAPIA), profesional.tiposAtencion)
        assertEquals(1, profesional.servicios.size)
        assertEquals("Sesion de kinesiologia deportiva", profesional.servicios.first().nombre)
        assertEquals(1, profesional.insignias.size)
        assertEquals(4.8, profesional.calificacionPromedio)
        assertEquals(32, profesional.totalResenas)
    }

    @Test
    fun `filtra por el campo tiposAtencion con el nombre del tipo, no por especialidades`() = runTest {
        val profesionalesCollection = setUpFirestore(profesionalDocumentos = emptyList())

        repository.buscarPorTipoAtencion(TipoAtencion.MASOTERAPIA)

        verify { profesionalesCollection.whereArrayContains("tiposAtencion", "MASOTERAPIA") }
        verify(exactly = 0) { profesionalesCollection.whereArrayContains("especialidades", any()) }
    }

    @Test
    fun `ignora valores desconocidos de tiposAtencion y lo deja vacio si el campo falta`() = runTest {
        coEvery { usuarioRepository.obtenerPorId("prof-1") } returns Result.Success(usuario)
        coEvery { usuarioRepository.obtenerPorId("prof-legado") } returns
            Result.Success(usuario.copy(id = "prof-legado"))
        val conValorRaro = mockDocumentSnapshot(
            id = "prof-1",
            data = mapOf("tiposAtencion" to listOf("Kinesiologia", "KINESIOLOGIA", 42L)),
        )
        val sinCampo = mockDocumentSnapshot(id = "prof-legado", data = mapOf("especialidades" to listOf("Kinesiologia")))
        setUpFirestore(
            profesionalDocumentos = listOf(conValorRaro, sinCampo),
            serviciosPorProfesionalId = mapOf("prof-1" to emptyList(), "prof-legado" to emptyList()),
        )

        val profesionales = (repository.buscarPorTipoAtencion(TipoAtencion.KINESIOLOGIA) as Result.Success).data

        assertEquals(listOf(TipoAtencion.KINESIOLOGIA), profesionales[0].tiposAtencion)
        assertTrue(profesionales[1].tiposAtencion.isEmpty())
    }

    @Test
    fun `retorna lista vacia cuando Firestore no devuelve documentos`() = runTest {
        setUpFirestore(profesionalDocumentos = emptyList())

        val resultado = repository.buscarPorTipoAtencion(TipoAtencion.KINESIOLOGIA)

        assertTrue(resultado is Result.Success)
        assertTrue((resultado as Result.Success).data.isEmpty())
    }

    @Test
    fun `retorna error SIN_INTERNET cuando la query lanza FirebaseNetworkException`() = runTest {
        setUpFirestore(
            profesionalDocumentos = emptyList(),
            queryException = FirebaseNetworkException("sin conexion"),
        )

        val resultado = repository.buscarPorTipoAtencion(TipoAtencion.KINESIOLOGIA)

        assertTrue(resultado is Result.Error)
        assertEquals(ProfesionalError.SIN_INTERNET, (resultado as Result.Error).error)
    }

    @Test
    fun `retorna error DESCONOCIDO ante una excepcion generica`() = runTest {
        setUpFirestore(
            profesionalDocumentos = emptyList(),
            queryException = IllegalStateException("boom"),
        )

        val resultado = repository.buscarPorTipoAtencion(TipoAtencion.KINESIOLOGIA)

        assertTrue(resultado is Result.Error)
        assertEquals(ProfesionalError.DESCONOCIDO, (resultado as Result.Error).error)
    }

    @Test
    fun `descarta insignia con tipo invalido sin romper el mapeo del resto del profesional`() = runTest {
        val usuarioBruno = usuario.copy(id = "prof-2", nombre = "Bruno Diaz", rut = "9876543-2", correoContacto = null, telefono = null, fotoUrl = null)
        coEvery { usuarioRepository.obtenerPorId("prof-2") } returns Result.Success(usuarioBruno)
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
            serviciosPorProfesionalId = mapOf("prof-2" to emptyList()),
        )

        val resultado = repository.buscarPorTipoAtencion(TipoAtencion.KINESIOLOGIA)

        assertTrue(resultado is Result.Success)
        val profesional = (resultado as Result.Success).data.first()
        assertTrue(profesional.insignias.isEmpty())
        assertEquals("Bruno Diaz", profesional.usuario.nombre)
        assertEquals("RNPI-9999", profesional.rnpi)
    }

    @Test
    fun `descarta el profesional cuando UsuarioRepository no encuentra el usuario`() = runTest {
        coEvery { usuarioRepository.obtenerPorId("prof-3") } returns Result.Error(UsuarioError.NO_ENCONTRADO)
        val profesionalDoc = mockDocumentSnapshot(
            id = "prof-3",
            data = mapOf(
                "especialidades" to listOf("KINESIOLOGIA"),
                "rnpi" to "RNPI-0000",
                "calificacionPromedio" to 1.0,
                "totalResenas" to 0L,
                "descripcion" to "",
                "estadoVerificacionGeneral" to "NO_SOLICITADO",
            ),
        )

        setUpFirestore(profesionalDocumentos = listOf(profesionalDoc))

        val resultado = repository.buscarPorTipoAtencion(TipoAtencion.KINESIOLOGIA)

        assertTrue(resultado is Result.Success)
        assertTrue((resultado as Result.Success).data.isEmpty())
    }

    @Test
    fun `obtenerPorId retorna el profesional cuando el documento existe`() = runTest {
        coEvery { usuarioRepository.obtenerPorId("prof-1") } returns Result.Success(usuario)
        val profesionalDoc = mockDocumentSnapshot(
            id = "prof-1",
            data = mapOf(
                "especialidades" to listOf("KINESIOLOGIA"),
                "rnpi" to "RNPI-1234",
                "calificacionPromedio" to 4.8,
                "totalResenas" to 32L,
                "descripcion" to "Kinesiologo deportivo",
                "estadoVerificacionGeneral" to "APROBADO",
            ),
        )

        val profesionalDocRef = mockk<DocumentReference>()
        val profesionalesCollection = mockk<CollectionReference>()
        every { firestore.collection(COLECCION_PROFESIONALES) } returns profesionalesCollection
        every { profesionalesCollection.document("prof-1") } returns profesionalDocRef
        val docTask = mockk<Task<DocumentSnapshot>>()
        coEvery { docTask.await() } returns profesionalDoc
        every { profesionalDocRef.get() } returns docTask

        val serviciosCollection = mockk<CollectionReference>()
        every { profesionalDocRef.collection(SUBCOLECCION_SERVICIOS) } returns serviciosCollection
        val serviciosTask = mockk<Task<QuerySnapshot>>()
        val serviciosSnapshot = mockk<QuerySnapshot>()
        every { serviciosSnapshot.documents } returns emptyList()
        coEvery { serviciosTask.await() } returns serviciosSnapshot
        every { serviciosCollection.get() } returns serviciosTask

        val resultado = repository.obtenerPorId("prof-1")

        assertTrue(resultado is Result.Success)
        assertEquals("Ana Soto", (resultado as Result.Success).data.usuario.nombre)
    }

    @Test
    fun `obtenerPorId retorna NO_ENCONTRADO cuando el documento no existe`() = runTest {
        val profesionalDocRef = mockk<DocumentReference>()
        val profesionalesCollection = mockk<CollectionReference>()
        every { firestore.collection(COLECCION_PROFESIONALES) } returns profesionalesCollection
        every { profesionalesCollection.document("prof-x") } returns profesionalDocRef
        val docTask = mockk<Task<DocumentSnapshot>>()
        coEvery { docTask.await() } returns mockDocumentSnapshot(id = "prof-x", exists = false, data = emptyMap())
        every { profesionalDocRef.get() } returns docTask

        val resultado = repository.obtenerPorId("prof-x")

        assertTrue(resultado is Result.Error)
        assertEquals(ProfesionalError.NO_ENCONTRADO, (resultado as Result.Error).error)
    }

    @Test
    fun `actualizarDisponibilidad escribe solo el arreglo disponibilidad con horas HH-mm`() = runTest {
        val profesionales = mockk<CollectionReference>()
        val docRef = mockk<DocumentReference>()
        every { firestore.collection(COLECCION_PROFESIONALES) } returns profesionales
        every { profesionales.document("prof-1") } returns docRef
        val task = mockk<Task<Void>>()
        coEvery { task.await() } returns mockk()
        val esperado = listOf(
            mapOf("diaSemana" to "MONDAY", "horaInicio" to "09:00", "horaFin" to "13:30", "activo" to true),
            mapOf("diaSemana" to "SATURDAY", "horaInicio" to "08:05", "horaFin" to "12:00", "activo" to false),
        )
        every { docRef.update("disponibilidad", esperado) } returns task

        val resultado = repository.actualizarDisponibilidad(
            "prof-1",
            listOf(
                Disponibilidad(DayOfWeek.MONDAY, LocalTime(9, 0), LocalTime(13, 30), activo = true),
                Disponibilidad(DayOfWeek.SATURDAY, LocalTime(8, 5), LocalTime(12, 0), activo = false),
            ),
        )

        assertTrue(resultado is Result.Success)
        verify { docRef.update("disponibilidad", esperado) }
    }

    @Test
    fun `actualizarDisponibilidad retorna SIN_INTERNET ante FirebaseNetworkException`() = runTest {
        val profesionales = mockk<CollectionReference>()
        val docRef = mockk<DocumentReference>()
        every { firestore.collection(COLECCION_PROFESIONALES) } returns profesionales
        every { profesionales.document("prof-1") } returns docRef
        val task = mockk<Task<Void>>()
        coEvery { task.await() } throws FirebaseNetworkException("sin red")
        every { docRef.update("disponibilidad", any<List<Map<String, Any>>>()) } returns task

        val resultado = repository.actualizarDisponibilidad("prof-1", emptyList())

        assertEquals(ProfesionalError.SIN_INTERNET, (resultado as Result.Error).error)
    }

    @Test
    fun `actualizarDescripcion escribe solo el campo descripcion`() = runTest {
        val profesionales = mockk<CollectionReference>()
        val docRef = mockk<DocumentReference>()
        every { firestore.collection(COLECCION_PROFESIONALES) } returns profesionales
        every { profesionales.document("prof-1") } returns docRef
        val task = mockk<Task<Void>>()
        coEvery { task.await() } returns mockk()
        every { docRef.update("descripcion", "Kinesiologa deportiva") } returns task

        val resultado = repository.actualizarDescripcion("prof-1", "Kinesiologa deportiva")

        assertTrue(resultado is Result.Success)
        verify { docRef.update("descripcion", "Kinesiologa deportiva") }
    }

    @Test
    fun `actualizarDescripcion retorna DESCONOCIDO ante un error inesperado`() = runTest {
        val profesionales = mockk<CollectionReference>()
        val docRef = mockk<DocumentReference>()
        every { firestore.collection(COLECCION_PROFESIONALES) } returns profesionales
        every { profesionales.document("prof-1") } returns docRef
        val task = mockk<Task<Void>>()
        coEvery { task.await() } throws IllegalStateException("permiso denegado")
        every { docRef.update("descripcion", any<String>()) } returns task

        val resultado = repository.actualizarDescripcion("prof-1", "x")

        assertEquals(ProfesionalError.DESCONOCIDO, (resultado as Result.Error).error)
    }

    @Test
    fun `actualizarTiposAtencion escribe los nombres en orden del enum`() = runTest {
        val profesionales = mockk<CollectionReference>()
        val docRef = mockk<DocumentReference>()
        every { firestore.collection(COLECCION_PROFESIONALES) } returns profesionales
        every { profesionales.document("prof-1") } returns docRef
        val task = mockk<Task<Void>>()
        coEvery { task.await() } returns mockk()
        every { docRef.update("tiposAtencion", listOf("KINESIOLOGIA", "MASOTERAPIA")) } returns task

        val resultado = repository.actualizarTiposAtencion(
            "prof-1",
            linkedSetOf(TipoAtencion.MASOTERAPIA, TipoAtencion.KINESIOLOGIA),
        )

        assertTrue(resultado is Result.Success)
        verify { docRef.update("tiposAtencion", listOf("KINESIOLOGIA", "MASOTERAPIA")) }
    }

    @Test
    fun `actualizarTiposAtencion rechaza un conjunto vacio sin tocar Firestore`() = runTest {
        val resultado = repository.actualizarTiposAtencion("prof-1", emptySet())

        assertEquals(ProfesionalError.DESCONOCIDO, (resultado as Result.Error).error)
        verify(exactly = 0) { firestore.collection(any()) }
    }

    @Test
    fun `actualizarTiposAtencion retorna SIN_INTERNET ante FirebaseNetworkException`() = runTest {
        val profesionales = mockk<CollectionReference>()
        val docRef = mockk<DocumentReference>()
        every { firestore.collection(COLECCION_PROFESIONALES) } returns profesionales
        every { profesionales.document("prof-1") } returns docRef
        val task = mockk<Task<Void>>()
        coEvery { task.await() } throws FirebaseNetworkException("sin red")
        every { docRef.update("tiposAtencion", any<List<String>>()) } returns task

        val resultado = repository.actualizarTiposAtencion("prof-1", setOf(TipoAtencion.KINESIOLOGIA))

        assertEquals(ProfesionalError.SIN_INTERNET, (resultado as Result.Error).error)
    }

    /**
     * Encadena los mocks de Firestore que recorre
     * `ProfesionalRepositoryImpl.buscarPorTipoAtencion`: la query sobre
     * `profesionales` y la subcoleccion `profesionales/{id}/servicios`
     * (el usuario ahora se resuelve via `UsuarioRepository`, mockeado
     * aparte con `coEvery` en cada test).
     */
    private fun setUpFirestore(
        profesionalDocumentos: List<DocumentSnapshot>,
        serviciosPorProfesionalId: Map<String, List<DocumentSnapshot>> = emptyMap(),
        queryException: Exception? = null,
    ): CollectionReference {
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
        return profesionalesCollection
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
        every { doc.getBoolean(any()) } answers { data[firstArg()] as? Boolean }
        every { doc.getTimestamp(any()) } answers { data[firstArg()] as? Timestamp }
        return doc
    }
}
