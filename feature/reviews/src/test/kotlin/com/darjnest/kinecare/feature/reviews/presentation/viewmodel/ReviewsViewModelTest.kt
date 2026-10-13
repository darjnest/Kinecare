@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.darjnest.kinecare.feature.reviews.presentation.viewmodel

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.darjnest.kinecare.core.common.data.error.ResenaError
import com.darjnest.kinecare.core.common.data.error.UsuarioError
import com.darjnest.kinecare.core.common.data.repository.ResenaRepository
import com.darjnest.kinecare.core.common.data.repository.UsuarioRepository
import com.darjnest.kinecare.core.common.domain.model.Resena
import com.darjnest.kinecare.core.common.domain.model.RolUsuario
import com.darjnest.kinecare.core.common.domain.model.Usuario
import com.darjnest.kinecare.core.common.result.Result
import com.darjnest.kinecare.feature.reviews.presentation.navigation.ARG_PROFESIONAL_ID
import io.mockk.coEvery
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class ReviewsViewModelTest {

    companion object {
        @JvmField
        @RegisterExtension
        val mainDispatcherExtension = MainDispatcherExtension()
    }

    private val resenaRepository = mockk<ResenaRepository>()
    private val usuarioRepository = mockk<UsuarioRepository>()
    private val firebaseAuth = mockk<FirebaseAuth>()

    private val profesionalId = "prof-1"

    /** Por defecto mira un cliente cualquiera; [uid] = `profesionalId` simula al profesional dueño. */
    private fun crearViewModel(id: String? = profesionalId, uid: String? = "cli-visitante"): ReviewsViewModel {
        if (uid == null) {
            every { firebaseAuth.currentUser } returns null
        } else {
            val usuario = mockk<FirebaseUser>()
            every { usuario.uid } returns uid
            every { firebaseAuth.currentUser } returns usuario
        }
        val handle = if (id == null) SavedStateHandle() else SavedStateHandle(mapOf(ARG_PROFESIONAL_ID to id))
        return ReviewsViewModel(handle, resenaRepository, usuarioRepository, firebaseAuth)
    }

    private fun resena(
        id: String,
        clienteId: String = "cli-1",
        calificacion: Int = 5,
        comentario: String? = "Muy bien",
        respuesta: String? = null,
        fechaMillis: Long = 1_700_000_000_000L,
    ) = Resena(
        id = id,
        reservaId = id,
        clienteId = clienteId,
        profesionalId = profesionalId,
        calificacion = calificacion,
        comentario = comentario,
        fecha = Instant.fromEpochMilliseconds(fechaMillis),
        respuestaProfesional = respuesta,
    )

    private fun usuario(id: String, nombre: String) = Usuario(
        id = id,
        nombre = nombre,
        rut = "12345678-5",
        email = "$id@kinecare.cl",
        correoContacto = null,
        telefono = "+56911111111",
        rol = RolUsuario.CLIENTE,
        fotoUrl = null,
        fechaRegistro = Instant.fromEpochMilliseconds(0),
    )

    private fun stubUsuario(id: String, nombre: String) {
        coEvery { usuarioRepository.obtenerPorId(id) } returns Result.Success(usuario(id, nombre))
    }

    @Test
    fun `carga las resenas y mapea el autor comentario fecha y respuesta`() = runTest {
        coEvery { resenaRepository.obtenerPorProfesional(profesionalId) } returns Result.Success(
            listOf(resena("r1", respuesta = "  Gracias  ", fechaMillis = 1_234L)),
        )
        stubUsuario("cli-1", "María Pérez Soto")

        val viewModel = crearViewModel()

        val state = viewModel.state.value
        assertFalse(state.cargando)
        assertNull(state.error)
        val item = state.resenas.single()
        assertEquals("r1", item.id)
        assertEquals("María S.", item.autor)
        assertEquals(5, item.calificacion)
        assertEquals("Muy bien", item.comentario)
        assertEquals(1_234L, item.fechaMillis)
        assertEquals("Gracias", item.respuestaProfesional)
        coVerify(exactly = 1) { resenaRepository.obtenerPorProfesional(profesionalId) }
    }

    @Test
    fun `mientras carga el estado esta cargando`() = runTest {
        val gate = CompletableDeferred<Result<List<Resena>, ResenaError>>()
        coEvery { resenaRepository.obtenerPorProfesional(profesionalId) } coAnswers { gate.await() }

        val viewModel = crearViewModel()

        assertTrue(viewModel.state.value.cargando)
        gate.complete(Result.Success(emptyList()))
        assertFalse(viewModel.state.value.cargando)
    }

    @Test
    fun `calcula promedio total y distribucion de 5 a 1 estrellas`() = runTest {
        coEvery { resenaRepository.obtenerPorProfesional(profesionalId) } returns Result.Success(
            listOf(
                resena("a", calificacion = 5),
                resena("b", calificacion = 5),
                resena("c", calificacion = 4),
                resena("d", calificacion = 2),
            ),
        )
        stubUsuario("cli-1", "Ana Soto")

        val resumen = crearViewModel().state.value.resumen
        assertNotNull(resumen)
        assertEquals(4.0, resumen!!.promedio, 0.0001)
        assertEquals(4, resumen.total)
        assertEquals(listOf(5, 4, 3, 2, 1), resumen.distribucion.keys.toList())
        assertEquals(mapOf(5 to 2, 4 to 1, 3 to 0, 2 to 1, 1 to 0), resumen.distribucion)
    }

    @Test
    fun `sin resenas no hay resumen y la lista queda vacia sin error`() = runTest {
        coEvery { resenaRepository.obtenerPorProfesional(profesionalId) } returns Result.Success(emptyList())

        val state = crearViewModel().state.value

        assertFalse(state.cargando)
        assertNull(state.error)
        assertNull(state.resumen)
        assertTrue(state.resenas.isEmpty())
        coVerify(exactly = 0) { usuarioRepository.obtenerPorId(any()) }
    }

    @Test
    fun `resuelve cada cliente distinto una sola vez`() = runTest {
        coEvery { resenaRepository.obtenerPorProfesional(profesionalId) } returns Result.Success(
            listOf(
                resena("r1", clienteId = "cli-1"),
                resena("r2", clienteId = "cli-2"),
                resena("r3", clienteId = "cli-1"),
            ),
        )
        stubUsuario("cli-1", "Ana Soto")
        stubUsuario("cli-2", "Luis Vera")

        val autores = crearViewModel().state.value.resenas.map { it.autor }

        assertEquals(listOf("Ana S.", "Luis V.", "Ana S."), autores)
        coVerify(exactly = 1) { usuarioRepository.obtenerPorId("cli-1") }
        coVerify(exactly = 1) { usuarioRepository.obtenerPorId("cli-2") }
    }

    @Test
    fun `si no se resuelve el autor usa Cliente y la resena igual se muestra`() = runTest {
        coEvery { resenaRepository.obtenerPorProfesional(profesionalId) } returns Result.Success(
            listOf(resena("r1", clienteId = "cli-1"), resena("r2", clienteId = "cli-2")),
        )
        coEvery { usuarioRepository.obtenerPorId("cli-1") } returns Result.Error(UsuarioError.NO_ENCONTRADO)
        stubUsuario("cli-2", "Luis Vera")

        val state = crearViewModel().state.value

        assertNull(state.error)
        assertEquals(listOf("Cliente", "Luis V."), state.resenas.map { it.autor })
    }

    @Test
    fun `nunca se expone un email como autor`() = runTest {
        coEvery { resenaRepository.obtenerPorProfesional(profesionalId) } returns
            Result.Success(listOf(resena("r1")))
        stubUsuario("cli-1", "ana@kinecare.cl")

        assertEquals("Cliente", crearViewModel().state.value.resenas.single().autor)
    }

    @Test
    fun `el comentario nulo o en blanco se oculta y la respuesta vacia tambien`() = runTest {
        coEvery { resenaRepository.obtenerPorProfesional(profesionalId) } returns Result.Success(
            listOf(
                resena("r1", comentario = null),
                resena("r2", comentario = "   ", respuesta = "  "),
                resena("r3", comentario = "  Hola  "),
            ),
        )
        stubUsuario("cli-1", "Ana Soto")

        val items = crearViewModel().state.value.resenas

        assertNull(items[0].comentario)
        assertNull(items[1].comentario)
        assertNull(items[1].respuestaProfesional)
        assertEquals("Hola", items[2].comentario)
    }

    @Test
    fun `cada ResenaError produce su estado de error sin resenas`() = runTest {
        ResenaError.entries.forEach { error ->
            coEvery { resenaRepository.obtenerPorProfesional(profesionalId) } returns Result.Error(error)

            val state = crearViewModel().state.value

            assertFalse(state.cargando, "cargando colgado para $error")
            assertEquals(error, state.error)
            assertTrue(state.resenas.isEmpty())
            assertNull(state.resumen)
        }
    }

    @Test
    fun `sin id en los argumentos queda en error sin llamar al repositorio`() = runTest {
        val state = crearViewModel(id = null).state.value

        assertEquals(ResenaError.DESCONOCIDO, state.error)
        assertFalse(state.cargando)
        coVerify(exactly = 0) { resenaRepository.obtenerPorProfesional(any()) }
    }

    @Test
    fun `Reintentar recarga y sale del estado de error`() = runTest {
        coEvery { resenaRepository.obtenerPorProfesional(profesionalId) } returns
            Result.Error(ResenaError.SIN_INTERNET)
        val viewModel = crearViewModel()
        assertEquals(ResenaError.SIN_INTERNET, viewModel.state.value.error)

        coEvery { resenaRepository.obtenerPorProfesional(profesionalId) } returns
            Result.Success(listOf(resena("r1")))
        stubUsuario("cli-1", "Ana Soto")
        viewModel.state.test {
            awaitItem()
            viewModel.onAction(ReviewsAction.Reintentar)
            val final = expectMostRecentItem()
            assertNull(final.error)
            assertFalse(final.cargando)
            assertEquals(1, final.resenas.size)
        }
        coVerify(exactly = 2) { resenaRepository.obtenerPorProfesional(profesionalId) }
    }

    @Test
    fun `al reintentar no se vuelven a pedir los autores ya resueltos`() = runTest {
        coEvery { resenaRepository.obtenerPorProfesional(profesionalId) } returns
            Result.Success(listOf(resena("r1")))
        stubUsuario("cli-1", "Ana Soto")
        val viewModel = crearViewModel()

        viewModel.onAction(ReviewsAction.Reintentar)

        assertEquals("Ana S.", viewModel.state.value.resenas.single().autor)
        coVerify(exactly = 1) { usuarioRepository.obtenerPorId("cli-1") }
    }

    @Test
    fun `al reintentar se vuelven a pedir los autores que fallaron`() = runTest {
        coEvery { resenaRepository.obtenerPorProfesional(profesionalId) } returns
            Result.Success(listOf(resena("r1")))
        coEvery { usuarioRepository.obtenerPorId("cli-1") } returns Result.Error(UsuarioError.SIN_INTERNET)
        val viewModel = crearViewModel()
        assertEquals("Cliente", viewModel.state.value.resenas.single().autor)

        stubUsuario("cli-1", "Ana Soto")
        viewModel.onAction(ReviewsAction.Reintentar)

        assertEquals("Ana S.", viewModel.state.value.resenas.single().autor)
    }

    // --- responder (profesional dueño) ---

    private fun cargarComoProfesional(vararg resenas: Resena): ReviewsViewModel {
        coEvery { resenaRepository.obtenerPorProfesional(profesionalId) } returns Result.Success(resenas.toList())
        stubUsuario("cli-1", "María Pérez Soto")
        return crearViewModel(uid = profesionalId)
    }

    @Test
    fun `solo el profesional dueño puede responder y solo a resenas sin respuesta`() = runTest {
        val resenas = arrayOf(resena("r1"), resena("r2", respuesta = "Gracias"))

        val comoProfesional = cargarComoProfesional(*resenas)
        assertEquals(
            mapOf("r1" to true, "r2" to false),
            comoProfesional.state.value.resenas.associate { it.id to it.puedeResponder },
        )

        val comoCliente = crearViewModel(uid = "cli-1")
        assertTrue(comoCliente.state.value.resenas.none { it.puedeResponder })

        val sinSesion = crearViewModel(uid = null)
        assertTrue(sinSesion.state.value.resenas.none { it.puedeResponder })
    }

    @Test
    fun `Responder abre el dialogo con el autor y una resena que no se puede responder no`() = runTest {
        val viewModel = cargarComoProfesional(resena("r1"), resena("r2", respuesta = "Gracias"))

        viewModel.onAction(ReviewsAction.Responder("r2"))
        assertNull(viewModel.state.value.respuesta)

        viewModel.onAction(ReviewsAction.Responder("r1"))
        val respuesta = requireNotNull(viewModel.state.value.respuesta)
        assertEquals("r1", respuesta.resenaId)
        assertEquals("María S.", respuesta.autor)
        assertFalse(respuesta.puedeEnviar)
    }

    @Test
    fun `un cliente no puede abrir el dialogo de respuesta`() = runTest {
        coEvery { resenaRepository.obtenerPorProfesional(profesionalId) } returns Result.Success(listOf(resena("r1")))
        stubUsuario("cli-1", "María Pérez Soto")
        val viewModel = crearViewModel(uid = "cli-1")

        viewModel.onAction(ReviewsAction.Responder("r1"))

        assertNull(viewModel.state.value.respuesta)
    }

    @Test
    fun `el texto se limita a 500 caracteres y un texto en blanco no se puede enviar`() = runTest {
        val viewModel = cargarComoProfesional(resena("r1"))
        viewModel.onAction(ReviewsAction.Responder("r1"))

        viewModel.onAction(ReviewsAction.CambiarRespuesta("   "))
        assertFalse(requireNotNull(viewModel.state.value.respuesta).puedeEnviar)

        viewModel.onAction(ReviewsAction.CambiarRespuesta("a".repeat(600)))
        val respuesta = requireNotNull(viewModel.state.value.respuesta)
        assertEquals(500, respuesta.texto.length)
        assertTrue(respuesta.puedeEnviar)
    }

    @Test
    fun `DescartarRespuesta cierra el dialogo sin escribir`() = runTest {
        val viewModel = cargarComoProfesional(resena("r1"))
        viewModel.onAction(ReviewsAction.Responder("r1"))

        viewModel.onAction(ReviewsAction.DescartarRespuesta)

        assertNull(viewModel.state.value.respuesta)
        coVerify(exactly = 0) { resenaRepository.responder(any(), any()) }
    }

    @Test
    fun `EnviarRespuesta publica el texto recortado, muestra la respuesta y avisa`() = runTest {
        val viewModel = cargarComoProfesional(resena("r1"))
        coEvery { resenaRepository.responder("r1", "Gracias por venir") } returns Result.Success(Unit)
        viewModel.onAction(ReviewsAction.Responder("r1"))
        viewModel.onAction(ReviewsAction.CambiarRespuesta("  Gracias por venir "))

        viewModel.eventos.test {
            viewModel.onAction(ReviewsAction.EnviarRespuesta)

            assertEquals(ReviewsEvent.MostrarMensaje("Respuesta publicada."), awaitItem())
        }

        val state = viewModel.state.value
        assertNull(state.respuesta)
        val item = state.resenas.single()
        assertEquals("Gracias por venir", item.respuestaProfesional)
        assertFalse(item.puedeResponder)
    }

    @Test
    fun `mientras envia el dialogo queda bloqueado y no se envia dos veces`() = runTest {
        val viewModel = cargarComoProfesional(resena("r1"))
        val gate = CompletableDeferred<Result<Unit, ResenaError>>()
        coEvery { resenaRepository.responder(any(), any()) } coAnswers { gate.await() }
        viewModel.onAction(ReviewsAction.Responder("r1"))
        viewModel.onAction(ReviewsAction.CambiarRespuesta("Gracias"))

        viewModel.onAction(ReviewsAction.EnviarRespuesta)
        viewModel.onAction(ReviewsAction.EnviarRespuesta)
        viewModel.onAction(ReviewsAction.CambiarRespuesta("otro texto"))
        viewModel.onAction(ReviewsAction.DescartarRespuesta)

        val respuesta = requireNotNull(viewModel.state.value.respuesta)
        assertTrue(respuesta.enviando)
        assertEquals("Gracias", respuesta.texto)
        coVerify(exactly = 1) { resenaRepository.responder(any(), any()) }
        gate.complete(Result.Success(Unit))
    }

    @Test
    fun `un error deja el dialogo abierto con el mensaje y la resena sin respuesta`() = runTest {
        val viewModel = cargarComoProfesional(resena("r1"))
        coEvery { resenaRepository.responder(any(), any()) } returns Result.Error(ResenaError.SIN_INTERNET)
        viewModel.onAction(ReviewsAction.Responder("r1"))
        viewModel.onAction(ReviewsAction.CambiarRespuesta("Gracias"))

        viewModel.onAction(ReviewsAction.EnviarRespuesta)

        val state = viewModel.state.value
        val respuesta = requireNotNull(state.respuesta)
        assertFalse(respuesta.enviando)
        assertEquals("Sin conexión. Revisa tu internet e inténtalo de nuevo.", respuesta.error)
        assertEquals("Gracias", respuesta.texto)
        assertNull(state.resenas.single().respuestaProfesional)

        viewModel.onAction(ReviewsAction.CambiarRespuesta("Gracias de nuevo"))
        assertNull(viewModel.state.value.respuesta?.error)
    }

    @Test
    fun `SIN_PERMISO se explica en el dialogo`() = runTest {
        val viewModel = cargarComoProfesional(resena("r1"))
        coEvery { resenaRepository.responder(any(), any()) } returns Result.Error(ResenaError.SIN_PERMISO)
        viewModel.onAction(ReviewsAction.Responder("r1"))
        viewModel.onAction(ReviewsAction.CambiarRespuesta("Gracias"))

        viewModel.onAction(ReviewsAction.EnviarRespuesta)

        assertTrue(requireNotNull(viewModel.state.value.respuesta?.error).contains("no es de tu perfil"))
    }
}
