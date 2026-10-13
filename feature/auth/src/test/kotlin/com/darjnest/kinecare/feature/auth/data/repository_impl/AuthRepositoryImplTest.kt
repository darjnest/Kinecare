package com.darjnest.kinecare.feature.auth.data.repository_impl

import com.darjnest.kinecare.core.common.domain.model.RolUsuario
import com.darjnest.kinecare.core.common.domain.model.TipoAtencion
import com.darjnest.kinecare.core.common.result.Result
import com.darjnest.kinecare.feature.auth.domain.AuthError
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.AuthResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.WriteBatch
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

private const val UID = "uid-1"

/**
 * Cubre la creacion de documentos al registrarse: un Profesional debe
 * quedar con `profesionales/{uid}` (con `tiposAtencion`) escrito en el mismo
 * batch que `usuarios/{uid}`, o nunca aparece en la busqueda.
 */
class AuthRepositoryImplTest {

    private val firebaseAuth = mockk<FirebaseAuth>(relaxed = true)
    private val firestore = mockk<FirebaseFirestore>()
    private val repository = AuthRepositoryImpl(firebaseAuth, firestore)

    private val usuarios = mockk<CollectionReference>()
    private val profesionales = mockk<CollectionReference>()
    private val usuarioRef = mockk<DocumentReference>()
    private val perfilesPublicos = mockk<CollectionReference>()
    private val perfilPublicoRef = mockk<DocumentReference>()
    private val rutsRegistrados = mockk<CollectionReference>()
    private val rutRef = mockk<DocumentReference>()
    private val datosRut = slot<Any>()
    private val datosPerfilPublico = slot<Any>()
    private val profesionalRef = mockk<DocumentReference>()
    private val clientes = mockk<CollectionReference>()
    private val clienteRef = mockk<DocumentReference>()
    private val datosCliente = slot<Any>()
    private val batch = mockk<WriteBatch>()
    private val datosProfesional = slot<Any>()

    @BeforeEach
    fun setUp() {
        mockkStatic("kotlinx.coroutines.tasks.TasksKt")

        every { firestore.collection("usuarios") } returns usuarios
        every { firestore.collection("profesionales") } returns profesionales
        every { firestore.collection("clientes") } returns clientes
        every { firestore.collection("perfilesPublicos") } returns perfilesPublicos
        every { perfilesPublicos.document(UID) } returns perfilPublicoRef
        every { firestore.collection("rutsRegistrados") } returns rutsRegistrados
        every { rutsRegistrados.document("123456785") } returns rutRef
        every { clientes.document(UID) } returns clienteRef
        every { usuarios.document(UID) } returns usuarioRef
        every { profesionales.document(UID) } returns profesionalRef

        every { firestore.batch() } returns batch
        every { batch.set(usuarioRef, any()) } returns batch
        every { batch.set(perfilPublicoRef, capture(datosPerfilPublico)) } returns batch
        every { batch.set(rutRef, capture(datosRut)) } returns batch
        every { batch.set(profesionalRef, capture(datosProfesional)) } returns batch
        every { batch.set(clienteRef, capture(datosCliente)) } returns batch
        val commitTask = mockk<Task<Void>>()
        coEvery { commitTask.await() } returns mockk()
        every { batch.commit() } returns commitTask

        // `obtenerUsuario` relee `usuarios/{uid}` despues del commit.
        val usuarioDoc = mockk<DocumentSnapshot>(relaxed = true)
        every { usuarioDoc.exists() } returns true
        every { usuarioDoc.getString("rol") } returns "PROFESIONAL"
        every { usuarioDoc.getTimestamp(any()) } returns null
        val getTask = mockk<Task<DocumentSnapshot>>()
        coEvery { getTask.await() } returns usuarioDoc
        every { usuarioRef.get() } returns getTask
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic("kotlinx.coroutines.tasks.TasksKt")
    }

    private fun setUpCuentaEmailCreada() {
        val user = mockk<FirebaseUser> { every { uid } returns UID }
        val authResult = mockk<AuthResult> { every { this@mockk.user } returns user }
        val task = mockk<Task<AuthResult>>()
        coEvery { task.await() } returns authResult
        every { firebaseAuth.createUserWithEmailAndPassword(any(), any()) } returns task
    }

    @Test
    fun `registrar un profesional crea usuarios y profesionales en el mismo batch con tiposAtencion`() = runTest {
        setUpCuentaEmailCreada()

        val resultado = repository.registrar(
            nombre = "Ana Soto",
            rut = "12.345.678-5",
            password = "secreta123",
            rol = RolUsuario.PROFESIONAL,
            telefono = "+56911111111",
            correoContacto = "ana@kinecare.cl",
            tiposAtencion = setOf(TipoAtencion.MASOTERAPIA, TipoAtencion.KINESIOLOGIA),
        )

        assertTrue(resultado is Result.Success)
        verify(exactly = 1) { batch.set(usuarioRef, any()) }
        verify(exactly = 1) { batch.set(profesionalRef, any()) }
        verify(exactly = 0) { batch.set(clienteRef, any()) }
        verify(exactly = 1) { batch.commit() }
        // El RUT se reclama (normalizado) y el perfil publico solo lleva nombre y foto.
        assertEquals(mapOf("uid" to UID), datosRut.captured)
        assertEquals(mapOf("nombre" to "Ana Soto", "fotoUrl" to null), datosPerfilPublico.captured)
        @Suppress("UNCHECKED_CAST")
        val perfil = datosProfesional.captured as Map<String, Any>
        assertEquals(listOf("KINESIOLOGIA", "MASOTERAPIA"), perfil["tiposAtencion"])
        assertEquals("NO_SOLICITADO", perfil["estadoVerificacionGeneral"])
        assertEquals(0.0, perfil["calificacionPromedio"])
    }

    @Test
    fun `registrar un cliente crea usuarios y un clientes vacio, sin profesionales`() = runTest {
        setUpCuentaEmailCreada()

        repository.registrar(
            nombre = "Bruno Diaz",
            rut = "12.345.678-5",
            password = "secreta123",
            rol = RolUsuario.CLIENTE,
            telefono = "+56922222222",
            correoContacto = "bruno@kinecare.cl",
            tiposAtencion = emptySet(),
        )

        verify(exactly = 1) { batch.set(usuarioRef, any()) }
        verify(exactly = 0) { batch.set(profesionalRef, any()) }
        verify(exactly = 1) { batch.set(clienteRef, any()) }
        @Suppress("UNCHECKED_CAST")
        val cliente = datosCliente.captured as Map<String, Any>
        assertEquals(emptyList<Any>(), cliente["direcciones"])
        assertEquals(emptyList<Any>(), cliente["favoritos"])
        assertEquals(emptyList<Any>(), cliente["metodosPago"])
    }

    @Test
    fun `si el batch falla el registro devuelve error en vez de dejar un perfil a medias`() = runTest {
        setUpCuentaEmailCreada()
        val commitFallido = mockk<Task<Void>>()
        coEvery { commitFallido.await() } throws IllegalStateException("PERMISSION_DENIED")
        every { batch.commit() } returns commitFallido

        val resultado = repository.registrar(
            nombre = "Ana Soto",
            rut = "12.345.678-5",
            password = "secreta123",
            rol = RolUsuario.PROFESIONAL,
            telefono = "+56911111111",
            correoContacto = "ana@kinecare.cl",
            tiposAtencion = setOf(TipoAtencion.KINESIOLOGIA),
        )

        assertEquals(AuthError.DESCONOCIDO, (resultado as Result.Error).error)
    }

    @Test
    fun `completarRegistroGoogle de un profesional tambien crea profesionales con tiposAtencion`() = runTest {
        val resultado = repository.completarRegistroGoogle(
            uid = UID,
            nombre = "Ana Soto",
            rut = "12.345.678-5",
            telefono = "+56911111111",
            correoContacto = "ana@gmail.com",
            rol = RolUsuario.PROFESIONAL,
            fotoUrl = null,
            tiposAtencion = setOf(TipoAtencion.KINESIOLOGIA),
        )

        assertTrue(resultado is Result.Success)
        @Suppress("UNCHECKED_CAST")
        val perfil = datosProfesional.captured as Map<String, Any>
        assertEquals(listOf("KINESIOLOGIA"), perfil["tiposAtencion"])
    }

    @Test
    fun `completarRegistroGoogle con un RUT ya reclamado devuelve RUT_YA_REGISTRADO sin consultar usuarios`() = runTest {
        val commitDenegado = mockk<Task<Void>>()
        coEvery { commitDenegado.await() } throws
            FirebaseFirestoreException("PERMISSION_DENIED", FirebaseFirestoreException.Code.PERMISSION_DENIED)
        every { batch.commit() } returns commitDenegado

        val resultado = repository.completarRegistroGoogle(
            uid = UID,
            nombre = "Ana Soto",
            rut = "12.345.678-5",
            telefono = "+56911111111",
            correoContacto = "ana@gmail.com",
            rol = RolUsuario.CLIENTE,
            fotoUrl = null,
            tiposAtencion = emptySet(),
        )

        assertEquals(AuthError.RUT_YA_REGISTRADO, (resultado as Result.Error).error)
        // `usuarios` es privado: ya no se lista para buscar el RUT.
        verify(exactly = 0) { usuarios.whereEqualTo(any<String>(), any()) }
    }

    @Test
    fun `completarRegistroGoogle ante otro error de Firestore devuelve DESCONOCIDO, no RUT_YA_REGISTRADO`() = runTest {
        val commitFallido = mockk<Task<Void>>()
        coEvery { commitFallido.await() } throws
            FirebaseFirestoreException("UNAVAILABLE", FirebaseFirestoreException.Code.UNAVAILABLE)
        every { batch.commit() } returns commitFallido

        val resultado = repository.completarRegistroGoogle(
            uid = UID,
            nombre = "Ana Soto",
            rut = "12.345.678-5",
            telefono = "+56911111111",
            correoContacto = "ana@gmail.com",
            rol = RolUsuario.CLIENTE,
            fotoUrl = null,
            tiposAtencion = emptySet(),
        )

        assertEquals(AuthError.DESCONOCIDO, (resultado as Result.Error).error)
    }

    @Test
    fun `registrar con un RUT ya reclamado borra la cuenta de Auth recien creada y devuelve RUT_YA_REGISTRADO`() = runTest {
        val user = mockk<FirebaseUser> { every { uid } returns UID }
        val borrado = mockk<Task<Void>>()
        coEvery { borrado.await() } returns mockk()
        every { user.delete() } returns borrado
        val authResult = mockk<AuthResult> { every { this@mockk.user } returns user }
        val creacion = mockk<Task<AuthResult>>()
        coEvery { creacion.await() } returns authResult
        every { firebaseAuth.createUserWithEmailAndPassword(any(), any()) } returns creacion
        val commitDenegado = mockk<Task<Void>>()
        coEvery { commitDenegado.await() } throws
            FirebaseFirestoreException("PERMISSION_DENIED", FirebaseFirestoreException.Code.PERMISSION_DENIED)
        every { batch.commit() } returns commitDenegado

        val resultado = repository.registrar(
            nombre = "Ana Soto",
            rut = "12.345.678-5",
            password = "secreta123",
            rol = RolUsuario.CLIENTE,
            telefono = "+56911111111",
            correoContacto = "ana@kinecare.cl",
            tiposAtencion = emptySet(),
        )

        assertEquals(AuthError.RUT_YA_REGISTRADO, (resultado as Result.Error).error)
        verify(exactly = 1) { user.delete() }
    }
}
