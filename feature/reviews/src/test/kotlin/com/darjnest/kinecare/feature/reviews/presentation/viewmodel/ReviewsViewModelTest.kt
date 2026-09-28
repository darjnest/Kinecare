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
import io.mockk.coVerify
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

    private val profesionalId = "prof-1"

    private fun crearViewModel(id: String? = profesionalId): ReviewsViewModel {
        val handle = if (id == null) SavedStateHandle() else SavedStateHandle(mapOf(ARG_PROFESIONAL_ID to id))
        return ReviewsViewModel(handle, resenaRepository, usuarioRepository)
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
}
